const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const staticDir = path.resolve(__dirname, '../../main/resources/static');
const html = fs.readFileSync(path.join(staticDir, 'index.html'), 'utf8');
const appSource = fs.readFileSync(path.join(staticDir, 'app.js'), 'utf8');

function loadAppContext() {
    const context = vm.createContext({
        localStorage: { getItem: () => 'en' },
        document: {
            querySelectorAll: () => [],
            getElementById: () => null,
            addEventListener() {},
        },
        window: {},
        navigator: {},
    });
    for (const script of ['i18n.js', 'idempotency.js', 'accounts.js', 'transfers.js', 'app.js']) {
        vm.runInContext(fs.readFileSync(path.join(staticDir, script), 'utf8'), context,
            { filename: script });
    }
    return context;
}

test('login body carries the TOTP code only when well-formed', () => {
    const context = loadAppContext();
    // Compare serialized: objects cross the vm boundary with a different
    // prototype, so deepEqual would fail on identity, not structure.
    const body = (u, p, t) => vm.runInContext(
        `JSON.stringify(buildLoginBody(${JSON.stringify(u)}, ${JSON.stringify(p)}, ${JSON.stringify(t)}))`,
        context);
    assert.equal(body('alice', 'pw', '123456'),
        '{"username":"alice","password":"pw","totpCode":"123456"}');
    assert.equal(body('alice', 'pw', '  123456  '),
        '{"username":"alice","password":"pw","totpCode":"123456"}');
    for (const bad of [null, '', '  ', '12345', '123456789', '12ab56', 123456]) {
        assert.equal(body('alice', 'pw', bad), '{"username":"alice","password":"pw"}');
    }
});

test('security section, reset panel and status wiring exist', () => {
    for (const id of ['security-section', 'mfa-status-pill', 'btn-mfa-enable', 'mfa-enroll-box',
        'mfa-secret-text', 'btn-mfa-copy', 'mfa-confirm-form', 'mfa-confirm-code',
        'mfa-disable-form', 'mfa-disable-password', 'mfa-disable-code', 'login-totp',
        'btn-show-reset', 'reset-request-form', 'reset-confirm-form', 'reset-username',
        'reset-token', 'reset-new-password']) {
        assert.ok(html.includes(`id="${id}"`), `index.html must contain #${id}`);
    }
    for (const key of ['nav.security', 'security.title', 'security.subtitle', 'mfa.title',
        'mfa.on', 'mfa.off', 'mfa.enable', 'mfa.confirm', 'mfa.disable', 'mfa.code',
        'reset.request_title', 'reset.confirm_title', 'reset.token', 'auth.forgot', 'auth.totp']) {
        assert.ok(appI18nHas(key), `i18n key missing: ${key}`);
    }
    for (const endpoint of ['/auth/mfa/status', '/auth/mfa/enable', '/auth/mfa/confirm',
        '/auth/mfa/disable', '/auth/password/reset/request', '/auth/password/reset/confirm']) {
        assert.ok(appSource.includes(`'${endpoint}'`), `app.js must call ${endpoint}`);
    }
    assert.ok(appSource.includes("data-target=\"security-section\"") || html.includes('data-target="security-section"'),
        'security tab must be reachable from navigation');
});

function appI18nHas(key) {
    const i18n = fs.readFileSync(path.join(staticDir, 'i18n.js'), 'utf8');
    const en = i18n.split('tr:')[0];
    const tr = i18n.split('tr:')[1] || '';
    return en.includes(`'${key}'`) && tr.includes(`'${key}'`);
}
