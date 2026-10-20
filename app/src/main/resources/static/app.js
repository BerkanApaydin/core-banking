// --- Configuration ---
/* exported API_BASE, accountPager, activeTab, buildLoginBody, cancellationsInFlight, fundingMode, historyCache, historyPager, meetsPasswordPolicy, reportPager, userId */
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
let reportPager = { criteria: null, page: 0, hasNext: false, loading: false, requestVersion: 0, cursors: [null] };
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
    initSecurity();
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

// --- Security (MFA + password reset) ---
// TOTP codes are 6-8 ASCII digits; anything else never reaches the server
// (the backend @Pattern mirrors this, this is only a client courtesy).
function isTotpLike(value) {
    return typeof value === 'string' && /^\d{6,8}$/.test(value.trim());
}

// Audit (MFA): the authenticator code rides along only when the user typed
// a well-formed one — non-enrolled logins send no totpCode at all.
function buildLoginBody(username, password, totp) {
    const body = { username, password };
    if (isTotpLike(totp)) {
        body.totpCode = totp.trim();
    }
    return body;
}

async function refreshMfaStatus() {
    const pill = document.getElementById('mfa-status-pill');
    const enableBtn = document.getElementById('btn-mfa-enable');
    const enablePasswordGroup = document.getElementById('mfa-enable-password-group');
    const enrollBox = document.getElementById('mfa-enroll-box');
    const disableForm = document.getElementById('mfa-disable-form');
    if (!pill) return;
    try {
        const status = await fetchApi('/auth/mfa/status');
        const on = status && status.mfaEnabled === true;
        pill.textContent = on ? __('mfa.on') : __('mfa.off');
        pill.classList.toggle('on', on);
        pill.classList.toggle('off', !on);
        if (enableBtn) enableBtn.classList.toggle('d-none', on);
        if (enablePasswordGroup) enablePasswordGroup.classList.toggle('d-none', on);
        if (enrollBox && on) enrollBox.classList.add('d-none');
        if (disableForm) disableForm.classList.toggle('d-none', !on);
    } catch (err) {
        // Status is informational: a failed read must not break the section.
        // Leave the enable path visible (server re-validates everything).
        if (enableBtn) enableBtn.classList.remove('d-none');
    }
}

function initSecurity() {
    // --- Password-reset panel (unauthenticated, on the login tab) ---
    const loginForm = document.getElementById('login-form');
    const showReset = document.getElementById('btn-show-reset');
    const requestForm = document.getElementById('reset-request-form');
    const confirmForm = document.getElementById('reset-confirm-form');
    const showLoginView = () => {
        if (loginForm) loginForm.classList.remove('d-none');
        if (requestForm) requestForm.classList.add('d-none');
        if (confirmForm) confirmForm.classList.add('d-none');
    };
    if (showReset && requestForm) {
        showReset.addEventListener('click', () => {
            if (loginForm) loginForm.classList.add('d-none');
            requestForm.classList.remove('d-none');
            if (confirmForm) confirmForm.classList.add('d-none');
        });
    }
    ['btn-hide-reset', 'btn-hide-reset-2'].forEach(id => {
        const btn = document.getElementById(id);
        if (btn) btn.addEventListener('click', showLoginView);
    });
    if (requestForm) {
        requestForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const username = document.getElementById('reset-username').value.trim();
            if (!username) return;
            const spinner = document.getElementById('reset-request-spinner');
            if (spinner) spinner.classList.remove('d-none');
            try {
                await fetchApi('/auth/password/reset/request', {
                    method: 'POST',
                    body: JSON.stringify({ username })
                });
                // Always the same message (enumeration-safe by API contract).
                showAlert(__('reset.request_sent'));
                requestForm.classList.add('d-none');
                if (confirmForm) confirmForm.classList.remove('d-none');
            } catch (err) {
                showAlert(err.message, 'danger');
            } finally {
                if (spinner) spinner.classList.add('d-none');
            }
        });
    }
    if (confirmForm) {
        confirmForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const token = document.getElementById('reset-token').value.trim();
            const newPassword = document.getElementById('reset-new-password').value;
            if (!token || !newPassword) return;
            const spinner = document.getElementById('reset-confirm-spinner');
            if (spinner) spinner.classList.remove('d-none');
            try {
                await fetchApi('/auth/password/reset/confirm', {
                    method: 'POST',
                    body: JSON.stringify({ token, newPassword })
                });
                showAlert(__('reset.confirm_success'));
                confirmForm.reset();
                showLoginView();
            } catch (err) {
                showAlert(err.message, 'danger');
            } finally {
                if (spinner) spinner.classList.add('d-none');
            }
        });
    }

    // --- MFA enrollment (authenticated) ---
    const enableBtn = document.getElementById('btn-mfa-enable');
    const enablePasswordGroup = document.getElementById('mfa-enable-password-group');
    const enablePassword = document.getElementById('mfa-enable-password');
    const enrollBox = document.getElementById('mfa-enroll-box');
    const secretText = document.getElementById('mfa-secret-text');
    const copyBtn = document.getElementById('btn-mfa-copy');
    const confirmMfaForm = document.getElementById('mfa-confirm-form');
    const disableForm = document.getElementById('mfa-disable-form');
    if (enableBtn && enrollBox) {
        enableBtn.addEventListener('click', async () => {
            const password = enablePassword ? enablePassword.value : '';
            if (!password) {
                showAlert(__('auth.password.required'), 'danger');
                if (enablePassword) enablePassword.focus();
                return;
            }
            try {
                const enrollment = await fetchApi('/auth/mfa/enable', {
                    method: 'POST',
                    body: JSON.stringify({ password })
                });
                if (enablePassword) enablePassword.value = '';
                if (enablePasswordGroup) enablePasswordGroup.classList.add('d-none');
                if (secretText) secretText.textContent = enrollment.base32Secret || '';
                enrollBox.classList.remove('d-none');
                enableBtn.classList.add('d-none');
            } catch (err) {
                showAlert(err.message, 'danger');
            }
        });
    }
    if (copyBtn && secretText) {
        copyBtn.addEventListener('click', async () => {
            try {
                await navigator.clipboard.writeText(secretText.textContent || '');
                showAlert(__('mfa.copied'));
            } catch (err) {
                showAlert(err.message, 'danger');
            }
        });
    }
    if (confirmMfaForm) {
        confirmMfaForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const password = document.getElementById('mfa-confirm-password').value;
            const code = document.getElementById('mfa-confirm-code').value.trim();
            if (!password) {
                showAlert(__('auth.password.required'), 'danger');
                return;
            }
            if (!isTotpLike(code)) return;
            const spinner = document.getElementById('mfa-confirm-spinner');
            if (spinner) spinner.classList.remove('d-none');
            try {
                await fetchApi('/auth/mfa/confirm', {
                    method: 'POST',
                    body: JSON.stringify({ password, code })
                });
                confirmMfaForm.reset();
                showAlert(__('mfa.confirm_success'));
                enrollBox.classList.add('d-none');
                await refreshMfaStatus();
            } catch (err) {
                showAlert(err.message, 'danger');
            } finally {
                if (spinner) spinner.classList.add('d-none');
            }
        });
    }
    if (disableForm) {
        disableForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const password = document.getElementById('mfa-disable-password').value;
            const code = document.getElementById('mfa-disable-code').value.trim();
            if (!password || !isTotpLike(code)) return;
            const spinner = document.getElementById('mfa-disable-spinner');
            if (spinner) spinner.classList.remove('d-none');
            try {
                await fetchApi('/auth/mfa/disable', {
                    method: 'POST',
                    body: JSON.stringify({ password, code })
                });
                showAlert(__('mfa.disable_success'));
                disableForm.reset();
                await refreshMfaStatus();
            } catch (err) {
                showAlert(err.message, 'danger');
            } finally {
                if (spinner) spinner.classList.add('d-none');
            }
        });
    }
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
    } else if (tabId === 'security-section') {
        refreshMfaStatus();
    }
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
        const d = parseApiTimestamp(t.createdAt);
        if (!d) return;
        const k = `${d.getUTCFullYear()}-${d.getUTCMonth()}-${d.getUTCDate()}`;
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
            const start = new Date(end.getTime());
            start.setUTCDate(end.getUTCDate() - (days - 1));
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
