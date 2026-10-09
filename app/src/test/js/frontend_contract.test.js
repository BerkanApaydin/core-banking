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
    // Backend rule mirror: start.plusMonths(12) must not be before end.
    const justInside = vm.runInContext("buildReportDateRange('2025-09-24', '2026-09-23')", context);
    assert.equal(justInside.endDate, '2026-09-23T23:59:59.999999');
    assert.throws(() => vm.runInContext("buildReportDateRange('2025-09-24', '2026-09-24')", context),
        /report.range_too_long/);
    assert.throws(() => vm.runInContext("buildReportDateRange('2025-01-01', '2026-06-01')", context),
        /report.range_too_long/);
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

test('transfer cancel posts Idempotency-Key (backend rejects without it)', async () => {
    const calls = [];
    const context = vm.createContext({
        userId: 7,
        cancellationsInFlight: new Set(),
        confirmDialog: async () => true,
        __: key => key,
        getIdempotencyKey: async () => 'cancel-key-1',
        markIdempotencyStarted() {},
        markIdempotencyFailed() {},
        idempotencyErrorMessage: error => error.message,
        clearIdempotencyKey() {},
        fetchApi: async (url, options) => {
            calls.push({ url, options });
            return {};
        },
        showAlert() {},
        loadAccounts: async () => {},
        loadAccountHistory() {}
    });
    const functionSource = 'async function cancelTransfer(transferId, accountId) {'
        + source.split('async function cancelTransfer(transferId, accountId) {')[1]
            .split('// --- Detail surfaces')[0];
    vm.runInContext(functionSource, context);
    await vm.runInContext('cancelTransfer(19, 7)', context);

    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, '/transfers/19/cancel');
    assert.equal(calls[0].options.method, 'POST');
    assert.equal(calls[0].options.headers['Idempotency-Key'], 'cancel-key-1');
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
            page: 0, hasNext: false, loading: false, requestVersion: 0, cursors: [null] },
        window: { __lastReport: null },
        document: { getElementById: id => elements.get(id) },
        __: (key, page) => `${key}:${page}`,
        URLSearchParams,
        PAGE_SIZE: 100,
        fetchApi: async url => {
            calls.push(url);
            return { transfers: [{ id: calls.length }], hasNext: calls.length === 1 };
        },
        renderReportResults: report => rendered.push(report),
        showAlert: error => { throw new Error(error); }
    });
    const functions = 'function reportCursorForPage(cursors, page) {'
        + source.split('function reportCursorForPage(cursors, page) {')[1]
            .split('// --- Authentication Operations ---')[0]
        + 'function invalidateGeneratedReport() {'
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

test('concurrent 401s share one silent refresh (theft-safe coalescing)', async () => {
    const functions = 'let refreshInFlight = null;\nasync function trySilentRefresh() {'
        + source.split('async function trySilentRefresh() {')[1].split('function logout()')[0];
    let fetchCalls = 0;
    const context = vm.createContext({
        API_BASE: '',
        authenticated: true,
        userId: null,
        username: null,
        document: { cookie: 'BANK_CSRF=' + 'x'.repeat(43) },
        getLanguage: () => 'en',
        browserCsrfToken: () => 'x'.repeat(43),
        fetch: async () => {
            fetchCalls++;
            await new Promise(resolve => setTimeout(resolve, 10));
            return { ok: true, json: async () => ({ userId: 7, username: 'alice' }) };
        },
        __: key => key
    });
    vm.runInContext(functions, context);

    const [first, second] = await vm.runInContext(
        'Promise.all([trySilentRefresh(), trySilentRefresh()])', context);
    assert.equal(first, true);
    assert.equal(second, true);
    assert.equal(fetchCalls, 1);
    assert.equal(vm.runInContext('userId', context), 7);
});

test('password gate mirrors backend policy (12–72 + upper/lower/digit)', () => {
    const functions = 'function meetsPasswordPolicy(pw) {'
        + source.split('function meetsPasswordPolicy(pw) {')[1].split('function initUxEnhancements')[0];
    const context = vm.createContext({});
    vm.runInContext(functions, context);

    // Backend PasswordPolicy.DEFAULT is (12, upper, lower, digit) and
    // RegisterWebRequest caps at 72 chars (BCrypt truncation guard); the
    // client gate must reject everything the backend would reject.
    assert.equal(vm.runInContext("meetsPasswordPolicy('Short123456')", context), false);
    assert.equal(vm.runInContext("meetsPasswordPolicy('alllowercase12')", context), false);
    assert.equal(vm.runInContext("meetsPasswordPolicy('ALLUPPERCASE12')", context), false);
    assert.equal(vm.runInContext("meetsPasswordPolicy('NoDigitsHereAA')", context), false);
    assert.equal(vm.runInContext("meetsPasswordPolicy('ValidPass1234')", context), true);
    assert.equal(vm.runInContext("meetsPasswordPolicy('Aa1" + "x".repeat(69) + "')", context), true);
    assert.equal(vm.runInContext("meetsPasswordPolicy('Aa1" + "x".repeat(70) + "')", context), false);
});

test('failed silent refresh keeps the session logged out', async () => {
    const functions = 'let refreshInFlight = null;\nasync function trySilentRefresh() {'
        + source.split('async function trySilentRefresh() {')[1].split('function logout()')[0];
    const context = vm.createContext({
        API_BASE: '',
        authenticated: true,
        userId: 7,
        username: 'alice',
        document: { cookie: '' },
        getLanguage: () => 'en',
        browserCsrfToken: () => null,
        fetch: async () => ({ ok: false }),
        __: key => key
    });
    vm.runInContext(functions, context);

    assert.equal(await vm.runInContext('trySilentRefresh()', context), false);
});

test('report query omits cursor params on page 0 (offset path)', () => {
    const functions = 'function reportCursorForPage(cursors, page) {'
        + source.split('function reportCursorForPage(cursors, page) {')[1]
            .split('// --- Authentication Operations ---')[0];
    const context = vm.createContext({ URLSearchParams });
    vm.runInContext(functions, context);

    const query = vm.runInContext(
        "buildReportQuery({ accountId: 7, startDate: '2026-09-01T00:00:00',"
        + " endDate: '2026-09-30T23:59:59.999999' }, 0, 100, null).toString()",
        context);
    assert.match(query, /accountId=7/);
    assert.match(query, /page=0/);
    assert.match(query, /size=100/);
    assert.doesNotMatch(query, /cursor/);
    assert.doesNotMatch(query, /undefined/);
});

test('report query carries the keyset cursor past page 0 (no OFFSET)', () => {
    const functions = 'function reportCursorForPage(cursors, page) {'
        + source.split('function reportCursorForPage(cursors, page) {')[1]
            .split('// --- Authentication Operations ---')[0];
    const context = vm.createContext({ URLSearchParams });
    vm.runInContext(functions, context);

    const query = vm.runInContext(
        "buildReportQuery({ accountId: 7, startDate: '2026-09-01T00:00:00',"
        + " endDate: '2026-09-30T23:59:59.999999' }, 3, 100,"
        + " { createdAt: '2026-09-10T12:00:00', id: 42 }).toString()",
        context);
    assert.match(query, /cursorCreatedAt=2026-09-10T12/);
    assert.match(query, /cursorId=42/);
});

test('report cursor stack pushes forward and pops back', () => {
    const functions = 'function reportCursorForPage(cursors, page) {'
        + source.split('function reportCursorForPage(cursors, page) {')[1]
            .split('// --- Authentication Operations ---')[0];
    const context = vm.createContext({});
    vm.runInContext(functions, context);

    assert.equal(vm.runInContext('reportCursorForPage([null], 0)', context), null);
    assert.equal(vm.runInContext('reportCursorForPage(null, 2)', context), null);
    // JSON-serialized comparison: vm-realm objects carry a different
    // prototype than host objects, so deepEqual would reject them.
    assert.equal(vm.runInContext(
        "JSON.stringify(storeReportCursor([null], 0,"
        + " { hasNext: true, nextCursorCreatedAt: '2026-09-10T12:00:00', nextCursorId: 42 }))",
        context),
        '[null,{"createdAt":"2026-09-10T12:00:00","id":42}]');
    // Terminal page (hasNext=false) stores null: going forward again stays
    // on the offset path instead of sending a stale cursor.
    assert.equal(vm.runInContext(
        "JSON.stringify(storeReportCursor([null], 0, { hasNext: false }))", context),
        '[null,null]');
});

test('etag cache stores validators, evicts oldest-first, clears on demand', () => {
    const functions = 'const etagCache = new Map();'
        + source.split('const etagCache = new Map();')[1].split('function isOffline()')[0];
    const context = vm.createContext({ Map });
    vm.runInContext(functions, context);

    assert.equal(vm.runInContext("etagCacheLookup('GET /x')", context), null);
    vm.runInContext("etagCacheSave('GET /x', 'W/abc', { v: 1 })", context);
    assert.equal(vm.runInContext("JSON.stringify(etagCacheLookup('GET /x'))", context),
        '{"etag":"W/abc","body":{"v":1}}');
    // Empty validator never cached.
    vm.runInContext("etagCacheSave('GET /y', null, { v: 2 })", context);
    assert.equal(vm.runInContext("etagCacheLookup('GET /y')", context), null);
    // Bounded at 30: the oldest entry is evicted first.
    vm.runInContext('for (let i = 0; i < 35; i++) etagCacheSave(`GET /k${i}`, `W/"${i}"`, i)', context);
    assert.equal(vm.runInContext('etagCache.size', context), 30);
    assert.equal(vm.runInContext("etagCacheLookup('GET /k0')", context), null);
    assert.equal(vm.runInContext("etagCacheLookup('GET /k34').body", context), 34);
    vm.runInContext('etagCacheInvalidate()', context);
    assert.equal(vm.runInContext('etagCache.size', context), 0);
});
