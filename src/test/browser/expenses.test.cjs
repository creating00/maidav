// Generate rendered pages with ExpenseIntegrationTest before running this browser test.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const { captureScreen } = require('./chromium-support.cjs');

test('expense form validates paid amounts and renders without horizontal overflow on desktop and mobile', async () => {
    const browser = process.env.CHROMIUM_PATH || [
        'C:/Program Files/Google/Chrome/Application/chrome.exe',
        'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
    ].find(file => fs.existsSync(file));
    assert.ok(browser, 'Set CHROMIUM_PATH to an installed Chromium browser');
    const directory = path.resolve('target/expenses-preview');
    const preview = path.join(directory, 'form.html');
    assert.ok(fs.existsSync(preview), 'Run ExpenseIntegrationTest first');
    const checks = `<script>
        document.addEventListener('DOMContentLoaded', () => {
            try {
                const check = (condition, message) => { if (!condition) throw new Error(message); };
                const form = document.getElementById('expense-form');
                check(form.enctype === 'multipart/form-data', 'Receipt form encoding');
                check(!form.checkValidity(), 'Required fields reject an empty expense');
                form.querySelector('[name="category"]').value = 'SERVICES';
                form.querySelector('[name="paymentMethod"]').value = 'CASH';
                form.querySelector('[name="description"]').value = 'Servicio de electricidad';
                const amount = form.querySelector('[name="amount"]');
                amount.value = '0';
                check(!amount.checkValidity(), 'Reject zero payment');
                amount.value = '-10';
                check(!amount.checkValidity(), 'Reject negative payment');
                amount.value = '10.001';
                check(!amount.checkValidity(), 'Reject fractions of cents');
                amount.value = '15000.25';
                check(form.checkValidity(), 'Receipt is optional for a valid expense');
                check(form.querySelector('[name="receipt"]').accept.includes('application/pdf'), 'PDF receipts accepted');
                check(form.querySelector('[name="operationKey"]').value.length === 36, 'Duplicate protection token');
                document.documentElement.dataset.expenseChecks = 'passed';
            } catch (error) { document.documentElement.dataset.expenseChecks = 'failed: ' + error.message; }
        });
    </script>`;
    const html = fs.readFileSync(preview, 'utf8').replace(/<script[^>]+src="https:[\s\S]*?<\/script>/g, '')
        .replace('</body>', checks + '</body>');
    const fixture = path.join(directory, 'form-browser-test.html');
    fs.writeFileSync(fixture, html);
    const result = spawnSync(browser, [
        '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--window-size=1440,1200',
        '--allow-file-access-from-files', '--virtual-time-budget=2000',
        '--user-data-dir=' + path.join(directory, 'browser-profile-' + Date.now()), '--dump-dom', pathToFileURL(fixture).href
    ], { encoding: 'utf8', timeout: 45000, maxBuffer: 5 * 1024 * 1024 });
    assert.ifError(result.error);
    assert.equal(result.status, 0, result.stderr);
    const marker = result.stdout.match(/data-expense-checks="([^"]*)"/);
    assert.equal(marker?.[1], 'passed', marker?.[1] || result.stderr.slice(-2000));

    for (const [page, width, height] of [['form', 1440, 1100], ['form', 390, 1400], ['index', 1440, 1000], ['detail', 390, 1400]]) {
        const original = fs.readFileSync(path.join(directory, page + '.html'), 'utf8');
        const start = original.indexOf('<section class="purchases expenses">');
        assert.ok(start > 0, 'Rendered expense section exists');
        const content = original.slice(start, original.lastIndexOf('</section>') + '</section>'.length);
        const appCss = pathToFileURL(path.resolve('src/main/resources/static/app.css')).href;
        const screen = path.join(directory, `${page}-${width}.html`);
        fs.writeFileSync(screen, `<!doctype html><html lang="es"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="${appCss}">
            <style>h1,h2,p,dl,dd{margin:0}button,input,select,textarea{font:inherit}button{border:0;background:none}body{font-family:var(--font-body)}</style>
            </head><body class="app-body"><main class="app-main" style="padding:24px">${content}</main></body></html>`);
        await captureScreen(browser, screen, width, height, path.join(directory, `${page}-${width}.png`));
    }
});
