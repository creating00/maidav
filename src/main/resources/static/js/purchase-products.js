(() => {
    'use strict';
    const form = document.getElementById('purchase-form');
    if (!form) return;
    const rows = document.getElementById('purchase-items');
    const byId = id => document.getElementById(id);
    const normalize = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
    const money = value => new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(Number(value) || 0);
    const mobile = () => window.matchMedia('(max-width: 767px)').matches;
    const catalog = byId('saleCatalogPanel');
    const catalogList = byId('saleCatalogList');
    const preview = byId('productPreviewModal');
    const scannerPanel = byId('barcodeScannerPanel');
    const toolbar = catalog.parentElement;
    let cache;
    let catalogLimit = 5;
    let activeRow;
    let pendingProduct;
    let returnFocus;
    let scanner;
    let toastTimer;
    let compactViewport = mobile();

    function products() {
        if (!cache) {
            cache = [...rows.querySelector('select').options].filter(option => option.value).map(option => ({
                id: option.value, code: option.dataset.productCode || '', barcode: option.dataset.barcode || '',
                description: option.dataset.description || option.textContent, image: option.dataset.image || '',
                stock: Number(option.dataset.stock) || 0, price: option.dataset.cost || '0',
                search: normalize([option.dataset.productCode, option.dataset.barcode, option.dataset.description].join(' '))
            })).sort((a, b) => b.stock - a.stock || a.description.localeCompare(b.description, 'es'));
        }
        return cache;
    }

    function imageFor(product, className) {
        const media = document.createElement('span');
        media.className = className;
        if (product.image) {
            const image = document.createElement('img');
            image.loading = 'lazy';
            image.decoding = 'async';
            image.src = product.image;
            image.alt = '';
            image.className = 'product-combobox__thumb';
            image.addEventListener('error', () => { media.textContent = 'Sin imagen'; }, { once: true });
            media.append(image);
        } else {
            media.textContent = 'Sin imagen';
        }
        return media;
    }

    function productCard(product, selected, choose, isCatalog = false) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = isCatalog
            ? 'flex w-full items-center gap-4 rounded-3xl border border-slate-200 bg-white p-3 text-left shadow-sm transition hover:border-slate-300 hover:bg-slate-50 purchase-catalog-card'
            : 'product-combobox__option';
        button.dataset.productId = product.id;
        button.classList.toggle('is-selected', selected === product.id);
        button.append(imageFor(product, 'product-combobox__media'));
        const details = document.createElement('span');
        details.className = 'product-combobox__content';
        const title = document.createElement('strong');
        title.className = 'product-combobox__title';
        title.textContent = product.description;
        const code = document.createElement('span');
        code.className = 'product-combobox__code';
        code.textContent = 'Código: ' + product.code;
        details.append(title, code);
        const meta = document.createElement('span');
        meta.className = 'product-combobox__meta';
        const stock = document.createElement('span');
        stock.className = 'product-combobox__stock';
        stock.textContent = 'Stock: ' + product.stock;
        const price = document.createElement('span');
        price.className = 'product-combobox__price';
        price.textContent = money(product.price);
        price.title = 'Costo de referencia; editable en la compra';
        meta.append(stock, price);
        if (isCatalog) { details.append(meta); button.append(details); }
        else button.append(details, meta);
        button.addEventListener('click', () => choose(product));
        return button;
    }

    function closeCombo(row = activeRow) {
        if (!row) return;
        const input = row.querySelector('.product-combobox__input');
        row.querySelector('.product-combobox__panel').classList.add('hidden');
        input.setAttribute('aria-expanded', 'false');
        const select = row.querySelector('select');
        input.value = select.value ? select.selectedOptions[0].textContent.trim() : '';
        input.setCustomValidity(select.value ? '' : 'Seleccione un producto del catálogo');
        if (activeRow === row) activeRow = null;
    }

    function renderCombo(row, term = '', limit = 5) {
        const options = row.querySelector('.product-combobox__options');
        const matches = products().filter(product => product.search.includes(normalize(term)));
        options.replaceChildren();
        row.querySelector('.product-combobox__empty').classList.toggle('hidden', matches.length > 0);
        matches.slice(0, limit).forEach(product => options.append(productCard(product, row.querySelector('select').value, selected => {
            const select = row.querySelector('select');
            const changed = select.value !== selected.id;
            select.value = selected.id;
            if (changed) select.dispatchEvent(new Event('change', { bubbles: true }));
            closeCombo(row);
            row.querySelector('.product-combobox__input').focus();
        })));
        if (matches.length > limit) {
            const more = document.createElement('button');
            more.type = 'button';
            more.className = 'product-combobox__load-more';
            more.textContent = `Mostrar más productos (${matches.length - limit} restantes)`;
            more.addEventListener('mousedown', event => event.preventDefault());
            more.addEventListener('click', event => {
                // Rendering replaces this button; do not let its detached click reach the outside-click handler.
                event.preventDefault();
                event.stopPropagation();
                const scrollTop = options.scrollTop;
                const restoreFocus = document.activeElement === more;
                renderCombo(row, term, limit + 5);
                options.scrollTop = scrollTop;
                if (restoreFocus) options.querySelectorAll('.product-combobox__option')[limit]?.focus({ preventScroll: true });
            });
            options.append(more);
        }
    }

    function openCombo(row, term = '') {
        if (activeRow && activeRow !== row) closeCombo();
        activeRow = row;
        row.querySelector('.product-combobox__panel').classList.remove('hidden');
        row.querySelector('.product-combobox__input').setAttribute('aria-expanded', 'true');
        const sheet = row.querySelector('.product-combobox__sheet-search');
        sheet.value = term;
        renderCombo(row, term);
        if (mobile()) sheet.focus();
    }

    function initializeRows() {
        [...rows.children].forEach(row => {
            const select = row.querySelector('select');
            const input = row.querySelector('.product-combobox__input');
            const panel = row.querySelector('.product-combobox__panel');
            input.id = select.id + '-search';
            panel.id = select.id + '-options';
            input.setAttribute('aria-controls', panel.id);
            select.previousElementSibling.htmlFor = input.id;
            if (row.dataset.pickerReady) return;
            row.dataset.pickerReady = 'true';
            select.classList.add('product-select-native');
            select.tabIndex = -1;
            select.required = false;
            select.setAttribute('aria-hidden', 'true');
            input.required = true;
            input.setAttribute('role', 'combobox');
            input.setAttribute('aria-autocomplete', 'list');
            input.setAttribute('aria-expanded', 'false');
            closeCombo(row);
            input.addEventListener('click', () => openCombo(row));
            input.addEventListener('input', () => {
                input.setCustomValidity('Seleccione un producto del catálogo');
                openCombo(row, input.value);
            });
            const sheet = row.querySelector('.product-combobox__sheet-search');
            sheet.addEventListener('input', () => renderCombo(row, sheet.value));
            row.querySelector('.product-combobox__toggle').addEventListener('click', () => {
                if (activeRow === row) closeCombo(); else openCombo(row);
            });
            select.addEventListener('change', () => {
                input.value = select.selectedOptions[0]?.textContent.trim() || '';
                input.setCustomValidity(select.value ? '' : 'Seleccione un producto del catálogo');
                row.querySelector('.product-combobox').classList.toggle('has-selection', Boolean(select.value));
            });
            [input, sheet].forEach(search => search.addEventListener('keydown', event => {
                if (event.key === 'Escape') { event.preventDefault(); closeCombo(row); input.focus(); }
                if (event.key === 'ArrowDown') {
                    event.preventDefault();
                    if (activeRow !== row) openCombo(row);
                    panel.querySelector('.product-combobox__option')?.focus();
                }
                if (event.key === 'Enter') {
                    event.preventDefault();
                    panel.querySelector('.product-combobox__option')?.click();
                }
            }));
            panel.addEventListener('keydown', event => {
                const choices = [...panel.querySelectorAll('button')];
                const index = choices.indexOf(document.activeElement);
                if (event.key === 'Escape') { closeCombo(row); input.focus(); }
                if (['ArrowDown', 'ArrowUp'].includes(event.key)) {
                    event.preventDefault();
                    choices[Math.max(0, Math.min(choices.length - 1, index + (event.key === 'ArrowDown' ? 1 : -1)))]?.focus();
                }
            });
        });
    }

    function notify(message, error = false) {
        const feedback = byId('saleCatalogFeedback');
        feedback.textContent = message;
        feedback.className = 'purchase-' + (error ? 'error' : 'success');
        feedback.setAttribute('role', 'status');
        const toast = byId('productActionToast');
        toast.textContent = message;
        toast.className = 'purchase-' + (error ? 'error' : 'success');
        toast.setAttribute('role', error ? 'alert' : 'status');
        toast.setAttribute('aria-hidden', String(mobile()));
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => toast.classList.add('hidden'), 2400);
    }

    function addProduct(product) {
        const detail = { productId: product.id, added: false };
        form.dispatchEvent(new CustomEvent('purchase:add-product', { detail }));
        notify(detail.added ? 'Se agregó el producto a la compra' : 'No se pudo agregar: revise el límite de ítems o cantidad', !detail.added);
        if (detail.added && mobile()) { byId('productSearchMobile').value = ''; renderCatalog(); }
        return detail.added;
    }

    function renderCatalog(reset = true) {
        if (catalog.classList.contains('hidden') || !mobile()) return;
        if (reset) catalogLimit = 5;
        const term = normalize(byId('productSearchMobile').value);
        const matches = products().filter(product => product.search.includes(term));
        catalogList.replaceChildren(...matches.slice(0, catalogLimit).map(product => productCard(product, '', addProduct, true)));
        byId('saleCatalogEmpty').classList.toggle('hidden', matches.length > 0);
        const more = byId('saleCatalogLoadMore');
        more.hidden = matches.length <= catalogLimit;
        more.textContent = `Mostrar más productos (${Math.max(0, matches.length - catalogLimit)} restantes)`;
    }

    function closePreview() {
        preview.classList.add('hidden');
        preview.setAttribute('aria-hidden', 'true');
        pendingProduct = null;
        returnFocus?.focus();
    }

    async function previewCode(code, trigger = document.activeElement) {
        const term = normalize(code);
        const product = products().find(product => normalize(product.barcode) === term || normalize(product.code) === term);
        if (!term || !product) { notify('No se encontró un producto con ese código', true); return; }
        await stopScanner();
        closeCombo();
        pendingProduct = product;
        returnFocus = trigger;
        byId('productPreviewDescription').textContent = product.description;
        byId('productPreviewCode').textContent = product.code;
        byId('productPreviewStock').textContent = product.stock;
        byId('productPreviewPrice').textContent = money(product.price);
        byId('productPreviewPrice').previousElementSibling.textContent = 'Costo de referencia';
        byId('productPreviewMessage').textContent = 'Revise el producto. El precio de adquisición se puede editar en la compra.';
        if (product.image) byId('productPreviewImage').src = product.image;
        else byId('productPreviewImage').removeAttribute('src');
        byId('productPreviewImage').classList.toggle('hidden', !product.image);
        byId('productPreviewImagePlaceholder').classList.toggle('hidden', Boolean(product.image));
        byId('confirmAddProductBtn').classList.remove('hidden');
        byId('productPreviewAfterActions').classList.add('hidden');
        preview.classList.remove('hidden');
        preview.setAttribute('aria-hidden', 'false');
        byId('confirmAddProductBtn').focus();
    }

    async function stopScanner() {
        if (scanner) await scanner.stop();
        scannerPanel.classList.add('hidden');
        ['stopBarcodeScannerBtn', 'stopBarcodeScannerBtnMobile'].forEach(id => byId(id).classList.add('hidden'));
        ['startBarcodeScannerBtn', 'startBarcodeScannerBtnMobile'].forEach(id => byId(id).classList.remove('hidden'));
    }

    async function startScanner() {
        if (!window.MaidavBarcodeScanner) { notify('No se pudo inicializar el lector', true); return; }
        if (!scanner) scanner = window.MaidavBarcodeScanner.create({
            videoElement: byId('barcodeScannerVideo'), fallbackElement: byId('barcodeScannerReader'),
            onStatus: message => { byId('barcodeScannerStatus').textContent = message; },
            onDetected: code => previewCode(code, mobile() ? byId('productSearchMobile') : byId('productSearch'))
        });
        toolbar.append(scannerPanel);
        scannerPanel.classList.remove('hidden');
        try {
            await scanner.start();
            ['startBarcodeScannerBtn', 'startBarcodeScannerBtnMobile'].forEach(id => byId(id).classList.add('hidden'));
            ['stopBarcodeScannerBtn', 'stopBarcodeScannerBtnMobile'].forEach(id => byId(id).classList.remove('hidden'));
        } catch (error) { await stopScanner(); notify(error.message || 'No se pudo acceder a la cámara', true); }
    }

    form.addEventListener('purchase:rows-changed', initializeRows);
    document.addEventListener('click', event => { if (!event.target.closest('.product-combobox')) closeCombo(); });
    byId('toggleSaleCatalogBtn').addEventListener('click', () => {
        const open = catalog.classList.toggle('hidden') === false;
        byId('toggleSaleCatalogBtn').textContent = open ? 'Ocultar catalogo' : 'Mostrar catalogo';
        if (open) renderCatalog(); else { catalogList.replaceChildren(); stopScanner(); }
    });
    byId('productSearchMobile').addEventListener('input', () => renderCatalog());
    ['productSearch', 'productSearchMobile'].forEach(id => byId(id).addEventListener('keydown', event => {
        if (event.key === 'Enter') { event.preventDefault(); previewCode(event.target.value, event.target); }
    }));
    byId('saleCatalogLoadMore').addEventListener('click', () => { catalogLimit += 5; renderCatalog(false); });
    byId('confirmAddProductBtn').addEventListener('click', () => {
        if (!pendingProduct || !addProduct(pendingProduct)) return;
        byId('confirmAddProductBtn').classList.add('hidden');
        byId('productPreviewAfterActions').classList.remove('hidden');
        byId('productPreviewMessage').textContent = 'Producto agregado a la compra';
        byId('closeProductPreviewBtn').focus();
    });
    byId('closeProductPreviewBtn').addEventListener('click', closePreview);
    byId('continueAfterAddBtn').addEventListener('click', () => {
        closePreview();
        (mobile() ? byId('productSearchMobile') : byId('productSearch')).focus();
    });
    preview.setAttribute('role', 'dialog');
    preview.setAttribute('aria-modal', 'true');
    preview.setAttribute('aria-labelledby', 'productPreviewTitle');
    preview.setAttribute('aria-hidden', 'true');
    preview.addEventListener('click', event => { if (event.target === preview) closePreview(); });
    preview.addEventListener('keydown', event => {
        if (event.key === 'Escape') closePreview();
        if (event.key !== 'Tab') return;
        const buttons = [...preview.querySelectorAll('button')].filter(button => button.offsetParent !== null);
        const first = buttons[0], last = buttons.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    });
    ['startBarcodeScannerBtn', 'startBarcodeScannerBtnMobile'].forEach(id => byId(id).addEventListener('click', startScanner));
    ['stopBarcodeScannerBtn', 'stopBarcodeScannerBtnMobile'].forEach(id => byId(id).addEventListener('click', stopScanner));
    window.addEventListener('pagehide', stopScanner);
    window.addEventListener('resize', () => {
        const compact = mobile();
        if (compact !== compactViewport) closeCombo();
        compactViewport = compact;
        if (!compact) { catalog.classList.add('hidden'); catalogList.replaceChildren(); byId('toggleSaleCatalogBtn').textContent = 'Mostrar catalogo'; }
    });
})();
