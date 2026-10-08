// --- Configuration ---
const API_BASE = '/api/v1';

// --- Theme (dark default, preference persisted) ---
function getPreferredTheme() {
    const saved = localStorage.getItem('theme');
    if (saved === 'light' || saved === 'dark') {
        return saved;
    }
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches) {
        return 'light';
    }
    return 'dark';
}

function updateThemeButtons() {
    const nextIsLight = document.documentElement.getAttribute('data-theme') !== 'light';
    document.querySelectorAll('[data-theme-toggle]').forEach(btn => {
        btn.setAttribute('aria-label', nextIsLight ? __('theme.switch_to_light') : __('theme.switch_to_dark'));
    });
}

function setTheme(theme) {
    if (theme === 'light') {
        document.documentElement.setAttribute('data-theme', 'light');
    } else {
        document.documentElement.removeAttribute('data-theme');
    }
    localStorage.setItem('theme', theme);
    const meta = document.getElementById('meta-theme-color');
    if (meta) {
        meta.setAttribute('content', theme === 'light' ? '#EDF0F6' : '#060F22');
    }
    updateThemeButtons();
}

function initTheme() {
    setTheme(getPreferredTheme());
    document.querySelectorAll('[data-theme-toggle]').forEach(btn => {
        btn.addEventListener('click', () => {
            const isLight = document.documentElement.getAttribute('data-theme') === 'light';
            setTheme(isLight ? 'dark' : 'light');
        });
    });
    document.addEventListener('languagechange', updateThemeButtons);
}

// --- State Management ---
let accounts = [];
let activeTab = 'accounts-section';
let authenticated = false;
let userId = null;
let username = null;
let fundingMode = 'unknown';
// Paged-list state: backend caps size at 100, so long lists need "load more".
// Filters/sorts apply to the items loaded so far.
let accountPager = { page: 0, last: true, total: 0 };
let historyCache = { accountId: null, items: [] };
let historyPager = { accountId: null, page: 0, last: true, total: 0 };
let reportPager = { criteria: null, page: 0, hasNext: false, loading: false, requestVersion: 0 };
const cancellationsInFlight = new Set();

// --- DOM Elements ---
const navItems = document.querySelectorAll('.nav-item');
const tabContents = document.querySelectorAll('.tab-content');
const alertContainer = document.getElementById('alert-container');

// --- Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    const savedLang = localStorage.getItem('lang');
    if (savedLang === 'tr' || savedLang === 'en') {
        currentLang = savedLang;
    }
    document.documentElement.lang = currentLang;
    translateStaticPage();
    initLanguageSwitcher();
    initNavigation();
    initModal();
    initTransferForm();
    initReportSection();
    initAuth();
    initDetailModals();
    initUxEnhancements();
    checkAuthStatus();
    restoreBrowserSession();
});

function initLanguageSwitcher() {
    // All switchers (auth screen + app header) stay in sync via the
    // global `languagechange` event dispatched by setLanguage().
    document.querySelectorAll('[data-lang-switcher]').forEach(sw => {
        sw.value = currentLang;
        sw.addEventListener('change', (e) => {
            setLanguage(e.target.value);
        });
    });
    document.addEventListener('languagechange', () => {
        document.querySelectorAll('[data-lang-switcher]').forEach(sw => {
            sw.value = currentLang;
        });
    });
}

// Re-render every dynamically built string on language change so the whole
// UI (not just static labels) follows the new locale.
document.addEventListener('languagechange', () => {
    renderFundingMode();
    if (!authenticated) return;
    renderPortfolioSummary();
    loadAccounts();
    populateTransferDropdowns();
    populateReportDropdown();
    const resultsEl = document.getElementById('report-results');
    if (window.__lastReport && resultsEl && !resultsEl.classList.contains('d-none')) {
        renderReportResults(window.__lastReport);
    }
    updateReportPagination();
    // Re-run transfer summary + sender-limit lines in the new language.
    document.getElementById('transfer-amount')?.dispatchEvent(new Event('input', { bubbles: true }));
    document.getElementById('sender-account-select')?.dispatchEvent(new Event('change', { bubbles: true }));
    // Re-render open detail surfaces in the new language.
    if (document.getElementById('account-detail-modal')?.classList.contains('active') && detailAccountId !== null) {
        openAccountDetail(detailAccountId);
    }
    if (document.getElementById('transfer-detail-modal')?.classList.contains('active') && detailTransferId !== null) {
        openTransferDetail(detailTransferId);
    }
});

// --- Alert System ---
function showAlert(message, type = 'success') {
    const alert = document.createElement('div');
    alert.className = `alert alert-${type}`;
    alert.setAttribute('role', 'alert');

    alert.innerHTML = `
        <span>${escapeHtml(message)}</span>
        <button class="alert-close" aria-label="${escapeHtml(__('general.dismiss'))}">×</button>
    `;

    alertContainer.appendChild(alert);

    // Auto dismiss after 5s
    const timeout = setTimeout(() => {
        alert.style.opacity = '0';
        alert.style.transform = 'translateX(100px)';
        alert.style.transition = 'all 0.5s ease-out';
        setTimeout(() => alert.remove(), 500);
    }, 5000);

    alert.querySelector('.alert-close').addEventListener('click', () => {
        clearTimeout(timeout);
        alert.remove();
    });
}

// --- Navigation ---
function initNavigation() {
    navItems.forEach(item => {
        item.addEventListener('click', () => {
            const target = item.getAttribute('data-target');
            switchTab(target);
        });
    });
}

function switchTab(tabId) {
    navItems.forEach(item => {
        if (item.getAttribute('data-target') === tabId) {
            item.classList.add('active');
            item.setAttribute('aria-current', 'page');
        } else {
            item.classList.remove('active');
            item.removeAttribute('aria-current');
        }
    });

    tabContents.forEach(content => {
        if (content.id === tabId) {
            content.classList.add('active');
        } else {
            content.classList.remove('active');
        }
    });

    activeTab = tabId;

    // Special reload operations on tab switch
    if (tabId === 'accounts-section') {
        loadAccounts();
    } else if (tabId === 'transfer-section') {
        populateTransferDropdowns();
    } else if (tabId === 'reports-section') {
        populateReportDropdown();
    }
}

// --- API Helpers ---
// Matches backend TRANSACTION_TIMEOUT_SECONDS=30: never wait forever.
const REQUEST_TIMEOUT_MS = 30000;
const PAGE_SIZE = 100; // backend @Max(100) on page size

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
        if (options.method && !['GET', 'HEAD', 'OPTIONS'].includes(options.method.toUpperCase())) {
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
let refreshInFlight = null;

async function trySilentRefresh() {
    if (refreshInFlight) {
        try {
            await refreshInFlight;
            return authenticated;
        } catch (e) { return false; }
    }
    refreshInFlight = (async () => {
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
    })();
    try {
        return await refreshInFlight;
    } catch (e) {
        return false;
    } finally {
        refreshInFlight = null;
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

function populateReportDropdown() {
    const reportSelect = document.getElementById('report-account-select');
    const prevVal = reportSelect.value;

    reportSelect.innerHTML = `<option value="" disabled selected>${__('report.account_placeholder')}</option>`;

    accounts.forEach(acc => {
        const option = document.createElement('option');
        option.value = acc.id;
        option.textContent = `${acc.ownerName} - ${formatIbanDisplay(acc.iban)}`;
        reportSelect.appendChild(option);
    });

    if (prevVal && accounts.some(a => a.id == prevVal)) {
        reportSelect.value = prevVal;
        loadAccountHistory(prevVal);
    }
}

function initReportSection() {
    const reportSelect = document.getElementById('report-account-select');
    const tabBtns = document.querySelectorAll('.report-tab-btn');
    const tabContents = document.querySelectorAll('.report-tab-content');
    const reportFilterForm = document.getElementById('report-filter-form');

    // Last 30 calendar days, including today.
    const now = new Date();
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(now.getDate() - 29);

    document.getElementById('report-start-date').value = formatDateInput(thirtyDaysAgo);
    document.getElementById('report-end-date').value = formatDateInput(now);

    // Dropdown change triggers history load
    reportSelect.addEventListener('change', (e) => {
        loadAccountHistory(e.target.value);
        invalidateGeneratedReport();
    });
    reportFilterForm.addEventListener('input', invalidateGeneratedReport);
    reportFilterForm.addEventListener('change', invalidateGeneratedReport);

    // Tab buttons toggle
    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            tabBtns.forEach(b => {
                b.classList.remove('active');
                b.setAttribute('aria-selected', 'false');
            });
            btn.classList.add('active');
            btn.setAttribute('aria-selected', 'true');

            const target = btn.getAttribute('data-tab');
            tabContents.forEach(content => {
                if (content.id === target) {
                    content.classList.add('active');
                } else {
                    content.classList.remove('active');
                }
            });
            document.getElementById('btn-print-report').disabled = target !== 'generator-tab' || !window.__lastReport;

            // The volume chart measures its canvas on draw; a hidden tab has
            // zero width, so redraw when returning to the generator view.
            if (target === 'generator-tab' && window.__lastReport
                    && !document.getElementById('report-results').classList.contains('d-none')) {
                drawReportChart(window.__lastReport.transfers || []);
            }
        });
    });

    // Report Generation Form submit
    reportFilterForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const accountId = reportSelect.value;
        const startDate = document.getElementById('report-start-date').value;
        const endDate = document.getElementById('report-end-date').value;

        if (!accountId) {
            showAlert(__('report.select_account_first'), 'warning');
            return;
        }

        let range;
        try {
            range = buildReportDateRange(startDate, endDate);
        } catch (error) {
            showAlert(error.message, 'warning');
            return;
        }

        invalidateGeneratedReport();
        reportPager.criteria = { accountId, ...range };
        loadReportPage(0);
    });
    document.getElementById('report-page-previous').addEventListener('click', () => loadReportPage(reportPager.page - 1));
    document.getElementById('report-page-next').addEventListener('click', () => loadReportPage(reportPager.page + 1));
}

function invalidateGeneratedReport() {
    reportPager = { criteria: null, page: 0, hasNext: false, loading: false,
        requestVersion: reportPager.requestVersion + 1 };
    window.__lastReport = null;
    document.getElementById('report-results')?.classList.add('d-none');
    const printButton = document.getElementById('btn-print-report');
    if (printButton) printButton.disabled = true;
    updateReportPagination();
}

function updateReportPagination() {
    const controls = document.getElementById('report-pagination');
    if (!controls) return;
    controls.classList.toggle('d-none', !reportPager.criteria || (reportPager.page === 0 && !reportPager.hasNext));
    document.getElementById('report-page-previous').disabled = reportPager.loading || reportPager.page === 0;
    document.getElementById('report-page-next').disabled = reportPager.loading || !reportPager.hasNext;
    document.getElementById('report-page-label').textContent = __('report.page', reportPager.page + 1);
    const scope = document.getElementById('report-scope');
    if (scope) {
        const criteria = reportPager.criteria;
        scope.textContent = criteria ? __('report.scope', criteria.accountId,
            criteria.startDate.slice(0, 10), criteria.endDate.slice(0, 10), reportPager.page + 1) : '';
    }
}

async function loadReportPage(page) {
    if (!reportPager.criteria || reportPager.loading || page < 0
            || (page > reportPager.page && !reportPager.hasNext && reportPager.page !== 0)) return;
    reportPager.loading = true;
    const requestVersion = ++reportPager.requestVersion;
    const criteria = reportPager.criteria;
    updateReportPagination();
    try {
        const query = new URLSearchParams({ ...criteria, page: String(page), size: '100' });
        const report = await fetchApi(`/transfers/report?${query}`);
        if (requestVersion !== reportPager.requestVersion) return;
        reportPager.page = page;
        reportPager.hasNext = report.hasNext === true;
        renderReportResults(report);
        document.getElementById('btn-print-report').disabled = !document.getElementById('generator-tab').classList.contains('active');
    } catch (err) {
        if (requestVersion === reportPager.requestVersion) showAlert(err.message, 'danger');
    } finally {
        if (requestVersion === reportPager.requestVersion) {
            reportPager.loading = false;
            updateReportPagination();
        }
    }
}

async function loadAccountHistory(accountId, loadMore = false) {
    const historyList = document.getElementById('transaction-history-list');
    const selectedAcc = accounts.find(a => a.id == accountId);
    if (!selectedAcc) return;

    const sameAccount = historyPager.accountId !== null && String(historyPager.accountId) === String(accountId);
    if (!loadMore || !sameAccount) {
        historyPager = { accountId, page: 0, last: true, total: 0 };
        historyCache = { accountId, items: [] };
        historyList.innerHTML = `<div class="empty-state"><span class="spinner"></span><p>${__('report.history_loading')}</p></div>`;
    }
    const page = (loadMore && sameAccount) ? historyPager.page + 1 : 0;

    try {
        const response = await fetchApi(`/transfers/history/${accountId}?page=${page}&size=${PAGE_SIZE}`);
        const content = response.content || [];
        const pageItems = Array.isArray(content) ? content : [];
        historyCache = {
            accountId,
            items: (loadMore && sameAccount) ? historyCache.items.concat(pageItems) : pageItems
        };
        historyPager = {
            accountId,
            page,
            last: (response && typeof response.last === 'boolean') ? response.last : true,
            total: (response && typeof response.totalElements === 'number') ? response.totalElements : historyCache.items.length
        };
        const transfers = historyCache.items;
        historyList.innerHTML = '';
        const countPill = document.getElementById('history-count');
        if (countPill) {
            countPill.textContent = (!historyPager.last && historyPager.total > transfers.length)
                ? `${transfers.length}/${historyPager.total}`
                : `${transfers.length}`;
        }

        if (transfers.length === 0) {
            historyList.innerHTML = `
                <div class="empty-state">
                    <span class="empty-art"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z"/></svg></span>
                    <p>${__('report.history_none')}</p>
                </div>
            `;
            return;
        }

        transfers.forEach(t => {
            const isOutgoing = t.senderIban === selectedAcc.iban;
            const isCancelled = t.status === 'CANCELLED';
            const statusMeta = transferStatusMeta(t.status);

            const item = document.createElement('div');
            item.className = 'history-item';

            // Icon Class
            let badgeClass = '';
            let badgeIcon = '';
            let amountClass = '';
            let prefix = '';

            if (isCancelled) {
                badgeClass = 'badge-cancelled-icon';
                badgeIcon = '✖';
                amountClass = 'amount-cancelled';
                prefix = '';
            } else if (isOutgoing) {
                badgeClass = 'badge-outgoing';
                badgeIcon = '↗';
                amountClass = 'amount-minus';
                prefix = '-';
            } else {
                badgeClass = 'badge-incoming';
                badgeIcon = '↖';
                amountClass = 'amount-plus';
                prefix = '+';
            }

            // The backend owns the configurable cancellation window. Showing
            // the action for a completed outgoing transfer avoids a stale
            // client-side 24h rule hiding a server-eligible operation.
            const isEligibleForCancel = t.status === 'COMPLETED' && isOutgoing;
            const showStatusPill = !isCancelled && t.status !== 'COMPLETED';

            item.innerHTML = `
                <div class="history-details">
                    <div class="history-badge ${badgeClass}">${badgeIcon}</div>
                    <div class="history-meta">
                        <span class="history-title">
                            ${isCancelled ? __('transfer.cancelled_prefix') : ''}
                            ${isOutgoing ? __('transfer.outgoing', escapeHtml(formatIbanDisplay(t.receiverIban))) : __('transfer.incoming', escapeHtml(formatIbanDisplay(t.senderIban)))}
                        </span>
                        <span class="history-sub">${formatDate(t.createdAt)}</span>
                    </div>
                </div>
                <div class="history-right">
                    <span class="history-amount ${amountClass}">${prefix}${formatMoney(t.amount)} ${escapeHtml(t.currency)}</span>
                    ${showStatusPill ? `<span class="card-status-badge ${statusMeta.cls}">${statusMeta.label}</span>` : ''}
                    ${isEligibleForCancel ? `<button type="button" class="btn-cancel-transfer">${__('transfer.cancel_btn')}</button>` : ''}
                </div>
            `;

            historyList.appendChild(item);

            const cancelButton = item.querySelector('.btn-cancel-transfer');
            if (cancelButton) {
                cancelButton.addEventListener('click', (event) => {
                    event.stopPropagation();
                    cancelTransfer(t.id, accountId);
                });
            }

            item.addEventListener('click', (e) => {
                if (e.target.closest('.btn-cancel-transfer')) return;
                openTransferDetail(t.id);
            });
        });

        if (!historyPager.last && transfers.length > 0) {
            const moreWrap = document.createElement('div');
            moreWrap.className = 'load-more-wrap';
            moreWrap.innerHTML = `
                <p class="grid-hint">${__('general.showing_of', transfers.length, historyPager.total)}</p>
                <button type="button" class="btn btn-secondary" id="btn-more-history">${__('general.load_more')}</button>
            `;
            historyList.appendChild(moreWrap);
            document.getElementById('btn-more-history')
                .addEventListener('click', () => loadAccountHistory(accountId, true));
        }
    } catch (err) {
        showAlert(err.message, 'danger');
        historyList.innerHTML = `
            <div class="empty-state" style="color: var(--danger);">
                <span class="empty-art"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M12 3l10 18H2L12 3z"/><path d="M12 10v5M12 18.5v.5"/></svg></span>
                <p>${__('report.load_error')}</p>
            </div>
        `;
    }
}

// Exposed to global window scope so it can be called from dynamic HTML
async function cancelTransfer(transferId, accountId) {
    const keyName = `cancelKey:${userId}:${transferId}`;
    if (cancellationsInFlight.has(keyName)) return;
    cancellationsInFlight.add(keyName);
    let started = false;
    try {
        const confirmed = await confirmDialog(__('transfer.cancel_confirm'));
        if (!confirmed) return;
        const idempotencyKey = await getIdempotencyKey(keyName, { transferId });
        if (!idempotencyKey) return;
        markIdempotencyStarted(keyName);
        started = true;
        await fetchApi(`/transfers/${transferId}/cancel`, {
            method: 'POST',
            headers: { 'Idempotency-Key': idempotencyKey }
        });
        clearIdempotencyKey(keyName);
        showAlert(__('transfer.cancelled'));
        await loadAccounts();
        loadAccountHistory(accountId);
    } catch (err) {
        if (started) markIdempotencyFailed(keyName, err);
        showAlert(started ? idempotencyErrorMessage(err) : err.message, 'danger');
    } finally {
        cancellationsInFlight.delete(keyName);
    }
}

// --- Detail surfaces (cover the remaining read endpoints) ---
function showModal(id) {
    const m = document.getElementById(id);
    if (!m) return;
    try {
        m._opener = document.activeElement;
    } catch (e) { /* opener restore is best-effort */ }
    m.classList.add('active');
    const focusTarget = m.querySelector('.modal-container');
    if (focusTarget && typeof focusTarget.focus === 'function') {
        focusTarget.focus({ preventScroll: true });
    }
}

function hideModal(id) {
    const m = document.getElementById(id);
    if (!m) return;
    m.classList.remove('active');
    try {
        if (m._opener && typeof m._opener.focus === 'function') {
            m._opener.focus({ preventScroll: true });
        }
    } catch (e) { /* opener restore is best-effort */ }
    m._opener = null;
}

// Promise-based confirm replacing native confirm(), styled by the design system.
let __confirmResolver = null;
function confirmDialog(message) {
    const overlay = document.getElementById('confirm-modal');
    const msg = document.getElementById('confirm-message');
    if (!overlay || !msg) {
        return Promise.resolve(false);
    }
    msg.textContent = message;
    showModal('confirm-modal');
    return new Promise((resolve) => {
        __confirmResolver = resolve;
    });
}

function settleConfirm(value) {
    hideModal('confirm-modal');
    if (typeof __confirmResolver === 'function') {
        const resolve = __confirmResolver;
        __confirmResolver = null;
        resolve(value);
    }
}

function resolveAccountRef(accountId) {
    const acc = accounts.find(a => a.id == accountId);
    if (acc) {
        return `${escapeHtml(acc.ownerName)} · ${escapeHtml(formatIbanDisplay(acc.iban))}`;
    }
    return `#${accountId}`;
}

let detailAccountId = null;
let detailTransferId = null;

async function openAccountDetail(accountId) {
    detailAccountId = accountId;
    showModal('account-detail-modal');
    const body = document.getElementById('account-detail-body');
    const idLine = document.getElementById('account-detail-id');
    if (idLine) idLine.textContent = `#${accountId}`;
    if (!body) return;
    body.innerHTML = `<div class="empty-state"><span class="spinner"></span><p>${__('general.loading')}</p></div>`;
    try {
        // GET /accounts/{id}: backend returns the caller's own account only.
        const acc = await fetchApi(`/accounts/${accountId}`);
        body.innerHTML = `
            <div class="detail-balance">
                <span class="card-balance-label">${__('account.balance_label')}</span>
                <div class="card-balance">
                    <span>${formatMoney(acc.balance)}</span>
                    <span class="card-currency">${escapeHtml(acc.currency)}</span>
                </div>
            </div>
            <dl class="summary-list">
                <div><dt>${__('modal.account_name')}</dt><dd>${escapeHtml(acc.ownerName)}</dd></div>
                <div><dt>${__('modal.iban')}</dt><dd class="mono">${escapeHtml(formatIbanDisplay(acc.iban))} <button type="button" class="copy-btn" data-copy="${escapeHtml(acc.iban)}" title="${escapeHtml(__('general.copy_iban'))}" aria-label="${escapeHtml(__('general.copy_iban'))}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg></button></dd></div>
                <div><dt>${__('report.table_status')}</dt><dd><span class="card-status-badge ${acc.active ? 'badge-active' : 'badge-inactive'}">${acc.active ? __('account.active') : __('account.inactive')}</span> <span class="text-muted mono">${escapeHtml(acc.status || '')}</span></dd></div>
            </dl>
        `;
    } catch (err) {
        body.innerHTML = `
            <div class="empty-state" style="color: var(--danger);">
                <span class="empty-art"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M12 3l10 18H2L12 3z"/><path d="M12 10v5M12 18.5v.5"/></svg></span>
                <p>${escapeHtml(err.message || __('general.error'))}</p>
            </div>
        `;
    }
}

async function openTransferDetail(transferId) {
    detailTransferId = transferId;
    showModal('transfer-detail-modal');
    const body = document.getElementById('transfer-detail-body');
    const idLine = document.getElementById('transfer-detail-id');
    if (idLine) idLine.textContent = `#${transferId}`;
    if (!body) return;
    body.innerHTML = `<div class="empty-state"><span class="spinner"></span><p>${__('general.loading')}</p></div>`;
    try {
        // GET /transfers/{id}: backend authorizes sender-or-receiver only.
        // TransferDetailResponse carries account IDs (no IBANs): resolve names
        // from loaded accounts, fall back to #id for external accounts.
        const t = await fetchApi(`/transfers/${transferId}`);
        const meta = transferStatusMeta(t.status);
        body.innerHTML = `
            <dl class="summary-list">
                <div><dt>${__('report.table_date')}</dt><dd>${formatDate(t.createdAt)}</dd></div>
                <div><dt>${__('transfer.sum_from')}</dt><dd>${resolveAccountRef(t.senderAccountId)}</dd></div>
                <div><dt>${__('transfer.sum_to')}</dt><dd>${resolveAccountRef(t.receiverAccountId)}</dd></div>
                <div><dt>${__('transfer.sum_amount')}</dt><dd class="mono">${formatMoney(t.amount)} ${escapeHtml(t.currency)}</dd></div>
                <div><dt>${__('report.table_status')}</dt><dd><span class="card-status-badge ${meta.cls}">${meta.label}</span></dd></div>
            </dl>
        `;
    } catch (err) {
        body.innerHTML = `
            <div class="empty-state" style="color: var(--danger);">
                <span class="empty-art"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M12 3l10 18H2L12 3z"/><path d="M12 10v5M12 18.5v.5"/></svg></span>
                <p>${escapeHtml(err.message || __('general.error'))}</p>
            </div>
        `;
    }
}

let lastIbanLookup = '';

async function lookupManualIban(iban) {
    const note = document.getElementById('iban-lookup-note');
    if (!note || !authenticated) return;
    const v = String(iban || '').trim();
    if (!isValidIban(v) || !hasValidIbanChecksum(v)) {
        note.classList.add('d-none');
        note.innerHTML = '';
        lastIbanLookup = '';
        return;
    }
    if (v === lastIbanLookup) return;
    lastIbanLookup = v;
    try {
        // GET /accounts/iban/{iban}: backend only returns the caller's OWN
        // accounts (403 otherwise), so a hit means "one of my accounts".
        // Anything else stays silent by design — no existence oracle.
        const acc = await fetchApi(`/accounts/iban/${encodeURIComponent(v)}`);
        note.classList.remove('d-none');
        note.innerHTML = `${__('transfer.own_iban_note', escapeHtml(acc.ownerName))}`;
    } catch (err) {
        note.classList.add('d-none');
        note.innerHTML = '';
    }
}

function initDetailModals() {
    ['account-detail-modal', 'transfer-detail-modal'].forEach(id => {
        const m = document.getElementById(id);
        if (!m) return;
        m.addEventListener('click', (e) => {
            if (e.target === m) hideModal(id);
        });
    });
    const closeAcc = document.getElementById('close-account-detail');
    if (closeAcc) closeAcc.addEventListener('click', () => hideModal('account-detail-modal'));
    const closeTr = document.getElementById('close-transfer-detail');
    if (closeTr) closeTr.addEventListener('click', () => hideModal('transfer-detail-modal'));
    const receiptClose = document.getElementById('btn-receipt-close');
    if (receiptClose) receiptClose.addEventListener('click', () => hideModal('transfer-detail-modal'));
    const confirmOk = document.getElementById('btn-confirm-ok');
    if (confirmOk) confirmOk.addEventListener('click', () => settleConfirm(true));
    const confirmCancel = document.getElementById('btn-confirm-cancel');
    if (confirmCancel) confirmCancel.addEventListener('click', () => settleConfirm(false));
    const confirmOverlay = document.getElementById('confirm-modal');
    if (confirmOverlay) {
        confirmOverlay.addEventListener('click', (e) => {
            if (e.target === confirmOverlay) settleConfirm(false);
        });
    }
    const goHistory = document.getElementById('btn-detail-history');
    if (goHistory) {
        goHistory.addEventListener('click', () => {
            hideModal('account-detail-modal');
            switchTab('reports-section');
            syncMobileNav('reports-section');
            if (detailAccountId !== null) {
                const select = document.getElementById('report-account-select');
                if (select) {
                    select.value = detailAccountId;
                    select.dispatchEvent(new Event('change'));
                }
            }
        });
    }
    const goTransfer = document.getElementById('btn-detail-transfer');
    if (goTransfer) {
        goTransfer.addEventListener('click', () => {
            hideModal('account-detail-modal');
            switchTab('transfer-section');
            syncMobileNav('transfer-section');
            if (detailAccountId !== null) {
                const sender = document.getElementById('sender-account-select');
                if (sender) {
                    sender.value = detailAccountId;
                    sender.dispatchEvent(new Event('change'));
                }
            }
        });
    }
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            if (document.getElementById('confirm-modal')?.classList.contains('active')) {
                settleConfirm(false);
            }
            hideModal('account-detail-modal');
            hideModal('transfer-detail-modal');
        }
        // Focus trap: keep Tab cycling inside the topmost open modal.
        if (e.key === 'Tab') {
            const open = [...document.querySelectorAll('.modal-overlay.active')].pop();
            if (!open) return;
            const focusables = [...open.querySelectorAll(
                'button:not([disabled]), input, select, textarea, [tabindex]:not([tabindex="-1"])'
            )].filter(el => el.offsetParent !== null || el === document.activeElement);
            if (focusables.length === 0) return;
            const first = focusables[0];
            const last = focusables[focusables.length - 1];
            if (e.shiftKey && document.activeElement === first) {
                e.preventDefault();
                last.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
                e.preventDefault();
                first.focus();
            }
        }
    });
}

function renderReportResults(report) {
    const resultsContainer = document.getElementById('report-results');
    const countEl = document.getElementById('report-stat-count');
    const volumeEl = document.getElementById('report-stat-volume');
    const avgEl = document.getElementById('report-stat-avg');
    const tableBody = document.getElementById('report-table-body');

    countEl.textContent = report.pageTransferCount;
    volumeEl.textContent = `${formatMoney(report.pageVolume)} ${report.currency}`;
    if (avgEl) {
        const avg = report.pageTransferCount > 0 ? Number(report.pageVolume) / Number(report.pageTransferCount) : 0;
        avgEl.textContent = report.pageTransferCount > 0 ? `${formatMoney(avg)} ${report.currency}` : '—';
    }
    if (typeof drawReportChart === 'function') {
        try { drawReportChart(report.transfers || []); } catch (e) { /* chart is decorative */ }
    }
    tableBody.innerHTML = '';

    if (report.transfers.length === 0) {
        tableBody.innerHTML = `
            <tr>
                <td colspan="4" class="text-muted" style="text-align: center; padding: 2rem;">
                    ${__('report.no_transactions')}
                </td>
            </tr>
        `;
    } else {
        report.transfers.forEach(t => {
            const tr = document.createElement('tr');

            const isCancelled = t.status === 'CANCELLED';
            const statusMeta = transferStatusMeta(t.status);
            const statusBadge = `<span class="card-status-badge ${statusMeta.cls}">${statusMeta.label}</span>`;

            tr.innerHTML = `
                <td>${formatDate(t.createdAt)}</td>
                <td class="mono">${escapeHtml(formatIbanDisplay(t.senderIban))} &rarr; ${escapeHtml(formatIbanDisplay(t.receiverIban))}</td>
                <td>${statusBadge}</td>
                <td class="text-right ${isCancelled ? 'amount-cancelled' : 'amount-minus'}">
                    ${formatMoney(t.amount)} ${escapeHtml(t.currency)}
                </td>
            `;
            tableBody.appendChild(tr);
        });
    }

    resultsContainer.classList.remove('d-none');
}

// --- Utility Formatters ---
// Localized badge metadata for backend TransferStatus values.
function transferStatusMeta(status) {
    switch (status) {
        case 'CANCELLED': return { label: __('report.status_cancelled'), cls: 'badge-inactive' };
        case 'PENDING': return { label: __('report.status_pending'), cls: 'badge-pending' };
        case 'FAILED': return { label: __('report.status_failed'), cls: 'badge-failed' };
        default: return { label: __('report.status_completed'), cls: 'badge-active' };
    }
}
function escapeHtml(unsafe) {
    if (unsafe === null || unsafe === undefined) return '';
    return String(unsafe)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
function formatMoney(amount) {
    // Defensive: backend always sends non-null numerics, but a null/NaN must
    // never render as "NaN" in a banking UI.
    const n = Number(amount);
    if (!Number.isFinite(n)) return '—';
    return new Intl.NumberFormat(locale(), { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n);
}

function sanitizeIban(value) {
    // UX: the TR prefix is locked in and typed for the user; only digits can
    // follow (backend rule: ^TR[0-9]{24}$). Empty stays empty so the
    // placeholder and required-validation keep working.
    const raw = String(value || '');
    if (!raw.trim()) return '';
    return 'TR' + raw.toUpperCase().replace(/[^0-9]/g, '').slice(0, 24);
}

function isValidIban(value) {
    const v = String(value || '').toUpperCase().replace(/\s+/g, '');
    return /^TR[0-9]{24}$/.test(v);
}

function hasValidIbanChecksum(value) {
    const v = String(value || '').toUpperCase().replace(/\s+/g, '');
    if (!isValidIban(v)) return false;
    // Same ISO 13616 MOD 97-10 calculation as the account creation use case.
    const rearranged = v.slice(4) + '2927' + v.slice(2, 4);
    let remainder = 0;
    for (const digit of rearranged) {
        remainder = (remainder * 10 + Number(digit)) % 97;
    }
    return remainder === 1;
}

function formatIbanDisplay(iban) {
    if (!iban) return '';
    return iban.replace(/(.{4})/g, '$1 ').trim();
}

function formatDate(dateString) {
    const d = new Date(dateString);
    return d.toLocaleString(locale(), {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function formatDateInput(date) {
    const pad = (n) => n.toString().padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function buildReportDateRange(startDay, endDay) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(startDay) || !/^\d{4}-\d{2}-\d{2}$/.test(endDay)) {
        throw new Error(__('report.select_dates'));
    }
    if (startDay > endDay) {
        throw new Error(__('report.invalid_date_range'));
    }
    // Backend rule (GenerateTransferReportQueryImpl): start.plusMonths(12)
    // must not be before end. End-of-day is 23:59:59.999999, so endDay must
    // be strictly before start-plus-12-months (ISO strings compare
    // chronologically). Mirrors LocalDate.plusMonths incl. month-end clamp.
    if (endDay >= plusMonths12(startDay)) {
        throw new Error(__('report.range_too_long'));
    }
    // PostgreSQL stores transfer time as TIMESTAMP(6); BETWEEN is inclusive.
    return { startDate: `${startDay}T00:00:00`, endDate: `${endDay}T23:59:59.999999` };
}

function plusMonths12(day) {
    const [y, m, d] = day.split('-').map(Number);
    const target = new Date(y, m - 1 + 12, 1);
    const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
    const pad = (n) => String(n).padStart(2, '0');
    return `${target.getFullYear()}-${pad(target.getMonth() + 1)}-${pad(Math.min(d, lastDay))}`;
}

// --- Authentication Operations ---
function checkAuthStatus() {
    const authContainer = document.getElementById('auth-container');
    const appContainer = document.getElementById('app-container');
    const displayUsername = document.getElementById('display-username');

    if (authenticated) {
        authContainer.classList.add('d-none');
        appContainer.classList.remove('d-none');
        displayUsername.textContent = username;
        switchTab('accounts-section');
        loadAccountCapabilities();
    } else {
        fundingMode = 'unknown';
        renderFundingMode();
        authContainer.classList.remove('d-none');
        appContainer.classList.add('d-none');
    }
}

function initAuth() {
    const tabLoginBtn = document.getElementById('tab-login-btn');
    const tabRegisterBtn = document.getElementById('tab-register-btn');
    const loginForm = document.getElementById('login-form');
    const registerForm = document.getElementById('register-form');
    const loginSpinner = document.getElementById('login-spinner');
    const registerSpinner = document.getElementById('register-spinner');
    const btnLoginSubmit = document.getElementById('btn-login-submit');
    const btnRegisterSubmit = document.getElementById('btn-register-submit');
    const btnLogout = document.getElementById('btn-logout');

    // Tab Switching
    tabLoginBtn.addEventListener('click', () => {
        tabLoginBtn.classList.add('active');
        tabLoginBtn.setAttribute('aria-selected', 'true');
        tabRegisterBtn.classList.remove('active');
        tabRegisterBtn.setAttribute('aria-selected', 'false');
        loginForm.classList.remove('d-none');
        registerForm.classList.add('d-none');
    });

    tabRegisterBtn.addEventListener('click', () => {
        tabRegisterBtn.classList.add('active');
        tabRegisterBtn.setAttribute('aria-selected', 'true');
        tabLoginBtn.classList.remove('active');
        tabLoginBtn.setAttribute('aria-selected', 'false');
        registerForm.classList.remove('d-none');
        loginForm.classList.add('d-none');
    });

    // Login Form Submit
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const usernameVal = document.getElementById('login-username').value.trim();
        const passwordVal = document.getElementById('login-password').value;
        if (!usernameVal) {
            showAlert(__('auth.username.required'), 'warning');
            return;
        }

        loginSpinner.classList.remove('d-none');
        btnLoginSubmit.disabled = true;
        btnLoginSubmit.setAttribute('aria-busy', 'true');

        try {
            const result = await fetchApi('/auth/browser/login', {
                method: 'POST',
                body: JSON.stringify({ username: usernameVal, password: passwordVal })
            });

            authenticated = true;
            userId = result.userId;
            username = result.username;

            showAlert(__('auth.login.success'));
            checkAuthStatus();
        } catch (err) {
            showAlert(__('auth.login.failed') + err.message, 'danger');
        } finally {
            loginSpinner.classList.add('d-none');
            btnLoginSubmit.disabled = false;
            btnLoginSubmit.removeAttribute('aria-busy');
        }
    });

    // Register Form Submit
    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (btnRegisterSubmit.disabled) return;
        const usernameVal = document.getElementById('register-username').value.trim();
        const passwordVal = document.getElementById('register-password').value;
        if (!usernameVal) {
            showAlert(__('auth.username.required'), 'warning');
            return;
        }

        // Client mirror of backend PasswordPolicy defaults (min 12, upper,
        // lower, digit): instant feedback instead of a round-trip 400.
        if (!meetsPasswordPolicy(passwordVal)) {
            showAlert(__('auth.password.hint'), 'danger');
            return;
        }

        registerSpinner.classList.remove('d-none');
        btnRegisterSubmit.disabled = true;
        btnRegisterSubmit.setAttribute('aria-busy', 'true');

        const request = { username: usernameVal, password: passwordVal };
        const keyName = 'registerKey';
        let started = false;
        try {
            const registerKey = await getIdempotencyKey(keyName, request, false);
            if (!registerKey) return;
            markIdempotencyStarted(keyName);
            started = true;
            await fetchApi('/auth/register', {
                method: 'POST',
                headers: {
                    'Idempotency-Key': registerKey
                },
                body: JSON.stringify(request)
            });
            clearIdempotencyKey(keyName);

            showAlert(__('auth.register.success'));
            registerForm.reset();
            // Switch to login tab
            tabLoginBtn.click();
        } catch (err) {
            if (started) markIdempotencyFailed(keyName, err);
            showAlert(__('auth.register.failed') + (started ? idempotencyErrorMessage(err) : err.message), 'danger');
        } finally {
            registerSpinner.classList.add('d-none');
            btnRegisterSubmit.disabled = false;
            btnRegisterSubmit.removeAttribute('aria-busy');
        }
    });

    // Revoke server-side first. A failed/aborted request means the JWT may
    // remain usable until expiry; make that distinction visible to the user.
    btnLogout.addEventListener('click', async () => {
        if (btnLogout.disabled) return;
        btnLogout.disabled = true;
        let revoked = !authenticated;
        try {
            if (authenticated) {
                for (let attempt = 0; attempt < 2; attempt++) {
                    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
                    const timeoutId = controller ? setTimeout(() => controller.abort(), 5000) : null;
                    // Omit the CSRF header entirely when there is no cookie:
                    // an empty-but-present header is a mismatch on some filters.
                    const csrf = browserCsrfToken();
                    const logoutHeaders = { 'Content-Type': 'application/json' };
                    if (csrf) logoutHeaders['X-CSRF-Token'] = csrf;
                    try {
                        const response = await fetch(`${API_BASE}/auth/browser/logout`, {
                            method: 'POST',
                            headers: logoutHeaders,
                            credentials: 'same-origin',
                            ...(controller ? { signal: controller.signal } : {})
                        });
                        revoked = response.ok;
                        if (!response.ok) console.error('Logout revoke failed with HTTP', response.status);
                        // An expired access cookie is rejected by the filter
                        // before reaching the controller (so its cookie-clear
                        // never runs): rotate once, then retry the revoke so
                        // the server-side session is actually cleared.
                        if (!response.ok && response.status === 401 && attempt === 0
                                && typeof trySilentRefresh === 'function'
                                && await trySilentRefresh()) {
                            continue;
                        }
                        break; // Only a lost/aborted response warrants retrying.
                    } catch (e) {
                        console.error('Logout revoke attempt failed:', e);
                    } finally {
                        if (timeoutId) clearTimeout(timeoutId);
                    }
                }
            }
        } catch (e) {
            console.error('Logout revoke failed:', e);
        } finally {
            btnLogout.disabled = false;
        }
        if (revoked) logout();
        showAlert(__(revoked ? 'auth.logout.success' : 'auth.logout.local_only'), revoked ? 'success' : 'warning');
    });
}

function logout() {
    authenticated = false;
    userId = null;
    username = null;
    accounts = [];
    accountPager = { page: 0, last: true, total: 0 };
    historyCache = { accountId: null, items: [] };
    historyPager = { accountId: null, page: 0, last: true, total: 0 };
    invalidateGeneratedReport();
    detailAccountId = null;
    detailTransferId = null;
    lastIbanLookup = '';
    hideModal('account-detail-modal');
    hideModal('transfer-detail-modal');
    const ibanNote = document.getElementById('iban-lookup-note');
    if (ibanNote) {
        ibanNote.classList.add('d-none');
        ibanNote.innerHTML = '';
    }
    clearAllIdempotencyKeys();

    // Reset all forms
    document.getElementById('login-form').reset();
    document.getElementById('register-form').reset();
    document.getElementById('transfer-form').reset();
    document.getElementById('report-filter-form').reset();
    document.getElementById('create-account-form').reset();

    // Close modal if open
    document.getElementById('create-account-modal').classList.remove('active');

    // Reset dynamic UI lists & indicators
    document.getElementById('accounts-list').innerHTML = '';
    document.getElementById('transaction-history-list').innerHTML = '';
    document.getElementById('report-results').classList.add('d-none');
    document.getElementById('sender-balance-indicator').textContent = '';
    document.getElementById('sender-account-select').innerHTML = `<option value="" disabled selected>${__('transfer.sender_placeholder')}</option>`;
    document.getElementById('receiver-account-select').innerHTML = `<option value="" disabled selected>${__('transfer.recipient_placeholder')}</option>`;
    document.getElementById('report-account-select').innerHTML = `<option value="" disabled selected>${__('report.account_placeholder')}</option>`;

    // Reset active tab variable
    activeTab = 'accounts-section';

    checkAuthStatus();
}

/* ============================================================
   UX ENHANCEMENTS (additive — core flows untouched)
   ============================================================ */
function syncMobileNav(tabId) {
    document.querySelectorAll('.nav-item').forEach(item => {
        const on = item.getAttribute('data-target') === tabId;
        item.classList.toggle('active', on);
        if (on) item.setAttribute('aria-current', 'page');
        else item.removeAttribute('aria-current');
    });
}

function drawReportChart(transfers) {
    const canvas = document.getElementById('report-chart');
    if (!canvas) return;
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth || canvas.parentElement.clientWidth || 600;
    const h = 90;
    canvas.width = w * dpr;
    canvas.height = h * dpr;
    const ctx = canvas.getContext('2d');
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, w, h);
    const buckets = {};
    (transfers || []).forEach(t => {
        const d = new Date(t.createdAt);
        if (isNaN(d)) return;
        const k = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
        buckets[k] = (buckets[k] || 0) + Number(t.amount || 0);
    });
    const keys = Object.keys(buckets).sort().slice(-14);
    if (keys.length === 0) {
        ctx.fillStyle = 'rgba(140,160,155,0.5)';
        ctx.font = '12px Inter, sans-serif';
        ctx.fillText('—', 8, h / 2);
        return;
    }
    const max = Math.max(...keys.map(k => buckets[k]), 1);
    const bw = Math.max(8, (w - 16) / keys.length - 8);
    const isLight = document.documentElement.getAttribute('data-theme') === 'light';
    keys.forEach((k, i) => {
        const v = buckets[k] / max;
        const bh = Math.max(6, v * (h - 22));
        const x = 8 + i * ((w - 16) / keys.length);
        const y = h - 8 - bh;
        const g = ctx.createLinearGradient(0, y, 0, h);
        g.addColorStop(0, isLight ? '#143D7A' : '#3A7BFF');
        g.addColorStop(1, isLight ? 'rgba(20,61,122,0.15)' : 'rgba(47,111,237,0.08)');
        ctx.fillStyle = g;
        ctx.beginPath();
        if (typeof ctx.roundRect === 'function') {
            ctx.roundRect(x, y, bw, bh, 4);
        } else {
            ctx.rect(x, y, bw, bh);
        }
        ctx.fill();
    });
}

function passwordScore(pw) {
    let s = 0;
    if (!pw) return 0;
    if (pw.length >= 12) s++;
    if (pw.length >= 16) s++;
    if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) s++;
    if (/\d/.test(pw)) s++;
    if (/[^A-Za-z0-9]/.test(pw)) s++;
    return Math.min(s, 5);
}

function meetsPasswordPolicy(pw) {
    // Client mirror of backend RegisterWebRequest: PasswordPolicy defaults
    // (min 12, upper, lower, digit) plus @Size(max = 72) — the BCrypt
    // 72-byte truncation guard. maxlength=72 covers typed input; this covers
    // pasted/programmatic values so a doomed 400 round-trip never starts.
    return !!pw && pw.length >= 12 && pw.length <= 72
        && /[A-Z]/.test(pw) && /[a-z]/.test(pw) && /\d/.test(pw);
}

function initUxEnhancements() {
    window.__accountView = window.__accountView || { currency: 'all', sort: 'new' };

    // Avatar + display name sync
    const syncAvatar = () => {
        const av = document.getElementById('user-avatar');
        const dn = document.getElementById('display-username');
        const name = (username || (dn && dn.textContent) || 'U').trim() || 'U';
        if (av) av.textContent = name.charAt(0).toUpperCase();
        if (dn && username) dn.textContent = username;
    };
    syncAvatar();
    document.addEventListener('languagechange', syncAvatar);
    const _checkAuth = checkAuthStatus;
    checkAuthStatus = function () { _checkAuth(); syncAvatar(); };

    // Password visibility toggles
    document.querySelectorAll('.pw-toggle').forEach(btn => {
        btn.addEventListener('click', () => {
            const input = document.getElementById(btn.getAttribute('data-pw-target'));
            if (!input) return;
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            btn.setAttribute('aria-pressed', show ? 'true' : 'false');
            btn.setAttribute('aria-label', show ? __('auth.hide_password') : __('auth.show_password'));
            input.focus();
        });
    });

    // Register password strength
    const regPw = document.getElementById('register-password');
    const bar = document.getElementById('register-strength-bar');
    if (regPw && bar) {
        const colors = ['#fb7185', '#fb923c', '#fbbf24', '#34d399', '#2dd4bf'];
        regPw.addEventListener('input', () => {
            const s = passwordScore(regPw.value);
            bar.style.width = `${(s / 5) * 100}%`;
            bar.style.background = colors[Math.max(0, s - 1)] || '#fb7185';
        });
    }

    // IBAN counters
    const bindCounter = (inputId, counterId) => {
        const inp = document.getElementById(inputId);
        const c = document.getElementById(counterId);
        if (!inp || !c) return;
        const upd = () => { c.textContent = (inp.value || '').length; };
        inp.addEventListener('input', upd);
        upd();
    };
    bindCounter('receiver-iban-input', 'iban-counter');

    // Copy IBAN (event delegation, works for dynamic cards)
    document.addEventListener('click', async (e) => {
        const btn = e.target.closest('[data-copy]');
        if (!btn) return;
        e.stopPropagation();
        const text = btn.getAttribute('data-copy') || '';
        try {
            await navigator.clipboard.writeText(text);
            showAlert(__('account.copied'));
        } catch (err) {
            const ta = document.createElement('textarea');
            ta.value = text;
            document.body.appendChild(ta);
            ta.select();
            try { document.execCommand('copy'); showAlert(__('account.copied')); } catch (e2) { /* noop */ }
            ta.remove();
        }
    });

    // Account toolbar: currency pills + sort (sector-standard listing filter)
    const sort = document.getElementById('account-sort');
    document.querySelectorAll('.seg-pill[data-currency]').forEach(pill => {
        pill.addEventListener('click', () => {
            document.querySelectorAll('.seg-pill[data-currency]').forEach(p => p.classList.remove('is-active'));
            pill.classList.add('is-active');
            window.__accountView.currency = pill.getAttribute('data-currency') || 'all';
            if (authenticated) loadAccounts();
        });
    });
    if (sort) {
        sort.addEventListener('change', () => {
            window.__accountView.sort = sort.value;
            if (authenticated) loadAccounts();
        });
    }
    const refreshBtn = document.getElementById('btn-refresh-accounts');
    if (refreshBtn) refreshBtn.addEventListener('click', () => { if (authenticated) loadAccounts(); });

    // Quick-transfer + generic goto buttons
    document.querySelectorAll('[data-goto]').forEach(b => {
        b.addEventListener('click', () => {
            switchTab(b.getAttribute('data-goto'));
            syncMobileNav(b.getAttribute('data-goto'));
        });
    });

    // Transfer: quick amount chips
    document.querySelectorAll('.chip[data-amount]').forEach(ch => {
        ch.addEventListener('click', () => {
            const amtInput = document.getElementById('transfer-amount');
            if (!amtInput) return;
            const v = ch.getAttribute('data-amount');
            if (v === 'max') {
                const sender = document.getElementById('sender-account-select');
                const acc = accounts.find(a => a.id == (sender && sender.value));
                if (acc) amtInput.value = acc.balance;
            } else {
                amtInput.value = v;
            }
            amtInput.dispatchEvent(new Event('input', { bubbles: true }));
            amtInput.focus();
        });
    });

    // Transfer live summary + steps
    const updateSummary = () => {
        const sSel = document.getElementById('sender-account-select');
        const rSel = document.getElementById('receiver-account-select');
        const rIban = document.getElementById('receiver-iban-input');
        const amt = document.getElementById('transfer-amount');
        const cur = document.getElementById('transfer-currency');
        const f = document.getElementById('sum-from');
        const t = document.getElementById('sum-to');
        const a = document.getElementById('sum-amount');
        const note = document.getElementById('sum-note');
        if (!f || !t || !a) return;
        const sAcc = accounts.find(x => x.id == (sSel && sSel.value));
        const rAcc = accounts.find(x => x.id == (rSel && rSel.value));
        const isManual = document.querySelector('input[name="receiver-type"]:checked')?.value === 'manual';
        f.textContent = sAcc ? `${sAcc.ownerName} · ${formatIbanDisplay(sAcc.iban)}` : '—';
        t.textContent = isManual
            ? ((rIban && rIban.value.trim()) || '—')
            : (rAcc ? `${rAcc.ownerName} · ${formatIbanDisplay(rAcc.iban)}` : '—');
        const amtVal = parseFloat(amt && amt.value);
        a.textContent = Number.isFinite(amtVal) && amtVal > 0 ? `${formatMoney(amtVal)} ${cur ? cur.value : ''}` : '—';
        const ready = !!(sAcc && (isManual ? (rIban && isValidIban(rIban.value.trim())) : rAcc) && Number.isFinite(amtVal) && amtVal > 0);
        if (note) {
            note.textContent = ready ? __('transfer.sum_ready') : __('transfer.sum_hint');
            note.classList.toggle('ready', ready);
        }
        document.querySelectorAll('.steps .step').forEach(st => {
            const n = st.getAttribute('data-step');
            st.classList.toggle('is-active', (n === '1' && !sAcc) || (n === '2' && !!sAcc && !ready) || (n === '3' && ready));
            st.classList.toggle('is-done', (n === '1' && !!sAcc) || (n === '2' && ready));
        });
    };
    ['sender-account-select', 'receiver-account-select', 'receiver-iban-input', 'transfer-amount', 'transfer-currency'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('input', updateSummary);
        if (el) el.addEventListener('change', updateSummary);
    });
    document.querySelectorAll('input[name="receiver-type"]').forEach(r => r.addEventListener('change', updateSummary));
    document.addEventListener('languagechange', updateSummary);

    // Report quick ranges + print + footer clock
    document.querySelectorAll('.chip[data-range]').forEach(ch => {
        ch.addEventListener('click', () => {
            const days = parseInt(ch.getAttribute('data-range'), 10) || 30;
            const end = new Date();
            const start = new Date();
            start.setDate(end.getDate() - (days - 1));
            const s = document.getElementById('report-start-date');
            const e = document.getElementById('report-end-date');
            if (s) s.value = formatDateInput(start);
            if (e) e.value = formatDateInput(end);
            invalidateGeneratedReport();
        });
    });
    const printBtn = document.getElementById('btn-print-report');
    if (printBtn) printBtn.addEventListener('click', () => window.print());
    const clock = document.getElementById('foot-clock');
    if (clock) {
        const tick = () => {
            clock.textContent = new Date().toLocaleString(locale(), { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
        };
        tick();
        setInterval(tick, 30000);
        document.addEventListener('languagechange', tick);
    }
    window.addEventListener('resize', () => {
        const c = document.getElementById('report-chart');
        if (c && !document.getElementById('report-results').classList.contains('d-none')) {
            const last = window.__lastReport;
            if (last) drawReportChart(last.transfers || []);
        }
    });

    // Shake invalid forms for tactile feedback
    document.querySelectorAll('form').forEach(f => {
        f.addEventListener('submit', () => {
            if (!f.checkValidity()) {
                f.classList.remove('shake');
                void f.offsetWidth;
                f.classList.add('shake');
            }
        });
    });

    // Wrap renderReportResults to cache last report for chart resize
    const _render = renderReportResults;
    renderReportResults = function (report) {
        window.__lastReport = report;
        _render(report);
    };

    // Connectivity banner: fail-fast in fetchApi is not enough UX on its own.
    window.addEventListener('offline', () => showAlert(__('general.offline'), 'warning'));
    window.addEventListener('online', () => showAlert(__('general.back_online')));

    // Backend liveness (GET /actuator/health: public, not rate-limited).
    // Only the dot color + footer label change; i18n-managed texts untouched.
    window.__healthOk = true;
    const applyHealth = (ok) => {
        window.__healthOk = ok;
        document.querySelectorAll('.pulse-dot, .live-dot').forEach(d => {
            d.style.background = ok ? 'var(--success)' : 'var(--warning)';
        });
        const label = document.getElementById('health-status');
        if (label) {
            label.textContent = ok ? __('health.up') : __('health.down');
            label.classList.toggle('health-ok', ok);
            label.classList.toggle('health-bad', !ok);
        }
    };
    const checkBackendHealth = async () => {
        try {
            const ctrl = (typeof AbortController !== 'undefined') ? new AbortController() : null;
            const t = ctrl ? setTimeout(() => ctrl.abort(), 8000) : null;
            const res = await fetch('/actuator/health', {
                headers: { 'Accept': 'application/json' },
                ...(ctrl ? { signal: ctrl.signal } : {})
            });
            if (t) clearTimeout(t);
            let up = res.ok;
            try {
                const j = await res.json();
                up = res.ok && !!j && j.status === 'UP';
            } catch (e) { /* keep res.ok */ }
            applyHealth(up);
        } catch (e) {
            applyHealth(false);
        }
    };
    checkBackendHealth();
    setInterval(checkBackendHealth, 60000);
    document.addEventListener('languagechange', () => {
        const label = document.getElementById('health-status');
        if (label) {
            label.textContent = window.__healthOk ? __('health.up') : __('health.down');
        }
    });
}
