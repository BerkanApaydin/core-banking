// --- Configuration ---
const API_BASE = '/api/v1';

// --- State Management ---
let accounts = [];
let activeTab = 'accounts-section';
let token = localStorage.getItem('token') || null;
let userId = localStorage.getItem('userId') || null;
let username = localStorage.getItem('username') || null;
let transferIdempotencyKey = crypto.randomUUID();

// --- DOM Elements ---
const navItems = document.querySelectorAll('.nav-item');
const tabContents = document.querySelectorAll('.tab-content');
const alertContainer = document.getElementById('alert-container');

// --- Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    initNavigation();
    initModal();
    initTransferForm();
    initReportSection();
    initAuth();
    checkAuthStatus();
});

// --- Alert System ---
function showAlert(message, type = 'success') {
    const alert = document.createElement('div');
    alert.className = `alert alert-${type}`;

    alert.innerHTML = `
        <span>${escapeHtml(message)}</span>
        <button class="alert-close">&times;</button>
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
        } else {
            item.classList.remove('active');
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
async function fetchApi(endpoint, options = {}) {
    try {
        const headers = {
            'Content-Type': 'application/json',
            ...options.headers
        };
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }
        const response = await fetch(`${API_BASE}${endpoint}`, {
            ...options,
            headers
        });

        if (response.status === 401) {
            logout();
            throw new Error('Your session has expired, please log in again.');
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
            // Handle error messages from GlobalExceptionHandler
            throw new Error((data && data.message) ? data.message : 'An error occurred.');
        }

        return data;
    } catch (error) {
        console.error('API Error:', error);
        throw error;
    }
}

// --- Accounts Operations ---
async function loadAccounts() {
    const listElement = document.getElementById('accounts-list');

    try {
        const response = await fetchApi('/accounts');
        accounts = response.content || response;
        listElement.innerHTML = '';

        if (accounts.length === 0) {
            listElement.innerHTML = `
                <div class="empty-state" style="grid-column: 1 / -1;">
                    <span class="empty-icon">💳</span>
                    <p>No accounts found. You can start by opening a new account.</p>
                </div>
            `;
            return;
        }

        accounts.forEach(acc => {
            const card = document.createElement('div');
            card.className = `card ${acc.active ? 'card-active' : 'card-inactive'}`;

            card.innerHTML = `
                <div class="card-header">
                    <div class="card-bank-info">
                        <span class="card-bank-name">X BANK RETAIL</span>
                        <span class="card-owner">${escapeHtml(acc.ownerName)}</span>
                    </div>
                    <div class="card-chip"></div>
                </div>
                <div class="card-body">
                    <span class="card-balance-label">Available Balance</span>
                    <div class="card-balance">
                        <span>${formatMoney(acc.balance)}</span>
                        <span class="card-currency">${escapeHtml(acc.currency)}</span>
                    </div>
                </div>
                <div class="card-footer">
                    <span class="card-iban">${escapeHtml(formatIbanDisplay(acc.iban))}</span>
                    <span class="card-status-badge ${acc.active ? 'badge-active' : 'badge-inactive'}">
                        ${acc.active ? 'Active' : 'Inactive'}
                    </span>
                </div>
            `;

            // Add click action to view history
            card.addEventListener('click', () => {
                switchTab('reports-section');
                const select = document.getElementById('report-account-select');
                select.value = acc.id;
                select.dispatchEvent(new Event('change'));
            });

            listElement.appendChild(card);
        });
    } catch (err) {
        showAlert(err.message, 'danger');
        listElement.innerHTML = `
            <div class="empty-state" style="grid-column: 1 / -1; color: var(--danger);">
                <span class="empty-icon">⚠️</span>
                <p>Account information could not be loaded. Please refresh the page.</p>
            </div>
        `;
    }
}

// --- Account Creation Modal ---
function initModal() {
    const modal = document.getElementById('create-account-modal');
    const openBtn = document.getElementById('open-create-modal');
    const closeBtn = document.getElementById('close-create-modal');
    const cancelBtn = document.getElementById('btn-cancel-create');
    const form = document.getElementById('create-account-form');
    const spinner = document.getElementById('btn-account-spinner');
    const submitBtn = document.getElementById('btn-submit-account');

    function openModal() {
        modal.classList.add('active');
        form.reset();
    }

    function closeModal() {
        modal.classList.remove('active');
    }

    openBtn.addEventListener('click', openModal);
    closeBtn.addEventListener('click', closeModal);
    cancelBtn.addEventListener('click', closeModal);

    // Close modal if clicked overlay
    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });

    // Format IBAN input dynamically
    const ibanInput = document.getElementById('acc-iban');
    ibanInput.addEventListener('input', (e) => {
        let val = e.target.value.toUpperCase().replace(/\s+/g, '');
        if (val && !val.startsWith('TR')) {
            val = 'TR' + val.replace(/[^0-9]/g, '');
        } else if (val) {
            val = 'TR' + val.substring(2).replace(/[^0-9]/g, '');
        }
        e.target.value = val;
    });

    // Handle Form Submit
    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        const ownerName = document.getElementById('acc-owner-name').value.trim();
        const iban = document.getElementById('acc-iban').value.trim();
        const balance = parseFloat(document.getElementById('acc-balance').value);
        const currency = document.getElementById('acc-currency').value;

        if (iban.length !== 26) {
            showAlert('IBAN number must be exactly 26 characters.', 'danger');
            return;
        }

        spinner.classList.remove('d-none');
        submitBtn.disabled = true;

        try {
            await fetchApi('/accounts', {
                method: 'POST',
                body: JSON.stringify({ userId: parseInt(userId), iban, ownerName, initialBalance: balance, currency })
            });

            showAlert('Your account has been successfully created.');
            closeModal();
            loadAccounts();
        } catch (err) {
            showAlert(err.message, 'danger');
        } finally {
            spinner.classList.add('d-none');
            submitBtn.disabled = false;
        }
    });
}

// --- Transfer Page Operations ---
function populateTransferDropdowns() {
    const senderSelect = document.getElementById('sender-account-select');
    const receiverSelect = document.getElementById('receiver-account-select');
    const currencySelect = document.getElementById('transfer-currency');
    const balanceIndicator = document.getElementById('sender-balance-indicator');

    const prevSender = senderSelect.value;
    const prevReceiver = receiverSelect.value;

    senderSelect.innerHTML = '<option value="" disabled selected>Select sender</option>';
    receiverSelect.innerHTML = '<option value="" disabled selected>Select recipient</option>';

    const activeAccounts = accounts.filter(a => a.active);

    activeAccounts.forEach(acc => {
        const optionContent = `${acc.ownerName} - ${formatIbanDisplay(acc.iban)} (${formatMoney(acc.balance)} ${acc.currency})`;

        const optSender = document.createElement('option');
        optSender.value = acc.id;
        optSender.textContent = optionContent;
        senderSelect.appendChild(optSender);

        const optReceiver = document.createElement('option');
        optReceiver.value = acc.id;
        optReceiver.textContent = optionContent;
        receiverSelect.appendChild(optReceiver);
    });

    // Restore previous selection if still valid
    if (prevSender && activeAccounts.some(a => a.id == prevSender)) {
        senderSelect.value = prevSender;
        updateSenderBalance();
    } else {
        balanceIndicator.textContent = '';
    }

    if (prevReceiver && activeAccounts.some(a => a.id == prevReceiver)) {
        receiverSelect.value = prevReceiver;
    }
}

function updateSenderBalance() {
    const select = document.getElementById('sender-account-select');
    const indicator = document.getElementById('sender-balance-indicator');
    const currencySelect = document.getElementById('transfer-currency');

    const selectedAcc = accounts.find(a => a.id == select.value);
    if (selectedAcc) {
        indicator.textContent = `Limit: ${formatMoney(selectedAcc.balance)} ${selectedAcc.currency}`;
        currencySelect.value = selectedAcc.currency;
    } else {
        indicator.textContent = '';
    }
}

function initTransferForm() {
    const form = document.getElementById('transfer-form');
    const senderSelect = document.getElementById('sender-account-select');
    const receiverSelect = document.getElementById('receiver-account-select');
    const receiverTypeRadios = document.querySelectorAll('input[name="receiver-type"]');
    const registeredGroup = document.getElementById('receiver-registered-group');
    const manualGroup = document.getElementById('receiver-manual-group');
    const manualIbanInput = document.getElementById('receiver-iban-input');
    const swapBtn = document.getElementById('btn-swap-accounts');
    const spinner = document.getElementById('btn-transfer-spinner');
    const submitBtn = document.getElementById('btn-submit-transfer');

    senderSelect.addEventListener('change', updateSenderBalance);

    // Toggle registered vs manual receiver input
    receiverTypeRadios.forEach(radio => {
        radio.addEventListener('change', (e) => {
            if (e.target.value === 'registered') {
                registeredGroup.classList.remove('d-none');
                manualGroup.classList.add('d-none');
                receiverSelect.required = true;
                manualIbanInput.required = false;
            } else {
                registeredGroup.classList.add('d-none');
                manualGroup.classList.remove('d-none');
                receiverSelect.required = false;
                manualIbanInput.required = true;
            }
        });
    });

    // Format manual IBAN field
    manualIbanInput.addEventListener('input', (e) => {
        let val = e.target.value.toUpperCase().replace(/\s+/g, '');
        if (val && !val.startsWith('TR')) {
            val = 'TR' + val.replace(/[^0-9]/g, '');
        } else if (val) {
            val = 'TR' + val.substring(2).replace(/[^0-9]/g, '');
        }
        e.target.value = val;
    });

    // Swap button click handler
    swapBtn.addEventListener('click', () => {
        const temp = senderSelect.value;
        const receiverType = document.querySelector('input[name="receiver-type"]:checked').value;

        if (receiverType === 'registered' && receiverSelect.value) {
            senderSelect.value = receiverSelect.value;
            receiverSelect.value = temp;
            updateSenderBalance();
        } else {
            showAlert('You can only swap when two registered accounts are selected.', 'warning');
        }
    });

    // Form submission
    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        const senderId = senderSelect.value;
        const receiverType = document.querySelector('input[name="receiver-type"]:checked').value;
        const amount = parseFloat(document.getElementById('transfer-amount').value);
        const currency = document.getElementById('transfer-currency').value;

        const senderAcc = accounts.find(a => a.id == senderId);
        if (!senderAcc) {
            showAlert('Please select a sender account.', 'danger');
            return;
        }

        if (amount > senderAcc.balance) {
            showAlert('Sender account balance is insufficient for this transaction.', 'danger');
            return;
        }

        let receiverIban = '';
        if (receiverType === 'registered') {
            const receiverId = receiverSelect.value;
            const receiverAcc = accounts.find(a => a.id == receiverId);
            if (!receiverAcc) {
                showAlert('Please select a recipient account.', 'danger');
                return;
            }
            if (senderId === receiverId) {
                showAlert('Cannot transfer to the same account.', 'danger');
                return;
            }
            if (senderAcc.currency !== receiverAcc.currency) {
                showAlert('Cannot transfer between accounts with mismatched currencies.', 'danger');
                return;
            }
            receiverIban = receiverAcc.iban;
        } else {
            receiverIban = manualIbanInput.value.trim();
            if (receiverIban.length !== 26) {
                showAlert('Enter a valid 26-digit recipient IBAN.', 'danger');
                return;
            }
            if (senderAcc.iban === receiverIban) {
                showAlert('Cannot transfer to the sender account.', 'danger');
                return;
            }
        }

        spinner.classList.remove('d-none');
        submitBtn.disabled = true;

        try {
            const result = await fetchApi('/transfers', {
                method: 'POST',
                headers: {
                    'Idempotency-Key': transferIdempotencyKey
                },
                body: JSON.stringify({
                    senderIban: senderAcc.iban,
                    receiverIban: receiverIban,
                    amount: amount,
                    currency: currency
                })
            });

            showAlert(`Transfer completed successfully! (Amount: ${formatMoney(result.amount)} ${escapeHtml(result.currency)})`);
            form.reset();
            transferIdempotencyKey = crypto.randomUUID();
            document.getElementById('sender-balance-indicator').textContent = '';

            // Reload accounts in background
            await loadAccounts();
        } catch (err) {
            showAlert(err.message, 'danger');
        } finally {
            spinner.classList.add('d-none');
            submitBtn.disabled = false;
        }
    });
}

// --- Reports & History Section ---
function populateReportDropdown() {
    const reportSelect = document.getElementById('report-account-select');
    const prevVal = reportSelect.value;

    reportSelect.innerHTML = '<option value="" disabled selected>Select account</option>';

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

    // Set default dates for report filters (last 30 days)
    const now = new Date();
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(now.getDate() - 30);

    document.getElementById('report-start-date').value = formatDateTimeLocal(thirtyDaysAgo);
    document.getElementById('report-end-date').value = formatDateTimeLocal(now);

    // Dropdown change triggers history load
    reportSelect.addEventListener('change', (e) => {
        loadAccountHistory(e.target.value);
        document.getElementById('report-results').classList.add('d-none');
    });

    // Tab buttons toggle
    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            tabBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            const target = btn.getAttribute('data-tab');
            tabContents.forEach(content => {
                if (content.id === target) {
                    content.classList.add('active');
                } else {
                    content.classList.remove('active');
                }
            });
        });
    });

    // Report Generation Form submit
    reportFilterForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const accountId = reportSelect.value;
        const startDate = document.getElementById('report-start-date').value;
        const endDate = document.getElementById('report-end-date').value;

        if (!accountId) {
            showAlert('Please select an account first.', 'warning');
            return;
        }

        try {
            const report = await fetchApi(`/transfers/report?accountId=${accountId}&startDate=${startDate}&endDate=${endDate}`);
            renderReportResults(report);
        } catch (err) {
            showAlert(err.message, 'danger');
        }
    });
}

async function loadAccountHistory(accountId) {
    const historyList = document.getElementById('transaction-history-list');
    historyList.innerHTML = '<div class="empty-state"><span class="spinner"></span><p>Loading transactions...</p></div>';

    const selectedAcc = accounts.find(a => a.id == accountId);
    if (!selectedAcc) return;

    try {
        const response = await fetchApi(`/transfers/history/${accountId}`);
        const transfers = response.items || [];
        historyList.innerHTML = '';

        if (transfers.length === 0) {
            historyList.innerHTML = `
                <div class="empty-state">
                    <span class="empty-icon">🔍</span>
                    <p>No transaction history found for this account.</p>
                </div>
            `;
            return;
        }

        transfers.forEach(t => {
            const isOutgoing = t.senderIban === selectedAcc.iban;
            const isCancelled = t.status === 'CANCELLED';

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

            // Cancellation eligibility (within 24 hours and completed)
            const isEligibleForCancel = !isCancelled && isOutgoing && (new Date() - new Date(t.createdAt)) < 24 * 60 * 60 * 1000;

            item.innerHTML = `
                <div class="history-details">
                    <div class="history-badge ${badgeClass}">${badgeIcon}</div>
                    <div class="history-meta">
                        <span class="history-title">
                            ${isCancelled ? '[CANCELLED] ' : ''}
                            ${isOutgoing ? `Transfer: Recipient IBAN (${escapeHtml(formatIbanDisplay(t.receiverIban))})` : `Incoming Transfer: Sender IBAN (${escapeHtml(formatIbanDisplay(t.senderIban))})`}
                        </span>
                        <span class="history-sub">${formatDate(t.createdAt)}</span>
                    </div>
                </div>
                <div class="history-right">
                    <span class="history-amount ${amountClass}">${prefix}${formatMoney(t.amount)} ${escapeHtml(t.currency)}</span>
                    ${isEligibleForCancel ? `<button class="btn-cancel-transfer" onclick="cancelTransfer(${t.id}, ${accountId})">Cancel</button>` : ''}
                </div>
            `;

            historyList.appendChild(item);
        });
    } catch (err) {
        showAlert(err.message, 'danger');
        historyList.innerHTML = `
            <div class="empty-state" style="color: var(--danger);">
                <span class="empty-icon">⚠️</span>
                <p>Account history could not be loaded.</p>
            </div>
        `;
    }
}

// Exposed to global window scope so it can be called from dynamic HTML
window.cancelTransfer = async function (transferId, accountId) {
    if (!confirm('Are you sure you want to cancel this transfer and refund the money?')) {
        return;
    }

    try {
        await fetchApi(`/transfers/${transferId}/cancel`, { method: 'POST' });
        showAlert('Transfer successfully cancelled and balances updated.');

        // Reload all data
        await loadAccounts();
        loadAccountHistory(accountId);
    } catch (err) {
        showAlert(err.message, 'danger');
    }
};

function renderReportResults(report) {
    const resultsContainer = document.getElementById('report-results');
    const countEl = document.getElementById('report-stat-count');
    const volumeEl = document.getElementById('report-stat-volume');
    const tableBody = document.getElementById('report-table-body');

    countEl.textContent = report.totalTransfersCount;
    volumeEl.textContent = `${formatMoney(report.totalVolume)} ${report.currency}`;
    tableBody.innerHTML = '';

    if (report.transfers.length === 0) {
        tableBody.innerHTML = `
            <tr>
                <td colspan="4" class="text-muted" style="text-align: center; padding: 2rem;">
                    No transactions found in the selected date range.
                </td>
            </tr>
        `;
    } else {
        report.transfers.forEach(t => {
            const tr = document.createElement('tr');

            const isCancelled = t.status === 'CANCELLED';
            const statusBadge = `<span class="card-status-badge ${isCancelled ? 'badge-inactive' : 'badge-active'}">
                ${isCancelled ? 'CANCELLED' : 'COMPLETED'}
            </span>`;

            tr.innerHTML = `
                <td>${formatDate(t.createdAt)}</td>
                <td>Sender ID: ${t.senderAccountId} &rarr; Recipient ID: ${t.receiverAccountId}</td>
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
    return new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(amount);
}

function formatIbanDisplay(iban) {
    if (!iban) return '';
    return iban.replace(/(.{4})/g, '$1 ').trim();
}

function formatDate(dateString) {
    const d = new Date(dateString);
    return d.toLocaleString('en-US', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function formatDateTimeLocal(date) {
    const pad = (n) => n.toString().padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

// --- Authentication Operations ---
function checkAuthStatus() {
    const authContainer = document.getElementById('auth-container');
    const appContainer = document.getElementById('app-container');
    const displayUsername = document.getElementById('display-username');

    if (token) {
        authContainer.classList.add('d-none');
        appContainer.classList.remove('d-none');
        displayUsername.textContent = username;
        switchTab('accounts-section');
    } else {
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
        tabRegisterBtn.classList.remove('active');
        loginForm.classList.remove('d-none');
        registerForm.classList.add('d-none');
    });

    tabRegisterBtn.addEventListener('click', () => {
        tabRegisterBtn.classList.add('active');
        tabLoginBtn.classList.remove('active');
        registerForm.classList.remove('d-none');
        loginForm.classList.add('d-none');
    });

    // Login Form Submit
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const usernameVal = document.getElementById('login-username').value.trim();
        const passwordVal = document.getElementById('login-password').value;

        loginSpinner.classList.remove('d-none');
        btnLoginSubmit.disabled = true;

        try {
            const result = await fetchApi('/auth/login', {
                method: 'POST',
                body: JSON.stringify({ username: usernameVal, password: passwordVal })
            });

            token = result.token;
            userId = result.userId;
            username = result.username;

            localStorage.setItem('token', token);
            localStorage.setItem('userId', userId);
            localStorage.setItem('username', username);

            showAlert('Login successful. Welcome!');
            checkAuthStatus();
        } catch (err) {
            showAlert('Login failed: ' + err.message, 'danger');
        } finally {
            loginSpinner.classList.add('d-none');
            btnLoginSubmit.disabled = false;
        }
    });

    // Register Form Submit
    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const usernameVal = document.getElementById('register-username').value.trim();
        const passwordVal = document.getElementById('register-password').value;

        registerSpinner.classList.remove('d-none');
        btnRegisterSubmit.disabled = true;

        try {
            await fetchApi('/auth/register', {
                method: 'POST',
                body: JSON.stringify({ username: usernameVal, password: passwordVal })
            });

            showAlert('Registration completed successfully. You can now log in.');
            registerForm.reset();
            // Switch to login tab
            tabLoginBtn.click();
        } catch (err) {
            showAlert('Registration failed: ' + err.message, 'danger');
        } finally {
            registerSpinner.classList.add('d-none');
            btnRegisterSubmit.disabled = false;
        }
    });

    // Logout Click
    btnLogout.addEventListener('click', () => {
        logout();
        showAlert('Logged out successfully.');
    });
}

function logout() {
    token = null;
    userId = null;
    username = null;
    accounts = [];
    localStorage.removeItem('token');
    localStorage.removeItem('userId');
    localStorage.removeItem('username');

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
    document.getElementById('sender-account-select').innerHTML = '<option value="" disabled selected>Select sender</option>';
    document.getElementById('receiver-account-select').innerHTML = '<option value="" disabled selected>Select recipient</option>';
    document.getElementById('report-account-select').innerHTML = '<option value="" disabled selected>Select account</option>';

    // Reset active tab variable
    activeTab = 'accounts-section';

    checkAuthStatus();
}
