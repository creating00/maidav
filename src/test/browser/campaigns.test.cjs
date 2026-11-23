const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const { captureScreen } = require('./chromium-support.cjs');
const directory = path.resolve('target/campaigns-preview');
const browser = process.env.CHROMIUM_PATH || [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
].find(file => fs.existsSync(file));

function checkPage(name, checks, prelude = '') {
    assert.ok(browser, 'Set CHROMIUM_PATH');
    const source = path.join(directory, name + '.html');
    assert.ok(fs.existsSync(source), 'Run CampaignIntegrationTest first');
    const script = `<script>document.addEventListener('DOMContentLoaded', () => {try {
        const check = (condition, message) => { if (!condition) throw new Error(message); };
        ${checks}
        check(!window.campaignErrors.length, 'No runtime errors: ' + window.campaignErrors.join('; '));
        document.documentElement.dataset.campaignChecks = 'passed';
    } catch(error) { document.documentElement.dataset.campaignChecks = 'failed: ' + error.message; }});</script>`;
    const html = fs.readFileSync(source, 'utf8').replace(/<script[^>]+src="https:[\s\S]*?<\/script>/g, '')
        .replace('<head>', `<head><script>window.campaignErrors=[];window.addEventListener('error',event=>window.campaignErrors.push(event.message));${prelude}</script>`)
        .replace('</body>', script + '</body>');
    const fixture = path.join(directory, name + '-browser-test.html'); fs.writeFileSync(fixture, html);
    const result = spawnSync(browser, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
        '--window-size=1440,1200', '--allow-file-access-from-files', '--virtual-time-budget=2500',
        '--user-data-dir=' + path.join(directory, name + '-profile-' + Date.now()), '--dump-dom', pathToFileURL(fixture).href
    ], { encoding: 'utf8', timeout: 45000, maxBuffer: 12 * 1024 * 1024 });
    assert.ifError(result.error); assert.equal(result.status, 0, result.stderr);
    const marker = result.stdout.match(/data-campaign-checks="([^"]*)"/);
    assert.equal(marker?.[1], 'passed', marker?.[1] || result.stderr.slice(-2000));
}

test('campaign form shares product picker, paginates without closing and previews fixed and percentage prices', async () => {
    checkPage('form', `
        const form = document.getElementById('campaign-form');
        const rows = document.getElementById('campaign-items');
        const row = rows.firstElementChild;
        const select = row.querySelector('[data-field="productId"]');
        const option = [...select.options].find(option => Number(option.dataset.price) === 1000);
        check(option, 'Retail price fixture');
        const search = row.querySelector('.product-combobox__input');
        search.click();
        check(row.querySelectorAll('.product-combobox__option').length === 5, 'First five products');
        row.querySelector('.product-combobox__load-more').click();
        check(search.getAttribute('aria-expanded') === 'true', 'Load more keeps picker open');
        check(row.querySelectorAll('.product-combobox__option').length === 10, 'Next five products');
        search.value = option.dataset.code; search.dispatchEvent(new Event('input',{bubbles:true}));
        row.querySelector('.product-combobox__option').click();
        const value = row.querySelector('[data-field="discountValue"]'); value.value='800'; value.dispatchEvent(new Event('input',{bubbles:true}));
        check(row.querySelector('[data-normal]').textContent.includes('1.000,00'), 'Normal retail reference');
        check(row.querySelector('[data-offer]').textContent.includes('800,00'), 'Fixed offer preview');
        const mode = row.querySelector('[data-field="discountMode"]'); mode.value='PERCENTAGE'; value.value='10'; mode.dispatchEvent(new Event('change',{bubbles:true}));
        check(row.querySelector('[data-offer]').textContent.includes('900,00'), 'Percentage offer preview');
        value.value='100'; value.dispatchEvent(new Event('input',{bubbles:true})); check(!value.checkValidity(), 'Reject free or excessive discount');
        value.value='10'; value.dispatchEvent(new Event('input',{bubbles:true}));
        document.getElementById('name').value='Ofertas para clientes';
        check(form.checkValidity(), 'Campaign ready for submission');
        document.getElementById('add-campaign-item').click();
        check(rows.children.length === 2, 'Add campaign product');
        rows.lastElementChild.querySelector('[data-remove]').click(); check(rows.children.length === 1, 'Remove campaign product');
    `);
    for (const [page, width, height] of [['form',1440,1200], ['form',390,1400], ['detail',1440,1100], ['detail',390,1400]]) {
        const original = fs.readFileSync(path.join(directory,page+'.html'),'utf8');
        const start = original.indexOf('<section class="purchases campaigns">'); assert.ok(start > 0);
        const content = original.slice(start, original.lastIndexOf('</section>') + '</section>'.length);
        const screen = path.join(directory,`${page}-${width}.html`);
        fs.writeFileSync(screen, `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <link rel="stylesheet" href="${pathToFileURL(path.resolve('src/main/resources/static/app.css')).href}">
            <style>h1,h2,p,dl,dd{margin:0}button,input,select,textarea{font:inherit}button{border:0;background:none}body{font-family:var(--font-body)}</style></head>
            <body class="app-body"><main class="app-main" style="padding:24px">${content}</main></body></html>`);
        await captureScreen(browser, screen, width, height, path.join(directory,`${page}-${width}.png`));
    }
});

test('sale from offer prefills product and preserves fixed promotional price without overwriting regular-sale draft', () => {
    checkPage('sale', `
        const data = document.getElementById('campaignSaleData'); check(data,'Offer data present');
        const form = document.getElementById('saleForm');
        const selected = [...document.querySelectorAll('#itemsBody tr')].find(row => row.querySelector('.product-select').value === data.dataset.productId);
        check(selected,'Promoted product prefilled');
        const price = selected.querySelector('[name="unitPrices"]');
        check(Number(price.value) === 800 && price.readOnly,'Promotional unit price prefilled and locked');
        check(form.querySelector('[name="paymentType"]').value === 'CASH','Cash sale selected');
        check(form.querySelector('[name="paymentType"] option[value="CREDIT"]').disabled,'Credit disabled for this offer');
        selected.querySelector('[name="quantities"]').value='2'; selected.querySelector('[name="quantities"]').dispatchEvent(new Event('input',{bubbles:true}));
        check(Number(document.getElementById('totalAmount').textContent) === 1600,'Quantity changes preserve offer price');
        price.value='1'; price.dispatchEvent(new Event('input',{bubbles:true})); check(Number(price.value) === 800,'Pricing controls preserve authorized offer');
        check(document.getElementById('applyCashBtn').disabled,'Automatic repricing disabled');
        check(JSON.parse(localStorage.getItem('sales-form-draft-v1')).rows[0].productId === '999999','Regular draft unchanged');
    `, `localStorage.setItem('sales-form-draft-v1',JSON.stringify({rows:[{productId:'999999',quantity:'99',unitPrice:'1'}]}));`);
});
