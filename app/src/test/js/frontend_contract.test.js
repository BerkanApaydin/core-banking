const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/app.js'), 'utf8');
const accountSource = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/accounts.js'), 'utf8');

test('browser loads feature scripts in dependency order', () => {
    const staticDirectory = path.resolve(__dirname, '../../main/resources/static');
    const html = fs.readFileSync(path.join(staticDirectory, 'index.html'), 'utf8');
    const scripts = Array.from(html.matchAll(/<script src="([^"]+)"/g), match => match[1].split('?')[0]);
    assert.deepEqual(scripts, ['boot.js', 'i18n.js', 'idempotency.js', 'accounts.js', 'transfers.js', 'app.js']);
    for (const script of scripts) {
        assert.equal(fs.existsSync(path.join(staticDirectory, script)), true, `${script} must be served`);
    }

    const context = vm.createContext({
        localStorage: { getItem: () => 'tr' },
        document: { querySelectorAll: () => [], getElementById: () => null, addEventListener() {} },
        window: {}
    });
    for (const script of scripts.slice(1)) {
        vm.runInContext(fs.readFileSync(path.join(staticDirectory, script), 'utf8'), context,
            { filename: script });
    }
    assert.equal(vm.runInContext('getLanguage()', context), 'tr');
    assert.notEqual(vm.runInContext("__('app.title')", context), 'app.title');
    assert.equal(vm.runInContext('typeof getIdempotencyKey', context), 'function');
    assert.equal(vm.runInContext('typeof loadAccounts', context), 'function');
    assert.equal(vm.runInContext('typeof populateTransferDropdowns', context), 'function');
});

test('recipient IBAN validation matches backend MOD 97 rule', () => {
    const functions = 'function isValidIban(value) {'
        + source.split('function isValidIban(value) {')[1].split('function formatIbanDisplay')[0];
    const context = vm.createContext({});
    vm.runInContext(functions, context);

    assert.equal(vm.runInContext("hasValidIbanChecksum('TR400006200000000000000001')", context), true);
    assert.equal(vm.runInContext("hasValidIbanChecksum('TR410006200000000000000001')", context), false);
    assert.equal(vm.runInContext("hasValidIbanChecksum('TR123')", context), false);
});

test('report date selection covers both complete calendar days', () => {
    const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/index.html'), 'utf8');
    assert.match(html, /type="date"[^>]*id="report-start-date"/);
    assert.match(html, /type="date"[^>]*id="report-end-date"/);

    const functions = 'function formatDateInput(date) {'
        + source.split('function formatDateInput(date) {')[1].split('// --- Authentication Operations ---')[0];
    const context = vm.createContext({ __: key => key });
    vm.runInContext(functions, context);

    assert.equal(vm.runInContext('formatDateInput(new Date(2026, 8, 24, 23, 59))', context), '2026-09-24');
    const range = vm.runInContext("buildReportDateRange('2026-09-24', '2026-09-24')", context);
    assert.equal(range.startDate, '2026-09-24T00:00:00');
    assert.equal(range.endDate, '2026-09-24T23:59:59.999999');
    assert.throws(() => vm.runInContext("buildReportDateRange('2026-09-25', '2026-09-24')", context),
        /report.invalid_date_range/);
});

test('account creation sends no IBAN and displays the server-generated value', async () => {
    const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/index.html'), 'utf8');
    assert.equal(html.includes('id="acc-iban"'), false);
    const listeners = new Map();
    const calls = [];
    const alerts = [];
    const classes = { add() {}, remove() {} };
    const elements = {
        'create-account-modal': { classList: classes, addEventListener() {} },
        'open-create-modal': { addEventListener() {}, focus() {} },
        'close-create-modal': { addEventListener() {} },
        'btn-cancel-create': { addEventListener() {} },
        'create-account-form': { addEventListener(type, handler) { listeners.set(type, handler); }, reset() {} },
        'btn-account-spinner': { classList: classes },
        'btn-submit-account': { disabled: false, setAttribute() {}, removeAttribute() {} },
        'acc-account-name': { value: 'Günlük Harcamalar' },
        'acc-balance': { value: '25.00' },
        'acc-currency': { value: 'TRY' }
    };
    const context = vm.createContext({
        document: { getElementById: id => elements[id], addEventListener() {}, removeEventListener() {} },
        fundingMode: 'enabled', userId: 7,
        fetchApi: async (url, options) => {
            calls.push({ url, options });
            return { iban: 'TR440006200000000000000123' };
        },
        getIdempotencyKey: async () => 'account-key',
        markIdempotencyStarted() {}, clearIdempotencyKey() {}, loadAccounts() {},
        markIdempotencyFailed() {}, idempotencyErrorMessage: e => e.message,
        renderFundingMode() {}, showAlert: message => alerts.push(message),
        __: (key, ...args) => `${key}:${args.join(',')}`
    });
    const functionSource = 'function initModal() {'
        + accountSource.split('function initModal() {')[1];
    vm.runInContext(functionSource, context);
    vm.runInContext('initModal()', context);
    await listeners.get('submit')({ preventDefault() {} });

    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, '/accounts');
    assert.equal(Object.hasOwn(JSON.parse(calls[0].options.body), 'iban'), false);
    assert.equal(JSON.parse(calls[0].options.body).ownerName, 'Günlük Harcamalar');
    assert.equal(alerts[0], 'account.created:TR440006200000000000000123');
});

test('history cancel action uses a click listener accepted by the CSP', async () => {
    const items = [];
    const calls = [];
    const errors = [];
    const historyList = { innerHTML: '', appendChild(item) { items.push(item); } };
    const context = vm.createContext({
        accounts: [{ id: 7, iban: 'TR400006200000000000000001' }],
        historyPager: { accountId: null, page: 0, last: true, total: 0 },
        historyCache: { accountId: null, items: [] },
        PAGE_SIZE: 100,
        document: {
            getElementById(id) {
                return id === 'transaction-history-list' ? historyList : { textContent: '' };
            },
            createElement() {
                const listeners = new Map();
                const button = { addEventListener(type, handler) { listeners.set(type, handler); }, listeners };
                return {
                    innerHTML: '',
                    addEventListener(type, handler) { listeners.set(`item:${type}`, handler); },
                    querySelector(selector) {
                        return selector === '.btn-cancel-transfer'
                            && this.innerHTML.includes('class="btn-cancel-transfer"') ? button : null;
                    },
                    button,
                    listeners
                };
            }
        },
        fetchApi: async url => {
            assert.equal(url, '/transfers/history/7?page=0&size=100');
            return {
                content: [{ id: 19, senderIban: 'TR400006200000000000000001',
                    receiverIban: 'TR830006200000000000000003', status: 'COMPLETED',
                    createdAt: '2026-09-20T12:00:00', amount: 5, currency: 'TRY' }],
                last: true, totalElements: 1
            };
        },
        transferStatusMeta: () => ({ cls: 'badge-active', label: 'Completed' }),
        __: key => key,
        escapeHtml: value => value,
        formatIbanDisplay: value => value,
        formatDate: value => value,
        formatMoney: value => value,
        cancelTransfer: (...args) => calls.push(args),
        openTransferDetail: () => { throw new Error('row click must not run'); },
        showAlert: error => errors.push(error)
    });
    const historyFunction = 'async function loadAccountHistory(accountId, loadMore = false) {'
        + source.split('async function loadAccountHistory(accountId, loadMore = false) {')[1]
            .split('async function cancelTransfer')[0];
    vm.runInContext(historyFunction, context);
    await vm.runInContext('loadAccountHistory(7)', context);

    assert.equal(errors.length, 0);
    assert.equal(items.length, 1);
    assert.equal(items[0].innerHTML.includes('onclick='), false);
    let stopped = false;
    items[0].button.listeners.get('click')({ stopPropagation() { stopped = true; } });
    assert.equal(stopped, true);
    assert.deepEqual(calls, [[19, 7]]);
});

test('report pagination exposes every page and invalidation prevents printing stale data', async () => {
    const calls = [];
    const rendered = [];
    const elements = new Map();
    for (const id of ['report-pagination', 'report-scope', 'report-page-previous', 'report-page-next',
            'report-page-label', 'report-results', 'btn-print-report', 'generator-tab']) {
        const classes = new Set(id === 'generator-tab' ? ['active'] : []);
        elements.set(id, {
            disabled: false,
            textContent: '',
            classList: {
                add(name) { classes.add(name); },
                toggle(name, enabled) { if (enabled) classes.add(name); else classes.delete(name); },
                contains(name) { return classes.has(name); }
            }
        });
    }
    const context = vm.createContext({
        reportPager: { criteria: { accountId: '7', startDate: '2026-09-01T00:00', endDate: '2026-09-02T00:00' },
            page: 0, hasNext: false, loading: false, requestVersion: 0 },
        window: { __lastReport: null },
        document: { getElementById: id => elements.get(id) },
        __: (key, page) => `${key}:${page}`,
        URLSearchParams,
        fetchApi: async url => {
            calls.push(url);
            return { transfers: [{ id: calls.length }], hasNext: calls.length === 1 };
        },
        renderReportResults: report => rendered.push(report),
        showAlert: error => { throw new Error(error); }
    });
    const functions = 'function invalidateGeneratedReport() {'
        + source.split('function invalidateGeneratedReport() {')[1].split('async function loadAccountHistory')[0];
    vm.runInContext(functions, context);

    await vm.runInContext('loadReportPage(0)', context);
    assert.match(calls[0], /page=0&size=100$/);
    assert.equal(elements.get('report-page-next').disabled, false);
    assert.equal(elements.get('btn-print-report').disabled, false);
    await vm.runInContext('loadReportPage(1)', context);
    assert.match(calls[1], /page=1&size=100$/);
    assert.equal(elements.get('report-page-next').disabled, true);
    assert.equal(elements.get('report-page-previous').disabled, false);
    assert.equal(elements.get('report-page-label').textContent, 'report.page:2');
    assert.equal(rendered.length, 2);

    vm.runInContext('invalidateGeneratedReport()', context);
    assert.equal(elements.get('btn-print-report').disabled, true);
    assert.equal(elements.get('report-results').classList.contains('d-none'), true);
});

test('opening-balance field follows the authenticated backend capability', async () => {
    const balance = { readOnly: true, value: '0.00' };
    const hint = { textContent: '' };
    let enabled = true;
    const context = vm.createContext({
        fundingMode: 'unknown', authenticated: true,
        document: { getElementById: id => id === 'acc-balance' ? balance : hint },
        __: key => key,
        fetchApi: async url => {
            assert.equal(url, '/accounts/capabilities');
            return { initialFundingEnabled: enabled };
        }
    });
    const functions = 'function renderFundingMode() {'
        + accountSource.split('function renderFundingMode() {')[1].split('function initModal()')[0];
    vm.runInContext(functions, context);

    await vm.runInContext('loadAccountCapabilities()', context);
    assert.equal(balance.readOnly, false);
    assert.equal(hint.textContent, 'account.funding_enabled');

    enabled = false;
    balance.value = '500.00';
    await vm.runInContext('loadAccountCapabilities()', context);
    assert.equal(balance.readOnly, true);
    assert.equal(balance.value, '0.00');
    assert.equal(hint.textContent, 'account.funding_disabled');
});
