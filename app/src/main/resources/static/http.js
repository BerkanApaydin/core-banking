/* exported PAGE_SIZE, fetchApi, restoreBrowserSession */
// --- API Helpers ---
// Matches backend TRANSACTION_TIMEOUT_SECONDS=30: never wait forever.
const REQUEST_TIMEOUT_MS = 30000;
const PAGE_SIZE = 100; // backend @Max(100) on page size

// --- Conditional-GET (ETag) cache ---
// The server renders ETag validators on GET /transfers/report,
// GET /transfers/{id} and GET /accounts/{id}. Opt-in per call
// ({ etagCache: true }): the stored validator is sent as If-None-Match and
// an unchanged re-read comes back 304 with no body — no server re-hash, no
// re-render here. Bounded, and cleared by any successful mutation below, so
// post-transfer balances or statuses can never go stale.
const etagCache = new Map();
const ETAG_CACHE_LIMIT = 30;

function etagCacheKey(method, endpoint) {
    return `${method} ${endpoint}`;
}

function etagCacheLookup(key) {
    return etagCache.get(key) || null;
}

function etagCacheSave(key, etag, body) {
    if (!etag) return;
    if (!etagCache.has(key) && etagCache.size >= ETAG_CACHE_LIMIT) {
        etagCache.delete(etagCache.keys().next().value);
    }
    etagCache.set(key, { etag, body });
}

function etagCacheInvalidate() {
    etagCache.clear();
}

function isOffline() {
    return (typeof navigator !== 'undefined' && 'onLine' in navigator && !navigator.onLine);
}

async function fetchApi(endpoint, options = {}) {
    if (isOffline()) {
        const offErr = new Error(__('general.offline'));
        offErr.offline = true;
        throw offErr;
    }
    const controller = (typeof AbortController !== 'undefined') ? new AbortController() : null;
    const timeoutId = controller ? setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS) : null;
    try {
        const headers = {
            'Content-Type': 'application/json',
            ...options.headers
        };
        const method = (options.method || 'GET').toUpperCase();
        const useEtagCache = options.etagCache === true && (method === 'GET' || method === 'HEAD');
        const cacheKey = useEtagCache ? etagCacheKey(method, endpoint) : null;
        const cached = cacheKey ? etagCacheLookup(cacheKey) : null;
        if (cached) headers['If-None-Match'] = cached.etag;
        if (options.method && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
            const csrf = browserCsrfToken();
            if (csrf) headers['X-CSRF-Token'] = csrf;
        }
        headers['Accept-Language'] = getLanguage();
        const response = await fetch(`${API_BASE}${endpoint}`, {
            ...options,
            headers,
            credentials: 'same-origin',
            ...(controller ? { signal: controller.signal } : {})
        });

        if (response.status === 304 && cacheKey) {
            const hit = etagCacheLookup(cacheKey);
            if (hit) return hit.body;
            // Entry evicted between request and response: retry once without
            // the validator instead of surfacing an empty body as an error.
            const retry = { ...options };
            delete retry.etagCache;
            return fetchApi(endpoint, retry);
        }

        if (response.status === 401) {
            // Public endpoints (login/register) return 401 for bad credentials:
            // surface the server message. Only an authenticated session expiry
            // clears local state.
            const text = await response.text();
            let data = null;
            if (text) {
                try { data = JSON.parse(text); } catch (e) { data = { message: text }; }
            }
            if (authenticated) {
                // One silent rotation before giving up: short-lived access
                // tokens expire every 15 minutes by design.
                if (!options._refreshed && await trySilentRefresh()) {
                    return fetchApi(endpoint, { ...options, _refreshed: true });
                }
                logout();
                const expired = new Error(__('auth.session_expired'));
                expired.status = 401;
                throw expired;
            }
            throw new Error(extractErrorMessage(data) || __('auth.login.failed'));
        }

        const text = await response.text();
        let data = null;
        if (text) {
            try {
                data = JSON.parse(text);
            } catch (e) {
                data = { message: text };
            }
        }

        if (!response.ok) {
            // Backend envelope is RFC 9457 ProblemDetail ({message, detail,
            // errors{field: msg}}). Prefer the resolved message, then detail,
            // then joined field errors.
            const err = new Error(extractErrorMessage(data) || __('general.error'));
            err.status = response.status;
            err.code = data && typeof data.code === 'string' ? data.code : null;
            // 429 carries RFC 9110 Retry-After (seconds, = rate-limit window).
            // Never auto-retry it: the sliding window would just reject again.
            if (response.status === 429) {
                let waitSec = 0;
                try {
                    waitSec = parseInt(response.headers.get('Retry-After'), 10) || 0;
                } catch (e) { /* header unreadable: fall through */ }
                err.retryAfter = waitSec;
                if (waitSec > 0) {
                    err.message = `${err.message} ${__('general.retry_in', waitSec)}`;
                }
            }
            throw err;
        }

        if (cacheKey) {
            const respEtag = response.headers.get('ETag');
            if (respEtag) etagCacheSave(cacheKey, respEtag, data);
        } else if (method !== 'GET' && method !== 'HEAD') {
            // Any successful write may have moved balances, statuses or report
            // rows: drop every cached read rather than risk a stale modal.
            etagCacheInvalidate();
        }

        return data;
    } catch (error) {
        if (error && (error.name === 'AbortError' || error.code === 20)) {
            // Aborting the browser request does not roll back a server commit.
            const timedOut = new Error(__('general.timeout'));
            timedOut.timeout = true;
            throw timedOut;
        }
        console.error('API Error:', error);
        throw error;
    } finally {
        if (timeoutId) clearTimeout(timeoutId);
    }
}

function browserCsrfToken() {
    const match = document.cookie.match(/(?:^|; )(?:(?:__Host-)?BANK_CSRF)=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
}

// Single in-flight rotation shared by concurrent 401s: the backend treats a
// second presentation of the same refresh token as theft (whole family
// revoked), so parallel refresh calls must be coalesced client-side.
// `navigator.locks` extends that guarantee across same-origin tabs: without
// it two tabs can present the same cookie before either response rotates it.
let refreshInFlight = null;
const REFRESH_LOCK_NAME = 'bank-auth-refresh';

function refreshOnce() {
    if (refreshInFlight) return refreshInFlight;
    const pending = performSilentRefresh().finally(() => {
        if (refreshInFlight === pending) refreshInFlight = null;
    });
    refreshInFlight = pending;
    return pending;
}

async function performSilentRefresh() {
    const csrf = browserCsrfToken();
    const headers = { 'Accept-Language': getLanguage() };
    if (csrf) headers['X-CSRF-Token'] = csrf;
    const response = await fetch(`${API_BASE}/auth/browser/refresh`, {
        method: 'POST',
        headers,
        credentials: 'same-origin'
    });
    if (!response.ok) throw new Error('refresh failed');
    let result = null;
    try { result = await response.json(); } catch (e) { /* empty body: still rotated */ }
    if (result && result.userId) {
        userId = result.userId;
        username = result.username;
    }
    authenticated = true;
    return true;
}

async function trySilentRefresh() {
    if (refreshInFlight) {
        try {
            await refreshInFlight;
            return authenticated;
        } catch (e) { return false; }
    }
    if (typeof navigator !== 'undefined' && navigator.locks
            && typeof navigator.locks.request === 'function') {
        try {
            return await navigator.locks.request(
                REFRESH_LOCK_NAME, { mode: 'exclusive' }, refreshOnce);
        } catch (e) {
            return false;
        }
    }
    try {
        return await refreshOnce();
    } catch (e) {
        return false;
    }
}

async function restoreBrowserSession() {
    try {
        let response = await fetch(`${API_BASE}/auth/browser/session`, {
            credentials: 'same-origin', headers: { 'Accept-Language': getLanguage() }
        });
        if (!response.ok) {
            // The short-lived access cookie (15m) may have expired while the
            // refresh cookie (7d) is still valid: one silent rotation before
            // falling back to the login screen.
            if (response.status !== 401 || !(await trySilentRefresh())) return;
            response = await fetch(`${API_BASE}/auth/browser/session`, {
                credentials: 'same-origin', headers: { 'Accept-Language': getLanguage() }
            });
            if (!response.ok) return;
        }
        const result = await response.json();
        authenticated = true;
        userId = result.userId;
        username = result.username;
        checkAuthStatus();
    } catch (e) {
        // A network error does not create a local authenticated state.
    }
}

function extractErrorMessage(data) {
    if (!data || typeof data !== 'object') {
        return (typeof data === 'string' && data) ? data : '';
    }
    // Field errors first: validation 400s carry a generic message
    // ("Validation failed") with the actionable per-field text here.
    if (data.errors && typeof data.errors === 'object') {
        const parts = Object.entries(data.errors).map(([f, m]) => `${f}: ${m}`);
        if (parts.length > 0) return parts.join(' · ');
    }
    if (data.message) return data.message;
    if (data.detail) return data.detail;
    if (data.title && data.status) return `${data.title} (${data.status})`;
    return '';
}

