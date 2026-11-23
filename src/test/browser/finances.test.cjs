// Run FinanceIntegrationTest against a disposable local PostgreSQL database first.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const { captureScreen } = require('./chromium-support.cjs');

test('finance report renders reconciled totals and responsive positive, negative and empty states', async () => {
    const browser = process.env.CHROMIUM_PATH || [
        'C:/Program Files/Google/Chrome/Application/chrome.exe',
        'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
    ].find(file => fs.existsSync(file));
    assert.ok(browser, 'Set CHROMIUM_PATH to an installed Chromium browser');
    const directory = path.resolve('target/finances-preview');
    const preview = path.join(directory, 'summary.html');
    assert.ok(fs.existsSync(preview), 'Run FinanceIntegrationTest first');
    const checks = `<script>
        document.addEventListener('DOMContentLoaded', () => {
            try {
                const check = (condition, message) => { if (!condition) throw new Error(message); };
                const form = document.querySelector('.finance-filters');
                check(form.method === 'get', 'Report is read-only');
                check(form.querySelector('[name="from"]').value === '1984-06-15', 'Selected start date');
                check(form.querySelector('[name="to"]').value === '1984-06-15', 'Selected end date');
                check(document.getElementById('finance-income').textContent.includes('1.750,00'), 'Net collections rendered');
                check(document.getElementById('finance-payments').textContent.includes('250,00'), 'Purchases plus expenses rendered');
                check(document.getElementById('finance-difference').textContent.includes('1.500,00'), 'Period difference rendered');
                const criteria = document.querySelector('.finance-criteria');
                check(!criteria.open, 'Criteria initially collapsed');
                criteria.querySelector('summary').click();
                check(criteria.open, 'Calculation criteria can be inspected');
                const totals = [...document.querySelectorAll('tfoot td')].map(cell => cell.textContent);
                check(totals[0].includes('1.750,00') && totals[1].includes('250,00') && totals[2].includes('1.500,00'), 'Payment method footer reconciles');
                document.documentElement.dataset.financeChecks = 'passed';
            } catch (error) { document.documentElement.dataset.financeChecks = 'failed: ' + error.message; }
        });
    </script>`;
    const html = fs.readFileSync(preview, 'utf8').replace(/<script[^>]+src="https:[\s\S]*?<\/script>/g, '')
        .replace('</body>', checks + '</body>');
    const fixture = path.join(directory, 'summary-browser-test.html');
    fs.writeFileSync(fixture, html);
    const result = spawnSync(browser, [
        '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--window-size=1440,1200',
        '--allow-file-access-from-files', '--virtual-time-budget=2000',
        '--user-data-dir=' + path.join(directory, 'browser-profile-' + Date.now()), '--dump-dom', pathToFileURL(fixture).href
    ], { encoding: 'utf8', timeout: 45000, maxBuffer: 5 * 1024 * 1024 });
    assert.ifError(result.error);
    assert.equal(result.status, 0, result.stderr);
    const marker = result.stdout.match(/data-finance-checks="([^"]*)"/);
    assert.equal(marker?.[1], 'passed', marker?.[1] || result.stderr.slice(-2000));
    assert.match(fs.readFileSync(path.join(directory, 'negative.html'), 'utf8'), /finance-negative/);
    assert.match(fs.readFileSync(path.join(directory, 'empty.html'), 'utf8'), /Sin movimientos en este/);
    for (const [page, width, height] of [['summary', 1440, 1200], ['summary', 390, 1400], ['negative', 390, 1200], ['excluded', 1440, 1200], ['empty', 390, 1200]]) {
        const original = fs.readFileSync(path.join(directory, page + '.html'), 'utf8');
        const start = original.indexOf('<section class="purchases finances">');
        assert.ok(start > 0, 'Rendered finance section exists');
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
