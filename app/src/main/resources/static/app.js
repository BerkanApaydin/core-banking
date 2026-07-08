// --- Configuration ---
const API_BASE = '/api/v1';

// --- i18n Translations ---
const translations = {
    en: {
        'app.title': 'X Bank - Digital Banking',
        'welcome': 'Welcome',
        'welcome.subtitle': 'Please log in to continue.',
        'auth.login': 'Login',
        'auth.register': 'Register',
        'auth.username': 'Username',
        'auth.password': 'Password',
        'auth.username.placeholder': 'Enter your username',
        'auth.password.placeholder': 'Enter your password',
        'auth.register.username.placeholder': 'Choose a new username',
        'auth.register.password.placeholder': 'Set a strong password',
        'auth.password.hint': 'Password must be at least 8 characters, contain at least one uppercase letter, one lowercase letter, and one digit.',
        'auth.register.submit': 'Create Account & Register',
        'auth.login.submit': 'Login',
        'auth.login.success': 'Login successful. Welcome!',
        'auth.login.failed': 'Login failed: ',
        'auth.register.success': 'Registration completed successfully. You can now log in.',
        'auth.register.failed': 'Registration failed: ',
        'auth.logout': 'Logout',
        'auth.logout.success': 'Logged out successfully.',
        'auth.session_expired': 'Your session has expired, please log in again.',
        'nav.accounts': 'My Accounts',
        'nav.transfer': 'Money Transfer',
        'nav.reports': 'Analytics & Report',
        'nav.user.role': 'Customer',
        'account.title': 'Account Management',
        'account.subtitle': 'View your active bank accounts and create new ones.',
        'account.open_new': 'Open New Account',
        'account.balance_label': 'Available Balance',
        'account.active': 'Active',
        'account.inactive': 'Inactive',
        'account.no_accounts': 'No accounts found. You can start by opening a new account.',
        'account.load_error': 'Account information could not be loaded. Please refresh the page.',
        'account.created': 'Your account has been successfully created.',
        'account.iban_hint': 'Enter a 26-character IBAN starting with "TR".',
        'account.iban_must_be_26': 'IBAN number must be exactly 26 characters.',
        'transfer.title': 'Money Transfer',
        'transfer.subtitle': 'Transfer securely and instantly between your accounts or to other IBANs.',
        'transfer.sender_label': 'Sender Account',
        'transfer.sender_placeholder': 'Select sender',
        'transfer.recipient_type': 'Recipient Account Type',
        'transfer.recipient_registered': 'Registered Account',
        'transfer.recipient_manual': 'Enter Manual IBAN',
        'transfer.recipient_label': 'Recipient Account',
        'transfer.recipient_placeholder': 'Select recipient',
        'transfer.recipient_iban': 'Recipient IBAN',
        'transfer.amount': 'Amount',
        'transfer.currency': 'Currency',
        'transfer.submit': 'Complete Transfer',
        'transfer.swap_title': 'Swap Accounts',
        'transfer.swap_warning': 'You can only swap when two registered accounts are selected.',
        'transfer.select_sender': 'Please select a sender account.',
        'transfer.insufficient_balance': 'Sender account balance is insufficient for this transaction.',
        'transfer.select_recipient': 'Please select a recipient account.',
        'transfer.cannot_same': 'Cannot transfer to the same account.',
        'transfer.currency_mismatch': 'Cannot transfer between accounts with mismatched currencies.',
        'transfer.valid_iban': 'Enter a valid 26-digit recipient IBAN.',
        'transfer.cannot_sender': 'Cannot transfer to the sender account.',
        'transfer.success': 'Transfer completed successfully!',
        'transfer.limit': 'Limit',
        'transfer.info_title': 'Transaction Information',
        'transfer.info_instant': 'Your transfers are processed instantly 7/24 via FAST infrastructure.',
        'transfer.info_cancel': 'Per our security standards, transfers made within the last 24 hours can be cancelled.',
        'transfer.info_currency': 'Inter-account transfers require matching currencies.',
        'transfer.cancel_confirm': 'Are you sure you want to cancel this transfer and refund the money?',
        'transfer.cancelled': 'Transfer successfully cancelled and balances updated.',
        'transfer.cancelled_prefix': '[CANCELLED] ',
        'transfer.outgoing': 'Transfer: Recipient IBAN ({0})',
        'transfer.incoming': 'Incoming Transfer: Sender IBAN ({0})',
        'transfer.cancel_btn': 'Cancel',
        'report.title': 'Analytics & Account Details',
        'report.subtitle': 'Review your account activity and generate detailed reports for specific date ranges.',
        'report.account_label': 'Account to Review',
        'report.account_placeholder': 'Select account',
        'report.history_tab': 'Account History',
        'report.generator_tab': 'Report Generator',
        'report.history_title': 'Recent Account History',
        'report.history_empty': 'Select an account from the left to view transaction history.',
        'report.generator_title': 'Date-Based Report Generation',
        'report.start_date': 'Start Date',
        'report.end_date': 'End Date',
        'report.generate': 'Generate Report',
        'report.select_account_first': 'Please select an account first.',
        'report.stat_count': 'Total Transactions',
        'report.stat_volume': 'Total Volume',
        'report.no_transactions': 'No transactions found in the selected date range.',
        'report.table_date': 'Date',
        'report.table_description': 'Description',
        'report.table_status': 'Status',
        'report.table_amount': 'Amount',
        'report.status_completed': 'COMPLETED',
        'report.status_cancelled': 'CANCELLED',
        'report.history_none': 'No transaction history found for this account.',
        'report.history_loading': 'Loading transactions...',
        'report.load_error': 'Account history could not be loaded.',
        'modal.create_title': 'Create New Account',
        'modal.owner_name': 'Account Holder Name',
        'modal.owner_placeholder': 'e.g. John Doe',
        'modal.iban': 'IBAN Number',
        'modal.iban_placeholder': 'TR000000000000000000000000',
        'modal.balance': 'Initial Balance',
        'modal.currency': 'Currency',
        'modal.cancel': 'Cancel',
        'modal.create': 'Create Account',
        'general.error': 'An error occurred.',
        'general.loading': 'Loading...',
    },
    tr: {
        'app.title': 'X Bank - Dijital Bankacılık',
        'welcome': 'Hoş Geldiniz',
        'welcome.subtitle': 'Devam etmek için lütfen giriş yapın.',
        'auth.login': 'Giriş Yap',
        'auth.register': 'Kayıt Ol',
        'auth.username': 'Kullanıcı Adı',
        'auth.password': 'Şifre',
        'auth.username.placeholder': 'Kullanıcı adınızı girin',
        'auth.password.placeholder': 'Şifrenizi girin',
        'auth.register.username.placeholder': 'Yeni bir kullanıcı adı seçin',
        'auth.register.password.placeholder': 'Güçlü bir şifre belirleyin',
        'auth.password.hint': 'Şifre en az 8 karakter olmalı, en az bir büyük harf, bir küçük harf ve bir rakam içermelidir.',
        'auth.register.submit': 'Hesap Oluştur & Kaydol',
        'auth.login.submit': 'Giriş Yap',
        'auth.login.success': 'Giriş başarılı. Hoş geldiniz!',
        'auth.login.failed': 'Giriş başarısız: ',
        'auth.register.success': 'Kayıt başarıyla tamamlandı. Şimdi giriş yapabilirsiniz.',
        'auth.register.failed': 'Kayıt başarısız: ',
        'auth.logout': 'Çıkış Yap',
        'auth.logout.success': 'Başarıyla çıkış yapıldı.',
        'auth.session_expired': 'Oturumunuz süresi doldu, lütfen tekrar giriş yapın.',
        'nav.accounts': 'Hesaplarım',
        'nav.transfer': 'Para Transferi',
        'nav.reports': 'Analitik & Rapor',
        'nav.user.role': 'Müşteri',
        'account.title': 'Hesap Yönetimi',
        'account.subtitle': 'Aktif banka hesaplarınızı görüntüleyin ve yeni hesap açın.',
        'account.open_new': 'Yeni Hesap Aç',
        'account.balance_label': 'Kullanılabilir Bakiye',
        'account.active': 'Aktif',
        'account.inactive': 'Pasif',
        'account.no_accounts': 'Hiç hesap bulunamadı. Yeni bir hesap açarak başlayabilirsiniz.',
        'account.load_error': 'Hesap bilgileri yüklenemedi. Lütfen sayfayı yenileyin.',
        'account.created': 'Hesabınız başarıyla oluşturuldu.',
        'account.iban_hint': '"TR" ile başlayan 26 karakterli bir IBAN girin.',
        'account.iban_must_be_26': 'IBAN numarası tam olarak 26 karakter olmalıdır.',
        'transfer.title': 'Para Transferi',
        'transfer.subtitle': 'Hesaplarınız arasında veya diğer IBAN\'lara güvenli ve anında transfer yapın.',
        'transfer.sender_label': 'Gönderen Hesap',
        'transfer.sender_placeholder': 'Göndereni seçin',
        'transfer.recipient_type': 'Alıcı Hesap Türü',
        'transfer.recipient_registered': 'Kayıtlı Hesap',
        'transfer.recipient_manual': 'Manuel IBAN Gir',
        'transfer.recipient_label': 'Alıcı Hesap',
        'transfer.recipient_placeholder': 'Alıcıyı seçin',
        'transfer.recipient_iban': 'Alıcı IBAN',
        'transfer.amount': 'Tutar',
        'transfer.currency': 'Para Birimi',
        'transfer.submit': 'Transferi Tamamla',
        'transfer.swap_title': 'Hesapları Değiştir',
        'transfer.swap_warning': 'Sadece iki kayıtlı hesap seçiliyken değişim yapabilirsiniz.',
        'transfer.select_sender': 'Lütfen bir gönderen hesabı seçin.',
        'transfer.insufficient_balance': 'Gönderen hesap bakiyesi bu işlem için yetersiz.',
        'transfer.select_recipient': 'Lütfen bir alıcı hesabı seçin.',
        'transfer.cannot_same': 'Aynı hesaba transfer yapılamaz.',
        'transfer.currency_mismatch': 'Para birimleri eşleşmeyen hesaplar arasında transfer yapılamaz.',
        'transfer.valid_iban': 'Geçerli 26 haneli bir alıcı IBAN\'ı girin.',
        'transfer.cannot_sender': 'Gönderen hesaba transfer yapılamaz.',
        'transfer.success': 'Transfer başarıyla tamamlandı!',
        'transfer.limit': 'Limit',
        'transfer.info_title': 'İşlem Bilgileri',
        'transfer.info_instant': 'Transferleriniz FAST altyapısı ile 7/24 anında işlenir.',
        'transfer.info_cancel': 'Güvenlik standartlarımız gereği son 24 saat içinde yapılan transferler iptal edilebilir.',
        'transfer.info_currency': 'Hesaplar arası transferlerde para birimleri eşleşmelidir.',
        'transfer.cancel_confirm': 'Bu transferi iptal edip parayı iade etmek istediğinize emin misiniz?',
        'transfer.cancelled': 'Transfer başarıyla iptal edildi ve bakiyeler güncellendi.',
        'transfer.cancelled_prefix': '[İPTAL EDİLDİ] ',
        'transfer.outgoing': 'Transfer: Alıcı IBAN ({0})',
        'transfer.incoming': 'Gelen Transfer: Gönderen IBAN ({0})',
        'transfer.cancel_btn': 'İptal Et',
        'report.title': 'Analitik & Hesap Detayları',
        'report.subtitle': 'Hesap aktivitelerinizi inceleyin ve belirli tarih aralıkları için detaylı raporlar oluşturun.',
        'report.account_label': 'İncelenecek Hesap',
        'report.account_placeholder': 'Hesap seçin',
        'report.history_tab': 'Hesap Geçmişi',
        'report.generator_tab': 'Rapor Oluşturucu',
        'report.history_title': 'Son Hesap Hareketleri',
        'report.history_empty': 'İşlem geçmişini görüntülemek için soldan bir hesap seçin.',
        'report.generator_title': 'Tarih Bazlı Rapor Oluşturma',
        'report.start_date': 'Başlangıç Tarihi',
        'report.end_date': 'Bitiş Tarihi',
        'report.generate': 'Rapor Oluştur',
        'report.select_account_first': 'Lütfen önce bir hesap seçin.',
        'report.stat_count': 'Toplam İşlem',
        'report.stat_volume': 'Toplam Hacim',
        'report.no_transactions': 'Seçilen tarih aralığında işlem bulunamadı.',
        'report.table_date': 'Tarih',
        'report.table_description': 'Açıklama',
        'report.table_status': 'Durum',
        'report.table_amount': 'Tutar',
        'report.status_completed': 'TAMAMLANDI',
        'report.status_cancelled': 'İPTAL EDİLDİ',
        'report.history_none': 'Bu hesap için işlem geçmişi bulunamadı.',
        'report.history_loading': 'İşlemler yükleniyor...',
        'report.load_error': 'Hesap geçmişi yüklenemedi.',
        'modal.create_title': 'Yeni Hesap Oluştur',
        'modal.owner_name': 'Hesap Sahibi Adı',
        'modal.owner_placeholder': 'Örn: Ahmet Yılmaz',
        'modal.iban': 'IBAN Numarası',
        'modal.iban_placeholder': 'TR000000000000000000000000',
        'modal.balance': 'Başlangıç Bakiyesi',
        'modal.currency': 'Para Birimi',
        'modal.cancel': 'İptal',
        'modal.create': 'Hesap Oluştur',
        'general.error': 'Bir hata oluştu.',
        'general.loading': 'Yükleniyor...',
    }
};

let currentLang = localStorage.getItem('lang') || 'en';

function __(key, ...args) {
    let text = (translations[currentLang] && translations[currentLang][key])
        || (translations['en'] && translations['en'][key])
        || key;
    if (args.length > 0) {
        args.forEach((arg, i) => {
            text = text.replace(`{${i}}`, arg);
        });
    }
    return text;
}

function setLanguage(lang) {
    currentLang = lang;
    localStorage.setItem('lang', lang);
    document.documentElement.lang = lang;
    translateStaticPage();
    document.dispatchEvent(new CustomEvent('languagechange', { detail: { lang } }));
}

function translateStaticPage() {
    document.title = __('app.title');
    document.querySelectorAll('[data-i18n]').forEach(el => {
        const key = el.getAttribute('data-i18n');
        if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
            el.placeholder = __(key);
        } else {
            el.textContent = __(key);
        }
    });
}

function getLanguage() {
    return currentLang;
}

// --- Idempotency Key Helpers ---
function getIdempotencyKey(keyName) {
    let key = localStorage.getItem(keyName);
    if (!key) {
        key = crypto.randomUUID();
        localStorage.setItem(keyName, key);
    }
    return key;
}

function renewIdempotencyKey(keyName) {
    const key = crypto.randomUUID();
    localStorage.setItem(keyName, key);
    return key;
}

function clearIdempotencyKey(keyName) {
    localStorage.removeItem(keyName);
}

// --- State Management ---
let accounts = [];
let activeTab = 'accounts-section';
let token = localStorage.getItem('token') || null;
let userId = localStorage.getItem('userId') || null;
let username = localStorage.getItem('username') || null;

// --- DOM Elements ---
const navItems = document.querySelectorAll('.nav-item');
const tabContents = document.querySelectorAll('.tab-content');
const alertContainer = document.getElementById('alert-container');

// --- Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    const savedLang = localStorage.getItem('lang');
    if (savedLang) {
        currentLang = savedLang;
        document.documentElement.lang = savedLang;
    }
    translateStaticPage();
    initLanguageSwitcher();
    initNavigation();
    initModal();
    initTransferForm();
    initReportSection();
    initAuth();
    checkAuthStatus();
});

function initLanguageSwitcher() {
    const switcher = document.getElementById('language-switcher');
    if (switcher) {
        switcher.value = currentLang;
        switcher.addEventListener('change', (e) => {
            setLanguage(e.target.value);
        });
    }
}

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
        headers['Accept-Language'] = getLanguage();
        const response = await fetch(`${API_BASE}${endpoint}`, {
            ...options,
            headers
        });

        if (response.status === 401) {
            logout();
            throw new Error(__('auth.session_expired'));
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
            throw new Error((data && data.message) ? data.message : __('general.error'));
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
                    <p>${__('account.no_accounts')}</p>
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
                    <span class="card-balance-label">${__('account.balance_label')}</span>
                    <div class="card-balance">
                        <span>${formatMoney(acc.balance)}</span>
                        <span class="card-currency">${escapeHtml(acc.currency)}</span>
                    </div>
                </div>
                <div class="card-footer">
                    <span class="card-iban">${escapeHtml(formatIbanDisplay(acc.iban))}</span>
                    <span class="card-status-badge ${acc.active ? 'badge-active' : 'badge-inactive'}">
                        ${acc.active ? __('account.active') : __('account.inactive')}
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
                <p>${__('account.load_error')}</p>
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
            showAlert(__('account.iban_must_be_26'), 'danger');
            return;
        }

        spinner.classList.remove('d-none');
        submitBtn.disabled = true;

        try {
            const idempotencyKey = getIdempotencyKey('accountKey');
            await fetchApi('/accounts', {
                method: 'POST',
                headers: {
                    'Idempotency-Key': idempotencyKey
                },
                body: JSON.stringify({ userId: parseInt(userId), iban, ownerName, initialBalance: balance, currency })
            });

            showAlert(__('account.created'));
            renewIdempotencyKey('accountKey');
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

    senderSelect.innerHTML = `<option value="" disabled selected>${__('transfer.sender_placeholder')}</option>`;
    receiverSelect.innerHTML = `<option value="" disabled selected>${__('transfer.recipient_placeholder')}</option>`;

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
        indicator.textContent = `${__('transfer.limit')}: ${formatMoney(selectedAcc.balance)} ${selectedAcc.currency}`;
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
            showAlert(__('transfer.swap_warning'), 'warning');
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
            showAlert(__('transfer.select_sender'), 'danger');
            return;
        }

        if (amount > senderAcc.balance) {
            showAlert(__('transfer.insufficient_balance'), 'danger');
            return;
        }

        let receiverIban = '';
        if (receiverType === 'registered') {
            const receiverId = receiverSelect.value;
            const receiverAcc = accounts.find(a => a.id == receiverId);
            if (!receiverAcc) {
                showAlert(__('transfer.select_recipient'), 'danger');
                return;
            }
            if (senderId === receiverId) {
                showAlert(__('transfer.cannot_same'), 'danger');
                return;
            }
            if (senderAcc.currency !== receiverAcc.currency) {
                showAlert(__('transfer.currency_mismatch'), 'danger');
                return;
            }
            receiverIban = receiverAcc.iban;
        } else {
            receiverIban = manualIbanInput.value.trim();
            if (receiverIban.length !== 26) {
                showAlert(__('transfer.valid_iban'), 'danger');
                return;
            }
            if (senderAcc.iban === receiverIban) {
                showAlert(__('transfer.cannot_sender'), 'danger');
                return;
            }
        }

        const idempotencyKey = getIdempotencyKey('transferKey');
        let lastError = null;

        for (let attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                showAlert(`Retrying... (${attempt + 1}/3)`, 'warning');
                await new Promise(r => setTimeout(r, 1000 * Math.pow(2, attempt - 1)));
            }

            spinner.classList.remove('d-none');
            submitBtn.disabled = true;

            try {
                const result = await fetchApi('/transfers', {
                    method: 'POST',
                    headers: {
                        'Idempotency-Key': idempotencyKey
                    },
                    body: JSON.stringify({
                        senderIban: senderAcc.iban,
                        receiverIban: receiverIban,
                        amount: amount,
                        currency: currency
                    })
                });

                showAlert(`${__('transfer.success')} (${__('transfer.amount')}: ${formatMoney(result.amount)} ${escapeHtml(result.currency)})`);
                form.reset();
                renewIdempotencyKey('transferKey');
                document.getElementById('sender-balance-indicator').textContent = '';

                await loadAccounts();
                lastError = null;
                break;
            } catch (err) {
                lastError = err;
                if (attempt < 2) {
                    continue;
                }
            } finally {
                spinner.classList.add('d-none');
                submitBtn.disabled = false;
            }
        }

        if (lastError) {
            showAlert(lastError.message, 'danger');
        }
    });
}

// --- Reports & History Section ---
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
            showAlert(__('report.select_account_first'), 'warning');
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
    historyList.innerHTML = `<div class="empty-state"><span class="spinner"></span><p>${__('report.history_loading')}</p></div>`;

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
                    <p>${__('report.history_none')}</p>
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
                            ${isCancelled ? __('transfer.cancelled_prefix') : ''}
                            ${isOutgoing ? __('transfer.outgoing', escapeHtml(formatIbanDisplay(t.receiverIban))) : __('transfer.incoming', escapeHtml(formatIbanDisplay(t.senderIban)))}
                        </span>
                        <span class="history-sub">${formatDate(t.createdAt)}</span>
                    </div>
                </div>
                <div class="history-right">
                    <span class="history-amount ${amountClass}">${prefix}${formatMoney(t.amount)} ${escapeHtml(t.currency)}</span>
                    ${isEligibleForCancel ? `<button class="btn-cancel-transfer" onclick="cancelTransfer(${t.id}, ${accountId})">${__('transfer.cancel_btn')}</button>` : ''}
                </div>
            `;

            historyList.appendChild(item);
        });
    } catch (err) {
        showAlert(err.message, 'danger');
        historyList.innerHTML = `
            <div class="empty-state" style="color: var(--danger);">
                <span class="empty-icon">⚠️</span>
                <p>${__('report.load_error')}</p>
            </div>
        `;
    }
}

// Exposed to global window scope so it can be called from dynamic HTML
window.cancelTransfer = async function (transferId, accountId) {
    if (!confirm(__('transfer.cancel_confirm'))) {
        return;
    }

    const idempotencyKey = getIdempotencyKey('cancelKey_' + transferId);
    let lastError = null;

    for (let attempt = 0; attempt < 3; attempt++) {
        if (attempt > 0) {
            await new Promise(r => setTimeout(r, 1000 * Math.pow(2, attempt - 1)));
        }

        try {
            await fetchApi(`/transfers/${transferId}/cancel`, {
                method: 'POST',
                headers: {
                    'Idempotency-Key': idempotencyKey
                }
            });
            showAlert(__('transfer.cancelled'));
            clearIdempotencyKey('cancelKey_' + transferId);

            await loadAccounts();
            loadAccountHistory(accountId);
            lastError = null;
            break;
        } catch (err) {
            lastError = err;
        }
    }

    if (lastError) {
        showAlert(lastError.message, 'danger');
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
                    ${__('report.no_transactions')}
                </td>
            </tr>
        `;
    } else {
        report.transfers.forEach(t => {
            const tr = document.createElement('tr');

            const isCancelled = t.status === 'CANCELLED';
            const statusBadge = `<span class="card-status-badge ${isCancelled ? 'badge-inactive' : 'badge-active'}">
                ${isCancelled ? __('report.status_cancelled') : __('report.status_completed')}
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
    const locale = currentLang === 'tr' ? 'tr-TR' : 'en-US';
    return new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(amount);
}

function formatIbanDisplay(iban) {
    if (!iban) return '';
    return iban.replace(/(.{4})/g, '$1 ').trim();
}

function formatDate(dateString) {
    const d = new Date(dateString);
    const locale = currentLang === 'tr' ? 'tr-TR' : 'en-US';
    return d.toLocaleString(locale, {
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

            showAlert(__('auth.login.success'));
            checkAuthStatus();
        } catch (err) {
            showAlert(__('auth.login.failed') + err.message, 'danger');
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
            const registerKey = getIdempotencyKey('registerKey');
            await fetchApi('/auth/register', {
                method: 'POST',
                headers: {
                    'Idempotency-Key': registerKey
                },
                body: JSON.stringify({ username: usernameVal, password: passwordVal })
            });
            renewIdempotencyKey('registerKey');

            showAlert(__('auth.register.success'));
            registerForm.reset();
            // Switch to login tab
            tabLoginBtn.click();
        } catch (err) {
            showAlert(__('auth.register.failed') + err.message, 'danger');
        } finally {
            registerSpinner.classList.add('d-none');
            btnRegisterSubmit.disabled = false;
        }
    });

    // Logout Click
    btnLogout.addEventListener('click', () => {
        logout();
        showAlert(__('auth.logout.success'));
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
    localStorage.removeItem('transferKey');
    localStorage.removeItem('accountKey');
    localStorage.removeItem('registerKey');

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
