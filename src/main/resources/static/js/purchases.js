(() => {
    'use strict';
    const form = document.getElementById('purchase-form');
    if (!form) return;
    const items = document.getElementById('purchase-items');
    const add = document.getElementById('add-purchase-item');
    const totalOutput = document.getElementById('purchase-total');
    const prototype = items.firstElementChild.cloneNode(true);
    const money = cents => new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(Number(cents) / 100);

    function subtotal(row) {
        const quantity = row.querySelector('[data-field="quantity"]').value;
        const price = row.querySelector('[data-field="unitPrice"]').value;
        if (!/^\d{1,10}$/.test(quantity) || !/^\d{1,10}(\.\d{1,2})?$/.test(price)) return 0n;
        const [whole, decimal = ''] = price.split('.');
        return BigInt(quantity) * (BigInt(whole) * 100n + BigInt(decimal.padEnd(2, '0')));
    }

    function refresh() {
        let total = 0n;
        const rows = [...items.children];
        rows.forEach((row, index) => {
            row.querySelectorAll('[data-field]').forEach(input => {
                input.name = `items[${index}].${input.dataset.field}`;
                input.id = `purchase-${input.dataset.field}-${index}`;
                input.previousElementSibling.htmlFor = input.id;
            });
            const line = subtotal(row);
            row.querySelector('[data-subtotal]').textContent = money(line);
            row.querySelector('[data-remove]').disabled = rows.length === 1;
            total += line;
        });
        totalOutput.textContent = money(total);
        add.disabled = rows.length >= 100;
        form.dispatchEvent(new Event('purchase:rows-changed'));
    }

    add.addEventListener('click', () => {
        if (items.children.length >= 100) return;
        const row = prototype.cloneNode(true);
        row.querySelectorAll('[data-field]').forEach(input => { input.value = ''; });
        items.append(row);
        refresh();
        row.querySelector('.product-combobox__input').focus();
    });
    form.addEventListener('purchase:add-product', event => {
        const productId = String(event.detail.productId);
        const existing = [...items.children].find(row => row.querySelector('select').value === productId);
        if (existing) {
            const quantity = existing.querySelector('[data-field="quantity"]');
            if (Number(quantity.value) >= 2147483647) return;
            quantity.value = String(Math.max(1, Number(quantity.value) || 1) + 1);
        } else {
            let row = [...items.children].find(row => !row.querySelector('select').value);
            if (!row) {
                if (items.children.length >= 100) return;
                row = prototype.cloneNode(true);
                row.querySelectorAll('[data-field]').forEach(input => { input.value = ''; });
                items.append(row);
            }
            row.querySelector('select').value = productId;
            row.querySelector('[data-field="quantity"]').value = '1';
            row.querySelector('select').dispatchEvent(new Event('change', { bubbles: true }));
        }
        refresh();
        event.detail.added = true;
    });
    items.addEventListener('change', event => {
        if (event.target.dataset.field !== 'productId') return;
        const row = event.target.closest('.purchase-item');
        const selected = event.target.selectedOptions[0];
        row.querySelector('[data-field="unitPrice"]').value = selected?.value ? selected.dataset.cost : '';
        if (!row.querySelector('[data-field="quantity"]').value) row.querySelector('[data-field="quantity"]').value = '1';
        refresh();
    });
    items.addEventListener('input', refresh);
    items.addEventListener('click', event => {
        const remove = event.target.closest('[data-remove]');
        if (!remove || items.children.length <= 1) return;
        remove.closest('.purchase-item').remove();
        refresh();
        add.focus();
    });
    form.addEventListener('submit', () => {
        const submit = form.querySelector('[type="submit"]');
        submit.disabled = true;
        submit.textContent = 'Registrando compra…';
    });
    window.addEventListener('pageshow', () => {
        const submit = form.querySelector('[type="submit"]');
        submit.disabled = !prototype.querySelector('select option[value]:not([value=""])');
        submit.textContent = 'Registrar compra y sumar stock';
    });
    refresh();
})();
