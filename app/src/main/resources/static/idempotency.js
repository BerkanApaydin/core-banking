// --- Idempotency Key Helpers ---
/* exported getIdempotencyKey, markIdempotencyStarted, markIdempotencyFailed, idempotencyErrorMessage, clearIdempotencyKey, clearAllIdempotencyKeys */
// crypto.randomUUID() exists only in secure contexts (HTTPS/localhost).
// Fallback keeps money-movement endpoints working over plain-HTTP LAN URLs:
// output is hex+hyphens (36 chars), satisfying the backend SAFE_KEY charset
// ([A-Za-z0-9\-_.:]+) and the 128-char cap.
function newUuid() {
    try {
        if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
            return crypto.randomUUID();
        }
        if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
            const bytes = crypto.getRandomValues(new Uint8Array(16));
            bytes[6] = (bytes[6] & 0x0f) | 0x40;
            bytes[8] = (bytes[8] & 0x3f) | 0x80;
            const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
            return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
        }
    } catch (e) { /* fall through to Math.random fallback */ }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = Math.floor(Math.random() * 16);
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
    });
}

const IDEMPOTENCY_STORAGE_PREFIX = 'idempotency:v2:';
// The server removes terminal idempotency records after 24 hours. Never
// silently reuse a browser key beyond that window.
const IDEMPOTENCY_KEY_MAX_AGE_MS = 23 * 60 * 60 * 1000;
const idempotencyMemory = new Map();
const idempotencyActive = new Map();

async function requestSignature(payload) {
    if (typeof crypto === 'undefined' || !crypto.subtle || typeof crypto.subtle.digest !== 'function') {
        return null;
    }
    try {
        const bytes = new TextEncoder().encode(JSON.stringify(payload));
        const digest = await crypto.subtle.digest('SHA-256', bytes);
        return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
    } catch (e) {
        return null;
    }
}

function draftStorageKey(keyName, fingerprint) {
    return `${IDEMPOTENCY_STORAGE_PREFIX}draft:${keyName}:${fingerprint}`;
}

function loadIdempotencyDrafts(keyName, persistent) {
    if (!persistent) {
        if (!idempotencyMemory.has(keyName)) idempotencyMemory.set(keyName, new Map());
        const drafts = idempotencyMemory.get(keyName);
        for (const [fingerprint, state] of drafts) {
            if (!state.uncertain && Date.now() - state.createdAt >= IDEMPOTENCY_KEY_MAX_AGE_MS) {
                drafts.delete(fingerprint);
            }
        }
        return drafts;
    }
    // Refresh from storage so another tab's completion cannot leave a stale
    // in-memory key. Separate records preserve an uncertain older draft when
    // the user changes fields and later returns to the original payload.
    const drafts = new Map();
    const prefix = `${IDEMPOTENCY_STORAGE_PREFIX}draft:${keyName}:`;
    const expired = [];
    for (let i = 0; i < localStorage.length; i++) {
        const name = localStorage.key(i);
        if (!name || !name.startsWith(prefix)) continue;
        try {
            const state = JSON.parse(localStorage.getItem(name));
            if (state && state.version === 2 && typeof state.key === 'string'
                && typeof state.fingerprint === 'string') {
                if (!state.uncertain && Date.now() - state.createdAt >= IDEMPOTENCY_KEY_MAX_AGE_MS) {
                    expired.push(name);
                } else {
                    drafts.set(state.fingerprint, state);
                }
            }
        } catch (e) { /* malformed old browser state is ignored */ }
    }
    expired.forEach(name => localStorage.removeItem(name));
    idempotencyMemory.set(keyName, drafts);
    return drafts;
}

function saveIdempotencyState(keyName, state) {
    const drafts = idempotencyMemory.get(keyName);
    drafts.set(state.fingerprint, state);
    if (state.persistent) {
        localStorage.setItem(draftStorageKey(keyName, state.fingerprint), JSON.stringify(state));
    }
}

function syncIdempotencyWarning(keyName) {
    const drafts = idempotencyMemory.get(keyName);
    if (drafts && Array.from(drafts.values()).some(state => state.uncertain)) {
        localStorage.setItem(IDEMPOTENCY_STORAGE_PREFIX + 'uncertain:' + keyName, '1');
    } else {
        localStorage.removeItem(IDEMPOTENCY_STORAGE_PREFIX + 'uncertain:' + keyName);
    }
}

// Persist only a SHA-256 request signature, never raw IBANs or passwords.
// Registration is memory-only so a password-derived digest is not retained.
// On insecure HTTP origins without SubtleCrypto, all drafts are memory-only;
// an uncertainty marker survives reload and forces an explicit new intent.
async function getIdempotencyKey(keyName, payload, persist = true) {
    localStorage.removeItem(keyName); // Discard unbound keys from older frontend versions.
    const signature = await requestSignature(payload);
    const persistent = Boolean(signature && persist);
    const fingerprint = signature || JSON.stringify(payload);
    const drafts = loadIdempotencyDrafts(keyName, persistent);
    const previous = drafts.get(fingerprint);
    const matching = previous && Date.now() - previous.createdAt < IDEMPOTENCY_KEY_MAX_AGE_MS;
    const anyUncertain = Array.from(drafts.values()).some(state => state.uncertain);
    if (matching && previous.uncertain) {
        if (!await confirmDialog(__('general.uncertain_retry'))) return null;
    } else if (anyUncertain) {
        if (!await confirmDialog(__('general.uncertain_changed'))) return null;
    } else if (localStorage.getItem(IDEMPOTENCY_STORAGE_PREFIX + 'uncertain:' + keyName)) {
        if (!await confirmDialog(__('general.uncertain_new'))) return null;
    }

    if (matching) {
        idempotencyActive.set(keyName, fingerprint);
        return previous.key;
    }
    if (previous) {
        drafts.delete(fingerprint);
        if (previous.persistent) localStorage.removeItem(draftStorageKey(keyName, fingerprint));
    }
    const state = { version: 2, key: newUuid(), fingerprint, createdAt: Date.now(), uncertain: false, persistent };
    saveIdempotencyState(keyName, state);
    idempotencyActive.set(keyName, fingerprint);
    syncIdempotencyWarning(keyName);
    return state.key;
}

function markIdempotencyStarted(keyName) {
    const state = idempotencyMemory.get(keyName)?.get(idempotencyActive.get(keyName));
    if (!state) return;
    state.uncertain = true;
    saveIdempotencyState(keyName, state);
    syncIdempotencyWarning(keyName);
}

function isAmbiguousFailure(err) {
    if (!err || err.offline) return false;
    return !err.status || err.status >= 500
        || (err.status === 409 && err.code === 'CONCURRENT_REQUEST');
}

function markIdempotencyFailed(keyName, err) {
    if (isAmbiguousFailure(err)) return;
    const state = idempotencyMemory.get(keyName)?.get(idempotencyActive.get(keyName));
    if (state) {
        state.uncertain = false;
        saveIdempotencyState(keyName, state);
    }
    syncIdempotencyWarning(keyName);
}

function idempotencyErrorMessage(err) {
    return isAmbiguousFailure(err) ? `${err.message} ${__('general.outcome_unknown')}` : err.message;
}

function clearIdempotencyKey(keyName) {
    const fingerprint = idempotencyActive.get(keyName);
    const drafts = idempotencyMemory.get(keyName);
    const state = drafts?.get(fingerprint);
    if (state?.persistent) localStorage.removeItem(draftStorageKey(keyName, fingerprint));
    if (drafts) drafts.delete(fingerprint);
    idempotencyActive.delete(keyName);
    syncIdempotencyWarning(keyName);
}

function clearAllIdempotencyKeys() {
    idempotencyMemory.clear();
    idempotencyActive.clear();
    for (let i = localStorage.length - 1; i >= 0; i--) {
        const name = localStorage.key(i);
        if (name && (name.startsWith(IDEMPOTENCY_STORAGE_PREFIX)
            || name === 'accountKey' || name === 'transferKey' || name === 'registerKey'
            || name.startsWith('cancelKey_'))) {
            localStorage.removeItem(name);
        }
    }
}

