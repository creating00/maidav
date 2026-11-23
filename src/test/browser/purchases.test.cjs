// First generate rendered previews using PurchaseIntegrationTest against a disposable PostgreSQL database.
// Then run: node --test src/test/browser/purchases.test.cjs
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const { captureScreen } = require('./chromium-support.cjs');

test('rendered purchase form calculates money, manages rows and protects submission in Chromium', async () => {
    const browser = process.env.CHROMIUM_PATH || [
        'C:/Program Files/Google/Chrome/Application/chrome.exe',
        'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
    ].find(file => fs.existsSync(file));
    assert.ok(browser, 'Set CHROMIUM_PATH to an installed Chromium browser');
    const directory = path.resolve('target/purchases-preview');
    const preview = path.join(directory, 'form.html');
    assert.ok(fs.existsSync(preview), 'Run PurchaseIntegrationTest to generate actual Thymeleaf HTML first');
    const checks = `<script>
        window.addEventListener('error', event => {
            document.documentElement.dataset.purchaseError = event.message;
        });
        document.addEventListener('DOMContentLoaded', async () => {
            try {
                const check = (condition, message) => { if (!condition) throw new Error(message); };
                const rows = document.getElementById('purchase-items');
                const add = document.getElementById('add-purchase-item');
                const total = document.getElementById('purchase-total');
                const form = document.getElementById('purchase-form');
                const select = rows.children[0].querySelector('select');
                const originalOption = [...select.options].find(option => option.dataset.barcode);
                check(originalOption, 'Rendered product barcode metadata');
                const comboInput = rows.children[0].querySelector('.product-combobox__input');
                check(document.querySelectorAll('.product-combobox__option').length === 0, 'Catalog unloaded until requested');
                comboInput.click();
                check(document.querySelectorAll('.product-combobox__option').length === 5, 'Combobox loads first five');
                document.querySelector('.product-combobox__load-more').click();
                check(comboInput.getAttribute('aria-expanded') === 'true', 'Loading more keeps the combobox open');
                check(document.querySelectorAll('.product-combobox__option').length === 10, 'Combobox loads next five');
                document.querySelector('.product-combobox__load-more').click();
                check(comboInput.getAttribute('aria-expanded') === 'true', 'Repeated load more keeps the combobox open');
                check(document.querySelectorAll('.product-combobox__option').length === 15, 'Combobox accumulates results');
                window.dispatchEvent(new Event('resize'));
                check(comboInput.getAttribute('aria-expanded') === 'true', 'Height changes keep the picker open for virtual keyboard');
                comboInput.value = originalOption.dataset.productCode;
                comboInput.dispatchEvent(new Event('input', { bubbles: true }));
                check(document.querySelectorAll('.product-combobox__option').length === 1, 'Combobox filters by code');
                document.querySelector('.product-combobox__option').click();
                check(select.value === originalOption.value, 'Combobox selects product');
                check(rows.children[0].querySelector('[data-field="unitPrice"]').value === originalOption.dataset.cost, 'Purchase uses cost reference');
                comboInput.click();
                comboInput.value = originalOption.dataset.description.normalize('NFD').replace(/[\\u0300-\\u036f]/g, '');
                comboInput.dispatchEvent(new Event('input', { bubbles: true }));
                check([...document.querySelectorAll('.product-combobox__option')].some(button => button.dataset.productId === originalOption.value), 'Search description without accents');
                [...document.querySelectorAll('.product-combobox__option')].find(button => button.dataset.productId === originalOption.value).click();
                const search = document.getElementById('productSearch');
                search.focus();
                search.value = 'CODIGO-INEXISTENTE';
                search.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
                check(!document.getElementById('productActionToast').classList.contains('hidden'), 'Unknown barcode reports visible desktop feedback');
                search.value = originalOption.dataset.barcode;
                search.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
                await new Promise(resolve => setTimeout(resolve, 0));
                check(!document.getElementById('productPreviewModal').classList.contains('hidden'), 'Barcode opens preview');
                rows.children[0].querySelector('[data-field="unitPrice"]').value = '12.34';
                document.getElementById('confirmAddProductBtn').click();
                check(rows.children[0].querySelector('[data-field="quantity"]').value === '2', 'Repeat product increments quantity');
                check(rows.children[0].querySelector('[data-field="unitPrice"]').value === '12.34', 'Repeat product preserves edited purchase price');
                document.getElementById('closeProductPreviewBtn').click();
                check(document.activeElement === search, 'Preview restores focus: ' + document.activeElement.id + ' searchVisible=' + Boolean(search.offsetParent));
                const matchMedia = window.matchMedia;
                window.matchMedia = () => ({ matches: true });
                document.getElementById('toggleSaleCatalogBtn').click();
                check(document.getElementById('saleCatalogList').children.length === 5, 'Mobile catalog starts with five');
                document.getElementById('saleCatalogLoadMore').click();
                check(document.getElementById('saleCatalogList').children.length > 5, 'Mobile catalog loads more');
                const zeroStock = [...select.options].find(option => option.value && option.dataset.stock === '0');
                check(zeroStock, 'Out of stock fixture available');
                const mobileSearch = document.getElementById('productSearchMobile');
                mobileSearch.value = zeroStock.dataset.productCode;
                mobileSearch.dispatchEvent(new Event('input', { bubbles: true }));
                check(document.getElementById('saleCatalogList').children.length === 1, 'Mobile filters by code');
                document.getElementById('saleCatalogList').firstElementChild.click();
                check([...rows.querySelectorAll('select')].some(input => input.value === zeroStock.value), 'Purchases allow out of stock products');
                window.matchMedia = matchMedia;
                window.dispatchEvent(new Event('resize'));
                rows.lastElementChild.querySelector('[data-remove]').click();
                const fill = (row, quantity, price) => {
                    row.querySelector('select').selectedIndex = 1;
                    row.querySelector('[data-field="quantity"]').value = quantity;
                    row.querySelector('[data-field="unitPrice"]').value = price;
                    row.dispatchEvent(new Event('input', { bubbles: true }));
                };
                fill(rows.children[0], '3', '10.15');
                check(total.textContent.includes('30,45'), 'Exact decimal subtotal');
                add.click();
                fill(rows.children[1], '2', '11.00');
                check(total.textContent.includes('52,45'), 'Exact sum across rows');
                rows.children[0].querySelector('[data-remove]').click();
                check(rows.children.length === 1, 'Row removal');
                check(rows.children[0].querySelector('select').name === 'items[0].productId', 'Reindex after removal');
                check(total.textContent.includes('22,00'), 'Total updates after removal');
                check(rows.children[0].querySelector('[data-remove]').disabled, 'Keep at least one row');
                fill(rows.children[0], '0', '11');
                check(!rows.children[0].querySelector('[data-field="quantity"]').checkValidity(), 'Reject zero quantity');
                fill(rows.children[0], '1', '10.001');
                check(!rows.children[0].querySelector('[data-field="unitPrice"]').checkValidity(), 'Reject fractions of cents');
                fill(rows.children[0], '3', '10.15');
                for (let i = 1; i < 100; i++) add.click();
                check(rows.children.length === 100 && add.disabled, 'Limit to 100 items');
                const inputs = [...rows.querySelectorAll('[data-field]')];
                check(new Set(inputs.map(input => input.name)).size === 300, 'Unique binding names');
                check(new Set(inputs.map(input => input.id)).size === 300, 'Unique control ids');
                check(inputs.every(input => input.previousElementSibling.htmlFor === input.id + (input.tagName === 'SELECT' ? '-search' : '')), 'Labels match visible controls');
                form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
                check(form.querySelector('[type="submit"]').disabled, 'Disable duplicate submission');
                window.dispatchEvent(new Event('pageshow'));
                check(!form.querySelector('[type="submit"]').disabled, 'Restore form on back navigation');
                check(!document.documentElement.dataset.purchaseError, 'No runtime errors');
                document.documentElement.dataset.purchaseChecks = 'passed';
            } catch (error) {
                document.documentElement.dataset.purchaseChecks = 'failed: ' + error.message;
            }
        });
    </script>`;
    // External layout dependencies are outside this focused browser test; purchase code is loaded unchanged.
    const html = fs.readFileSync(preview, 'utf8')
        .replace(/<script[^>]+src="https:[\s\S]*?<\/script>/g, '')
        .replace('</body>', checks + '</body>');
    const fixture = path.join(directory, 'form-browser-test.html');
    fs.writeFileSync(fixture, html);
    const result = spawnSync(browser, [
        '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
        '--window-size=1440,1200',
        '--allow-file-access-from-files', '--virtual-time-budget=3000',
        '--user-data-dir=' + path.join(directory, 'browser-profile-' + Date.now()),
        '--dump-dom', pathToFileURL(fixture).href
    ], { encoding: 'utf8', timeout: 45000, maxBuffer: 10 * 1024 * 1024 });
    assert.ifError(result.error);
    assert.equal(result.status, 0, result.stderr);
    const marker = result.stdout.match(/data-purchase-checks="([^"]*)"/);
    assert.equal(marker?.[1], 'passed', marker?.[1] || result.stderr.slice(-2000));

    if (process.env.PURCHASE_SCREENSHOTS === '1') {
        for (const [page, width, height, state = ''] of [['form', 1440, 1200], ['form', 390, 1400], ['form', 1440, 1200, 'combo'], ['form', 390, 1400, 'catalog']]) {
            const original = fs.readFileSync(path.join(directory, page + '.html'), 'utf8');
            const start = original.indexOf('<section class="purchases">');
            assert.ok(start > 0, 'Rendered purchase section exists');
            const content = original.slice(start, original.lastIndexOf('</section>') + '</section>'.length);
            const appCss = pathToFileURL(path.resolve('src/main/resources/static/app.css')).href;
            // Isolate the real module from the app shell and CDN dependencies for reproducible visual review.
            const screen = path.join(directory, `${page}-${width}.html`);
            fs.writeFileSync(screen, `<!doctype html><html lang="es"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="${appCss}">
                <style>h1,h2,p,dl,dd{margin:0}button,input,select,textarea{font:inherit}button{border:0;background:none}body{font-family:var(--font-body)}</style>
                </head><body class="app-body"><main class="app-main" style="padding:24px">${content}</main></body></html>`);
            await captureScreen(browser, screen, width, height, path.join(directory, `${page}-${width}${state ? '-' + state : ''}.png`), state);
        }
    }
});
