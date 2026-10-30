// Run with: node --test src/test/browser/sales-catalog.test.cjs
// Uses an installed Chromium browser; no application or database is needed.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');

const source = fs.readFileSync(path.join(__dirname,
    '../../main/resources/templates/pages/sales/form.html'), 'utf8');

function sourceFunction(name) {
    const start = source.indexOf('            function ' + name + '(');
    assert.notEqual(start, -1, 'Missing function: ' + name);
    const end = source.indexOf('\n            function ', start + 1);
    assert.notEqual(end, -1, 'Missing function boundary: ' + name);
    return source.slice(start, end);
}

test('catalog stays unloaded until opened and renders products in bounded pages', () => {
    const browser = process.env.CHROMIUM_PATH || [
        'C:/Program Files/Google/Chrome/Application/chrome.exe',
        'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
    ].find(candidate => fs.existsSync(candidate));
    assert.ok(browser, 'Set CHROMIUM_PATH to an installed Chromium browser');

    // Also reject syntax errors anywhere in the real form script.
    const formScript = source.match(/<script>([\s\S]*?)<\/script>/)[1];
    new Function(formScript);

    const loadMoreStart = source.indexOf('            if (saleCatalogLoadMore) {');
    const loadMoreEnd = source.indexOf('\n            if (startBarcodeScannerBtn)', loadMoreStart);
    assert.ok(loadMoreStart > 0 && loadMoreEnd > loadMoreStart);
    const loadMoreHandler = source.slice(loadMoreStart, loadMoreEnd);
    const functions = ['setSaleCatalogPanelOpen', 'buildSaleCatalogCard', 'renderSaleCatalogList']
        .map(sourceFunction).join('\n');

    const fixture = `<!doctype html><html><body>
        <button id="toggleSaleCatalogBtn"></button>
        <div id="saleCatalogPanel" class="hidden">
            <div id="saleCatalogEmpty" class="hidden"></div>
            <div id="saleCatalogList"></div>
            <button id="saleCatalogLoadMore" hidden></button>
        </div>
        <script>
        try {
            const saleCatalogPanel = document.getElementById('saleCatalogPanel');
            const saleCatalogList = document.getElementById('saleCatalogList');
            const saleCatalogEmpty = document.getElementById('saleCatalogEmpty');
            const saleCatalogLoadMore = document.getElementById('saleCatalogLoadMore');
            const toggleSaleCatalogBtn = document.getElementById('toggleSaleCatalogBtn');
            let saleCatalogOpen = false;
            const saleCatalogPageSize = 5;
            let saleCatalogLimit = saleCatalogPageSize;
            let saleCatalogTerm = '';
            let desktop = true;
            let term = '';
            let imageAssignments = 0;
            let cacheReads = 0;
            let selectedProduct = null;
            window.matchMedia = () => ({ matches: desktop });
            const imageSrc = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, 'src');
            Object.defineProperty(HTMLImageElement.prototype, 'src', {
                get: imageSrc.get,
                set(value) { imageAssignments++; imageSrc.set.call(this, value); }
            });
            const products = Array.from({ length: 527 }, (_, i) => ({
                value: String(i), description: 'Product ' + i, productCode: String(i),
                searchText: 'product ' + i, stock: 10,
                imagePath: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg"/%3E'
            }));
            const buildProductCatalogCache = () => { cacheReads++; return products; };
            const productSearchMobile = { get value() { return term; } };
            const productSearch = { value: 'desktop barcode' };
            const normalizeSearchTerm = value => value.toLowerCase().trim();
            const getProductPriceForCatalogItem = () => 100;
            const money = value => String(value);
            const findProductOptionByValue = value => products.find(item => item.value === value);
            const addProductOptionToCart = item => { selectedProduct = item.value; return true; };
            const showProductToast = () => {};
            const isCompactMobileViewport = () => false;
            ${functions}
            ${loadMoreHandler}
            function check(condition, message) { if (!condition) throw new Error(message); }
            function count() { return saleCatalogList.children.length; }

            setSaleCatalogPanelOpen(false);
            renderSaleCatalogList('');
            setSaleCatalogPanelOpen(true);
            check(count() === 0 && imageAssignments === 0 && cacheReads === 0,
                'Desktop initialization must not build the mobile catalog or assign image URLs');

            desktop = false;
            setSaleCatalogPanelOpen(false);
            renderSaleCatalogList('product');
            check(imageAssignments === 0 && cacheReads === 0, 'Closed mobile catalog must stay unloaded');
            setSaleCatalogPanelOpen(true);
            check(count() === 5 && imageAssignments === 5, 'Opening must render only the first five');
            check(!saleCatalogLoadMore.hidden, 'More results must be reachable');
            check([...saleCatalogList.querySelectorAll('img')].every(img =>
                img.loading === 'lazy' && img.decoding === 'async'), 'Catalog images must load lazily');
            saleCatalogLoadMore.click();
            check(count() === 10, 'Load more must reveal another five products');

            term = 'product 526';
            renderSaleCatalogList(term);
            check(count() === 1 && saleCatalogLoadMore.hidden, 'Search must reach the last product');
            saleCatalogList.firstElementChild.click();
            check(selectedProduct === '526', 'Filtered product must still be added to the cart');
            term = 'missing';
            renderSaleCatalogList(term);
            check(count() === 0 && !saleCatalogEmpty.classList.contains('hidden')
                && saleCatalogLoadMore.hidden, 'Empty results must show the empty state');
            term = '';
            renderSaleCatalogList(term);
            check(count() === 5, 'Changing search must reset pagination');
            term = 'product 52';
            renderSaleCatalogList(term);
            saleCatalogLoadMore.click();
            check(count() === 8 && saleCatalogLoadMore.hidden,
                'Pagination must preserve the mobile search instead of using the desktop barcode field');
            term = '';
            renderSaleCatalogList(term);
            while (!saleCatalogLoadMore.hidden) saleCatalogLoadMore.click();
            check(count() === 527, 'Every product must remain accessible through pagination');

            desktop = true;
            setSaleCatalogPanelOpen(saleCatalogOpen);
            check(!saleCatalogOpen && count() === 0, 'Desktop resize must clear the hidden catalog');
            const beforeClosedSearch = imageAssignments;
            renderSaleCatalogList('');
            check(imageAssignments === beforeClosedSearch, 'Searching on desktop must not load catalog images');
            desktop = false;
            setSaleCatalogPanelOpen(true);
            check(count() === 5, 'Reopening must reset to a bounded page');
            document.body.dataset.result = 'passed';
        } catch (error) {
            document.body.dataset.result = 'failed';
            document.body.dataset.error = error.message;
        }
        </script></body></html>`;

    const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'maidav-catalog-test-'));
    try {
        const fixturePath = path.join(scratch, 'catalog.html');
        fs.writeFileSync(fixturePath, fixture);
        const result = spawnSync(browser, [
            '--headless', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
            '--disable-background-networking', '--user-data-dir=' + path.join(scratch, 'profile'),
            '--dump-dom', pathToFileURL(fixturePath).href
        ], { encoding: 'utf8', timeout: 45000, windowsHide: true, maxBuffer: 2 * 1024 * 1024 });
        assert.ifError(result.error);
        assert.equal(result.status, 0, result.stderr);
        assert.match(result.stdout, /<body data-result="passed">/,
            result.stdout.match(/<body[^>]*>/)?.[0] || result.stderr);
    } finally {
        fs.rmSync(scratch, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
    }
});
