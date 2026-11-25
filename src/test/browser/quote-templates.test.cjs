// First render fixtures: mvn -Dtest=QuoteTemplatesTest test
// Then run: node --test src/test/browser/quote-templates.test.cjs
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '../../..');
const fixtureDir = path.join(root, 'target/quotes-preview');
const browser = process.env.CHROMIUM_PATH || [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
].find(candidate => fs.existsSync(candidate));

function checkInBrowser(name, checks, setup = '') {
    assert.ok(browser, 'Set CHROMIUM_PATH to an installed Chromium browser');
    const source = path.join(fixtureDir, name + '.html');
    assert.ok(fs.existsSync(source), 'Render the fixtures with QuoteTemplatesTest first');
    let html = fs.readFileSync(source, 'utf8');
    // These tests exercise the real rendered form and scripts without CDN availability.
    html = html.replace(/<script[^>]+src="https:[^"]+"[^>]*><\/script>/g, '');
    html = html.replace(/<link[^>]+href="https:[^"]+"[^>]*>/g, '');
    for (const asset of ['app.css', 'barcode-scanner.js', 'favicon.svg']) {
        html = html.replaceAll('"/' + asset + '"', '"' + pathToFileURL(
            path.join(root, 'src/main/resources/static', asset)).href + '"');
    }
    html = html.replace('<head>', `<head><script>
        window.browserErrors = [];
        window.addEventListener('error', event => window.browserErrors.push(event.message));
        ${setup}
    </script>`);
    html = html.replace('</body>', `<script>
        window.addEventListener('DOMContentLoaded', () => {
            try {
                function check(condition, message) { if (!condition) throw new Error(message); }
                ${checks}
                check(window.browserErrors.length === 0, window.browserErrors.join('; '));
                document.body.dataset.result = 'passed';
            } catch (error) { document.body.dataset.error = error.message; }
        });
    </script></body>`);
    const fixturePath = path.join(fixtureDir, name + '-browser.html');
    fs.writeFileSync(fixturePath, html);
    const result = spawnSync(browser, [
        '--headless', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
        '--disable-background-networking', '--allow-file-access-from-files',
        '--user-data-dir=' + path.join(fixtureDir, 'profile-' + name),
        '--dump-dom', pathToFileURL(fixturePath).href
    ], { encoding: 'utf8', timeout: 45000, windowsHide: true, maxBuffer: 3 * 1024 * 1024 });
    assert.ifError(result.error);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /<body[^>]*data-result="passed"/,
        result.stdout.match(/<body[^>]*>/)?.[0] || result.stderr);
}

test('seller sees the template first with its pin and use action', () => {
    checkInBrowser('index-seller', `
        const cards = [...document.querySelectorAll('main article')];
        check(cards.length === 2, 'Both template and own quote must be visible');
        check(cards[0].classList.contains('bg-emerald-50'), 'Template needs a soft green background');
        check(cards[0].textContent.includes('Anclado'), 'Template needs its pin label');
        check(cards[0].querySelector('a[href="/quotes/8/use"]'), 'Template needs its use action');
        check(!document.querySelector('form[action$="/pin"]'), 'Seller must not get pin controls');
    `);
});

test('admin gets separate transfer and pin controls', () => {
    checkInBrowser('index-admin', `
        check(document.querySelectorAll('form[action$="/pin"]').length === 2, 'Admin needs pin controls');
        check(document.body.textContent.includes('Pasar a Venta'), 'Admin needs transfer action');
        check(document.querySelector('form[action="/quotes/8/pin"] input[name="pinned"]').value === 'false',
            'Pinned quote must offer unpinning');
    `);
});

test('price dialog opens modally with accessible title and current prices', () => {
    checkInBrowser('use-seller', `
        const dialog = document.getElementById('quotePricesDialog');
        check(dialog.open && dialog.matches(':modal'), 'Price warning must be modal');
        check(document.getElementById(dialog.getAttribute('aria-labelledby')), 'Dialog needs an accessible title');
        check(dialog.textContent.includes('PDR-6P45AB'), 'Changed article code must be visible');
        check(dialog.textContent.includes('$20000.00') && dialog.textContent.includes('$25000.00'),
            'Both historical and current prices must be visible');
        check(document.activeElement.textContent.trim() === 'Aceptar', 'Accept must get keyboard focus');
        check(dialog.querySelector('form').method === 'post', 'Accept must go through server validation');
    `);
});

test('quote loads all rows and recalculates totals instead of restoring an old sales draft', () => {
    checkInBrowser('sale-seller', `
        const rows = [...document.querySelectorAll('#itemsTable tbody tr')];
        check(rows.length === 2, 'Every quote item must load');
        check(rows[0].querySelector('[name="productIds"]').value === '7', 'Old draft must not replace quote');
        check(rows[0].querySelector('[name="quantities"]').value === '3', 'First quantity must be preserved');
        check(Number(rows[0].querySelector('[name="unitPrices"]').value) === 25000, 'Use current first price');
        check(rows[1].querySelector('[name="productIds"]').value === '9', 'Second product must load');
        check(rows[1].querySelector('[name="quantities"]').value === '2', 'Second quantity must be preserved');
        check(Number(document.getElementById('totalAmount').textContent) === 91000, 'Total must be recalculated');
        check(document.querySelector('[name="paymentType"]').value === 'CASH', 'Default payment must remain valid');
    `, `localStorage.setItem('sales-form-draft-v1', JSON.stringify({
        priceMode: 'WHOLESALE', rows: [{ productId: '9', quantity: '99', unitPrice: '1' }]
    }));`);
});
