(() => {
    'use strict';
    const form = document.getElementById('campaign-form');
    if (!form) return;
    const rows = document.getElementById('campaign-items');
    const add = document.getElementById('add-campaign-item');
    const prototype = rows.firstElementChild.cloneNode(true);
    const normalize = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
    const cents = value => {
        if (!/^\d{1,10}(\.\d{1,2})?$/.test(value || '')) return 0n;
        const [whole, decimal = ''] = value.split('.');
        return BigInt(whole) * 100n + BigInt(decimal.padEnd(2, '0'));
    };
    const money = value => new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(Number(value) / 100);
    const products = [...rows.querySelector('select').options].filter(option => option.value).map(option => ({
        id: option.value, text: option.textContent.trim(), code: option.dataset.code, description: option.dataset.description,
        image: option.dataset.image, price: option.dataset.price,
        search: normalize(option.dataset.code + ' ' + option.dataset.description)
    }));

    function close(row) {
        row.querySelector('.product-combobox__panel').classList.add('hidden');
        const select = row.querySelector('[data-field="productId"]');
        const input = row.querySelector('.product-combobox__input');
        input.value = select.value ? select.selectedOptions[0].textContent.trim() : '';
        input.setAttribute('aria-expanded', 'false');
        input.setCustomValidity(select.value ? '' : 'Seleccione un producto del catálogo');
    }

    function render(row, term = '', limit = 5) {
        const panel = row.querySelector('.product-combobox__panel');
        const list = row.querySelector('.product-combobox__options');
        const matches = products.filter(product => product.search.includes(normalize(term)));
        panel.classList.remove('hidden');
        row.querySelector('.product-combobox__input').setAttribute('aria-expanded', 'true');
        list.replaceChildren();
        row.querySelector('.product-combobox__empty').classList.toggle('hidden', matches.length > 0);
        matches.slice(0, limit).forEach(product => {
            const button = document.createElement('button');
            button.type = 'button'; button.className = 'product-combobox__option';
            const media = document.createElement('span'); media.className = 'product-combobox__media';
            if (product.image) { const image = document.createElement('img'); image.className = 'product-combobox__thumb'; image.src = product.image; image.alt = ''; image.loading = 'lazy'; media.append(image); }
            else media.textContent = 'Sin imagen';
            const content = document.createElement('span'); content.className = 'product-combobox__content';
            const title = document.createElement('strong'); title.className = 'product-combobox__title'; title.textContent = product.description;
            const code = document.createElement('span'); code.className = 'product-combobox__code'; code.textContent = product.code;
            content.append(title, code);
            const price = document.createElement('span'); price.className = 'product-combobox__price'; price.textContent = money(cents(product.price));
            button.append(media, content, price);
            button.addEventListener('click', () => { row.querySelector('[data-field="productId"]').value = product.id; close(row); refresh(); });
            list.append(button);
        });
        if (matches.length > limit) {
            const more = document.createElement('button'); more.type = 'button'; more.className = 'product-combobox__load-more'; more.textContent = 'Mostrar más productos';
            more.addEventListener('mousedown', event => event.preventDefault());
            more.addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); const scroll = list.scrollTop; render(row, term, limit + 5); list.scrollTop = scroll; });
            list.append(more);
        }
    }

    function refresh() {
        [...rows.children].forEach((row, index) => {
            row.querySelectorAll('[data-field]').forEach(input => {
                input.name = `items[${index}].${input.dataset.field}`; input.id = `campaign-${input.dataset.field}-${index}`;
                input.previousElementSibling.htmlFor = input.id;
            });
            const select = row.querySelector('[data-field="productId"]');
            const input = row.querySelector('.product-combobox__input');
            input.id = select.id + '-search'; select.previousElementSibling.htmlFor = input.id;
            if (!row.dataset.ready) {
                row.dataset.ready = 'true'; select.classList.add('product-select-native'); select.required = false; select.tabIndex = -1; select.setAttribute('aria-hidden', 'true');
                input.required = true; input.setAttribute('role', 'combobox');
                input.addEventListener('click', () => render(row)); input.addEventListener('input', () => { input.setCustomValidity('Seleccione un producto del catálogo'); render(row, input.value); });
                const sheet = row.querySelector('.product-combobox__sheet-search'); sheet.addEventListener('input', () => render(row, sheet.value));
                row.querySelector('.product-combobox__toggle').addEventListener('click', () => render(row));
                [input, sheet].forEach(search => search.addEventListener('keydown', event => {
                    if (event.key === 'Escape') { close(row); input.focus(); }
                    if (event.key === 'Enter') { event.preventDefault(); row.querySelector('.product-combobox__option')?.click(); }
                    if (event.key === 'ArrowDown') { event.preventDefault(); render(row, input.value); row.querySelector('.product-combobox__option')?.focus(); }
                }));
                close(row);
            }
            const normal = cents(select.selectedOptions[0]?.dataset.price);
            const valueInput = row.querySelector('[data-field="discountValue"]');
            const value = cents(valueInput.value);
            const percent = row.querySelector('[data-field="discountMode"]').value === 'PERCENTAGE';
            valueInput.max = percent ? '99.99' : '9999999999.99';
            valueInput.previousElementSibling.textContent = percent ? 'Descuento (%)' : 'Precio de oferta ($)';
            const offer = percent ? (normal * (10000n - value) + 5000n) / 10000n : value;
            row.querySelector('[data-normal]').textContent = money(normal);
            row.querySelector('[data-offer]').textContent = money(offer > 0n ? offer : 0n);
            valueInput.setCustomValidity(select.value && (offer <= 0n || offer >= normal) ? 'La oferta debe ser positiva y menor al precio normal' : '');
            row.querySelector('[data-remove]').disabled = rows.children.length === 1;
        });
        add.disabled = rows.children.length >= 100;
    }
    add.addEventListener('click', () => {
        if (rows.children.length >= 100) return;
        const row = prototype.cloneNode(true); row.querySelectorAll('[data-field]').forEach(input => { input.value = input.dataset.field === 'discountMode' ? 'FIXED_PRICE' : ''; });
        rows.append(row); refresh(); row.querySelector('.product-combobox__input').focus();
    });
    rows.addEventListener('input', refresh);
    rows.addEventListener('change', refresh);
    rows.addEventListener('click', event => { if (event.target.closest('[data-remove]') && rows.children.length > 1) { event.target.closest('.campaign-item').remove(); refresh(); } });
    document.addEventListener('click', event => { if (!event.target.closest('.product-combobox')) [...rows.children].forEach(close); });
    const start = document.getElementById('startDate'), end = document.getElementById('endDate');
    start.addEventListener('change', () => { end.min = start.value; });
    refresh();
})();
