/* exported initAuth */
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
        const totpVal = document.getElementById('login-totp').value.trim();
        if (!usernameVal) {
            showAlert(__('auth.username.required'), 'warning');
            return;
        }

        loginSpinner.classList.remove('d-none');
        btnLoginSubmit.disabled = true;
        btnLoginSubmit.setAttribute('aria-busy', 'true');

        try {
            // Audit (MFA): the authenticator code rides along only when the
            // user typed one — non-enrolled logins send no totpCode at all.
            const result = await fetchApi('/auth/browser/login', {
                method: 'POST',
                body: JSON.stringify(buildLoginBody(usernameVal, passwordVal, totpVal))
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

