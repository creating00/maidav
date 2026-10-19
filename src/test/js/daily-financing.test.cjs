const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

const templates = resolve(__dirname, '../../main/resources/templates/pages');
const products = readFileSync(resolve(templates, 'products/index.html'), 'utf8');
const sales = readFileSync(resolve(templates, 'sales/form.html'), 'utf8');

test('product and sale inline scripts have valid JavaScript syntax', () => {
    for (const html of [products, sales]) {
        for (const [, script] of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)) {
            new vm.Script(script);
        }
    }
});

// Exercise the actual inline functions without starting Spring or connecting to a database.
function loadFunctions(source, names, globals = {}) {
    const context = vm.createContext(globals);
    for (const name of names) {
        const pattern = new RegExp(`            function ${name}\\([^]*?\\n            \\}`);
        const match = source.match(pattern);
        assert.ok(match, `Missing template function: ${name}`);
        vm.runInContext(match[0], context);
    }
    return context;
}

const calculatorFunctions = ['parseNumber', 'roundTo50', 'money', 'getCalcConfig', 'isMonthlyLongEnabled', 'calculatePlans'];

test('product copy and WhatsApp text offer only the regular daily fee', () => {
    const ctx = loadFunctions(products, [...calculatorFunctions, 'moneyCompact', 'buildShareText'], { calcConfigEl: null });
    const plans = ctx.calculatePlans(80000, 80000);
    for (const client of [null, { name: 'Cliente de prueba' }]) {
        const text = ctx.buildShareText({ code: 'PRD-TEST', description: 'Ollas' }, plans, client);
        const lines = text.split('\n');
        assert.equal(lines.find(line => line.includes('Diaria')), '🗓 Diaria (144 dias): $1.400');
        assert.match(lines.find(line => line.includes('Semanal')), /\(Efe: \$/);
        assert.match(lines.find(line => line.includes('Mensual 4')), /\(Efe: \$/);
        assert.match(lines.find(line => line.includes('Mensual 8')), /\(Efe: \$/);
        assert.ok(text.includes('Efectivo: $104.000'));
    }
    assert.equal(plans.plans.DAILY.cashFee, null);
});

test('product financing panel keeps daily visible below the eight-month threshold', () => {
    const cells = new Map();
    let monthlyHidden;
    const panel = {
        dataset: { base: '80000', cost: '10000', code: 'PRD-TEST', description: 'Ollas' },
        querySelector(selector) {
            if (selector === '.monthly-long-plan-row') {
                return { classList: { toggle: (_, hidden) => { monthlyHidden = hidden; } } };
            }
            if (!cells.has(selector)) cells.set(selector, {});
            return cells.get(selector);
        }
    };
    const ctx = loadFunctions(products, [...calculatorFunctions, 'moneyCompact', 'buildShareText',
        'normalizeLineBreaks', 'setPanelText', 'renderPanel'], {
        calcConfigEl: { dataset: { monthlyLongMinCost: '15000' } }
    });
    ctx.renderPanel(panel);
    assert.equal(cells.get('.js-dia-ef').textContent, 'No aplica');
    assert.equal(cells.get('.js-dia').textContent, '$1400.00');
    assert.equal(monthlyHidden, true);
    assert.ok(!panel.dataset.whatsappText.includes('Mensual 8'));
    assert.ok(panel.dataset.whatsappText.includes('Diaria'));
    const dailyCard = products.match(/<article[^>]*>\s*<div[^>]*>\s*<p id="scanProductDailyLabel"[^]*?<\/article>/)[0];
    assert.ok(!dailyCard.includes('efectivo'));
    assert.ok(!dailyCard.includes('scanProductMonthlyLongPlan'));
});

test('sales calculator preserves weekly and monthly cash prices but has no daily cash price', () => {
    const cells = new Map();
    const ctx = loadFunctions(sales, [...calculatorFunctions, 'setText', 'updateCalculator'], {
        calcConfigEl: null,
        getCostCartTotal: () => 80000,
        getBaseCartTotal: () => 80000,
        document: { getElementById(id) {
            if (id === 'calcMonthlyLongRow') return null;
            if (!cells.has(id)) cells.set(id, {});
            return cells.get(id);
        } }
    });
    ctx.updateCalculator();
    assert.equal(cells.get('calcDiaEf').textContent, 'No aplica');
    assert.equal(cells.get('calcDia').textContent, '$1400.00');
    assert.equal(ctx.calculatePlans(80000).plans.DAILY.cashFee, null);
    for (const id of ['calcSemEf', 'calcMes4Ef', 'calcMes8Ef']) {
        assert.match(cells.get(id).textContent, /^\$[1-9]/);
    }
});

test('manual daily editor omits cash fields and summary, including restored legacy cash values', () => {
    const rows = [];
    let cashHeaderHidden;
    const inputs = name => rows.flatMap(row => [...row.innerHTML.matchAll(
        new RegExp(`name="${name}"[^]*?value="([^"]*)"`, 'g')
    )].map(match => ({ value: match[1], addEventListener() {} })));
    const ctx = loadFunctions(sales, ['parseNumber', 'money', 'buildManualInstallmentRows', 'updateManualInstallmentsSummary'], {
        manualInstallmentsBody: { appendChild: row => rows.push(row) },
        weeksCountInput: { value: '2' },
        firstDueDateInput: { value: '2026-10-20' },
        paymentFrequency: { value: 'DAILY' },
        manualInstallmentsSummary: { classList: { toggle() {} } },
        appliedPricingPreset: null,
        isManualInstallmentsEnabled: () => true,
        getManualInstallmentInputs: () => inputs('manualInstallmentAmounts'),
        getManualCashInstallmentInputs: () => inputs('manualCashInstallmentAmounts'),
        syncCartTotalFromManualInstallments() {}, saveDraft() {},
        toDateOnly: value => value, toIsoDate: value => value, formatDate: value => value,
        nextManualDueDate: value => value,
        document: {
            createElement: () => ({}),
            getElementById: id => id === 'totalAmount' ? { textContent: '3000' }
                : { classList: { toggle: (_, hidden) => { cashHeaderHidden = hidden; } } }
        }
    });
    ctx.buildManualInstallmentRows({ values: [1500, 1500], cashValues: [1200, 1200] });
    assert.equal(inputs('manualInstallmentAmounts').length, 2);
    assert.equal(inputs('manualCashInstallmentAmounts').length, 0);
    assert.equal(cashHeaderHidden, true);
    assert.equal(ctx.manualInstallmentsSummary.textContent, 'Total financiado: $3000.00');

    rows.length = 0;
    ctx.paymentFrequency.value = 'WEEKLY';
    ctx.buildManualInstallmentRows({ values: [1500, 1500], cashValues: [1200, 1200] });
    assert.equal(inputs('manualCashInstallmentAmounts').length, 2);
    assert.equal(cashHeaderHidden, false);
    assert.equal(ctx.manualInstallmentsSummary.textContent, 'Total financiado: $3000.00 | Total efectivo: $2400.00');
});
