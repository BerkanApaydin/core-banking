const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const storageSource = fs.readFileSync(
    path.resolve(__dirname, '../../main/resources/static/storage.js'), 'utf8');

function nativeStorage(initial = []) {
    const values = new Map(initial);
    return {
        get length() { return values.size; },
        key(index) { return Array.from(values.keys())[index] ?? null; },
        getItem(key) { return values.has(key) ? values.get(key) : null; },
        setItem(key, value) { values.set(key, String(value)); },
        removeItem(key) { values.delete(key); },
        clear() { values.clear(); },
        entries() { return Array.from(values.entries()); }
    };
}

function load(storage) {
    const context = vm.createContext({ localStorage: storage });
    vm.runInContext(storageSource, context);
    return context;
}

test('safeStorage delegates to a working localStorage', () => {
    const localStorage = nativeStorage([['lang', 'tr']]);
    const context = load(localStorage);

    assert.equal(vm.runInContext('safeStorage.getItem("lang")', context), 'tr');
    vm.runInContext('safeStorage.setItem("theme", "light")', context);
    assert.equal(localStorage.getItem('theme'), 'light');
    vm.runInContext('safeStorage.removeItem("theme")', context);
    assert.equal(localStorage.getItem('theme'), null);
});

test('safeStorage never throws when localStorage access is blocked', () => {
    const blocked = new Proxy({}, {
        get() { throw new Error('SecurityError'); }
    });
    const context = load(blocked);

    vm.runInContext('safeStorage.setItem("lang", "tr")', context);
    assert.equal(vm.runInContext('safeStorage.getItem("lang")', context), 'tr');
    assert.equal(vm.runInContext('safeStorage.length', context), 1);
    assert.equal(vm.runInContext('safeStorage.key(0)', context), 'lang');
    vm.runInContext('safeStorage.removeItem("lang")', context);
    assert.equal(vm.runInContext('safeStorage.getItem("lang")', context), null);
});

test('safeStorage hydrates memory when a later write fails', () => {
    const values = new Map([['existing', 'kept']]);
    const flaky = {
        get length() { return values.size; },
        key(index) { return Array.from(values.keys())[index] ?? null; },
        getItem(key) { return values.has(key) ? values.get(key) : null; },
        setItem(key, value) {
            if (key === 'fail') throw new Error('QuotaExceededError');
            values.set(key, String(value));
        },
        removeItem(key) { values.delete(key); },
        clear() { values.clear(); }
    };
    const context = load(flaky);

    assert.equal(vm.runInContext('safeStorage.getItem("existing")', context), 'kept');
    vm.runInContext('safeStorage.setItem("fail", "value")', context);
    assert.equal(vm.runInContext('safeStorage.getItem("existing")', context), 'kept');
    assert.equal(vm.runInContext('safeStorage.getItem("fail")', context), 'value');
    assert.equal(values.has('fail'), false);
});
