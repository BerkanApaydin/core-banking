const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const { webcrypto } = require('node:crypto');

const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/app.js'), 'utf8');
const helpers = source.split('// --- Idempotency Key Helpers ---')[1]
    .split('// --- State Management ---')[0];

function storage() {
    const values = new Map();
    return {
        get length() { return values.size; },
        key(index) { return Array.from(values.keys())[index] ?? null; },
        getItem(key) { return values.has(key) ? values.get(key) : null; },
        setItem(key, value) { values.set(key, String(value)); },
        removeItem(key) { values.delete(key); },
        entries() { return Array.from(values.entries()); }
    };
}

function browser(localStorage, crypto = webcrypto) {
    const answers = [];
    const prompts = [];
    const context = vm.createContext({
        localStorage,
        crypto,
        TextEncoder,
        Uint8Array,
        Date,
        Math,
        confirmDialog: async message => {
            prompts.push(message);
            return answers.shift() ?? false;
        },
        __: key => key
    });
    vm.runInContext(helpers, context);
    return {
        context,
        answers,
        prompts,
        async key(name, payload, persist = true) {
            context.keyName = name;
            context.payload = payload;
            context.persist = persist;
            return vm.runInContext('getIdempotencyKey(keyName, payload, persist)', context);
        },
        run(code) { return vm.runInContext(code, context); }
    };
}

test('same transfer draft reuses the key; a changed payload gets a fresh key', async () => {
    const localStorage = storage();
    const page = browser(localStorage);
    const first = { senderIban: 'TR111', receiverIban: 'TR222', amount: 10, currency: 'TRY' };
    const originalKey = await page.key('transferKey:7', first);
    assert.equal(await page.key('transferKey:7', { ...first }), originalKey);
    const changedKey = await page.key('transferKey:7', { ...first, amount: 20 });
    assert.notEqual(changedKey, originalKey);
    const stored = localStorage.entries().find(([name]) => name.startsWith('idempotency:v2:draft:transferKey:7:'))[1];
    assert.equal(stored.includes('TR111'), false);
    assert.equal(stored.includes('TR222'), false);
    assert.equal(page.prompts.length, 0);
});

test('unknown outcome survives reload and requires consent before exact retry or changed intent', async () => {
    const localStorage = storage();
    const draft = { senderIban: 'TR111', receiverIban: 'TR222', amount: 10, currency: 'TRY' };
    const firstPage = browser(localStorage);
    const originalKey = await firstPage.key('transferKey:7', draft);
    firstPage.run('markIdempotencyStarted("transferKey:7")');
    firstPage.run('markIdempotencyFailed("transferKey:7", new Error("network"))');

    const reloaded = browser(localStorage);
    assert.equal(await reloaded.key('transferKey:7', draft), null);
    reloaded.answers.push(true);
    assert.equal(await reloaded.key('transferKey:7', draft), originalKey);
    reloaded.answers.push(false);
    assert.equal(await reloaded.key('transferKey:7', { ...draft, amount: 20 }), null);
    reloaded.answers.push(true);
    const changedKey = await reloaded.key('transferKey:7', { ...draft, amount: 20 });
    assert.notEqual(changedKey, originalKey);
    reloaded.answers.push(true);
    assert.equal(await reloaded.key('transferKey:7', draft), originalKey);
    assert.deepEqual(reloaded.prompts, [
        'general.uncertain_retry', 'general.uncertain_retry',
        'general.uncertain_changed', 'general.uncertain_changed',
        'general.uncertain_retry'
    ]);
});

test('definitive 4xx result clears uncertainty but retains the draft key', async () => {
    const localStorage = storage();
    const page = browser(localStorage);
    const draft = { amount: 10 };
    const key = await page.key('transferKey:7', draft);
    page.run('markIdempotencyStarted("transferKey:7")');
    page.run('markIdempotencyFailed("transferKey:7", { status: 400 })');
    assert.equal(await page.key('transferKey:7', draft), key);
    assert.equal(localStorage.getItem('idempotency:v2:uncertain:transferKey:7'), null);
    page.run('clearIdempotencyKey("transferKey:7")');
    assert.notEqual(await page.key('transferKey:7', draft), key);
});

test('registration never persists a password fingerprint; insecure origin needs warning after reload', async () => {
    const localStorage = storage();
    const insecureCrypto = { randomUUID: webcrypto.randomUUID.bind(webcrypto) };
    const registration = browser(localStorage, insecureCrypto);
    const draft = { username: 'alice', password: 'Secret123' };
    const oldKey = await registration.key('registerKey', draft, false);
    registration.run('markIdempotencyStarted("registerKey")');
    assert.equal(localStorage.entries().some(([key, value]) => key.includes('registerKey') && value.includes('Secret123')), false);
    assert.equal(localStorage.entries().some(([name]) => name.startsWith('idempotency:v2:draft:registerKey:')), false);

    const reloaded = browser(localStorage, insecureCrypto);
    assert.equal(await reloaded.key('registerKey', draft, false), null);
    reloaded.answers.push(true);
    assert.notEqual(await reloaded.key('registerKey', draft, false), oldKey);
    assert.deepEqual(reloaded.prompts, ['general.uncertain_new', 'general.uncertain_new']);
});

test('expired key is not reused and logout clears all operation keys including cancellation', async () => {
    const localStorage = storage();
    const page = browser(localStorage);
    const original = await page.key('accountKey:7', { iban: 'TR111' });
    const draftName = localStorage.entries().find(([name]) => name.startsWith('idempotency:v2:draft:accountKey:7:'))[0];
    const stored = JSON.parse(localStorage.getItem(draftName));
    stored.createdAt -= 24 * 60 * 60 * 1000;
    localStorage.setItem(draftName, JSON.stringify(stored));
    const reloaded = browser(localStorage);
    assert.notEqual(await reloaded.key('accountKey:7', { iban: 'TR111' }), original);
    await reloaded.key('cancelKey:7:42', { transferId: 42 });
    localStorage.setItem('cancelKey_42', 'legacy');
    reloaded.run('clearAllIdempotencyKeys()');
    assert.equal(localStorage.entries().length, 0);
});

function logoutHarness(responses) {
    const listeners = new Map();
    const elements = new Map();
    const alerts = [];
    const requests = [];
    let cleared = 0;
    let calls = 0;
    const context = vm.createContext({
        API_BASE: '/api/v1',
        authenticated: true,
        browserCsrfToken: () => 'csrf-token',
        AbortController,
        setTimeout,
        clearTimeout,
        console: { error() {} },
        document: {
            getElementById(id) {
                if (!elements.has(id)) {
                    elements.set(id, {
                        disabled: false,
                        addEventListener(event, callback) { listeners.set(`${id}:${event}`, callback); }
                    });
                }
                return elements.get(id);
            }
        },
        fetch: async (url, options) => {
            requests.push({ url, options });
            const result = responses[calls++];
            if (result instanceof Error) throw result;
            return result;
        },
        logout: () => { cleared++; },
        showAlert: (message, type) => alerts.push([message, type]),
        __: key => key
    });
    const authSource = 'function initAuth() {' + source.split('function initAuth() {')[1].split('function logout() {')[0];
    vm.runInContext(authSource, context);
    vm.runInContext('initAuth()', context);
    return {
        click: () => listeners.get('btn-logout:click')(),
        alerts,
        requests,
        get cleared() { return cleared; },
        get calls() { return calls; }
    };
}

test('browser logout retries one lost response with cookie and CSRF header', async () => {
    const page = logoutHarness([new Error('connection reset'), { ok: true, status: 204 }]);
    await page.click();
    assert.equal(page.calls, 2);
    assert.equal(page.cleared, 1);
    assert.equal(page.requests[0].url, '/api/v1/auth/browser/logout');
    assert.equal(page.requests[0].options.credentials, 'same-origin');
    assert.equal(page.requests[0].options.headers['X-CSRF-Token'], 'csrf-token');
    assert.equal('Authorization' in page.requests[0].options.headers, false);
    assert.deepEqual(page.alerts, [['auth.logout.success', 'success']]);
});

test('logout treats server failure as unconfirmed revocation and avoids a repeat request', async () => {
    const page = logoutHarness([{ ok: false, status: 503 }]);
    await page.click();
    assert.equal(page.calls, 1);
    assert.equal(page.cleared, 0);
    assert.deepEqual(page.alerts, [['auth.logout.local_only', 'warning']]);
});
