// --- Configuration ---
const API_BASE = '/api/v1';

// --- i18n Translations ---
const translations = {
    en: {
        'app.title': 'X Bank — Corporate Digital Banking',
        'welcome': 'Corporate Internet Branch',
        'welcome.subtitle': 'Sign in with your corporate credentials to continue.',
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
        'auth.login.submit': 'Secure Login',
        'auth.login.success': 'Login successful. Welcome to X Bank.',
        'auth.login.failed': 'Login failed: ',
        'auth.register.success': 'Registration completed successfully. You can now log in.',
        'auth.register.failed': 'Registration failed: ',
        'auth.logout': 'Logout',
        'auth.logout.success': 'Logged out successfully.',
        'auth.session_expired': 'Your session has expired, please log in again.',
        'nav.accounts': 'Accounts',
        'nav.transfer': 'Money Transfer',
        'nav.reports': 'Reports & Analytics',
        'nav.user.role': 'Corporate Customer',
        'brand.legal': 'BANK',
        'brand.tagline': 'Institutional strength. Personal precision.',
        'brand.bullet1': 'Regulatory-aligned audit trail',
        'brand.bullet2': 'Multi-layer authentication & session guard',
        'brand.bullet3': 'Corporate cash management, 24/7',
        'account.title': 'Account Management',
        'account.subtitle': 'View your active bank accounts and create new ones.',
        'account.open_new': 'Open New Account',
        'account.balance_label': 'Available Balance',
        'account.active': 'Active',
        'account.inactive': 'Inactive',
        'account.no_accounts': 'No accounts found. You can start by opening a new account.',
        'account.load_error': 'Account information could not be loaded. Please refresh the page.',
        'account.created': 'Your account has been successfully created.',
        'portfolio.summary': '{0} accounts',
        'account.iban_hint': 'TR is filled in for you — just type the 24 digits.',
        'account.iban_must_be_26': 'IBAN is incomplete — it should be TR followed by 24 digits.',
        'account.invalid_balance': 'Please enter a valid initial balance (0 or more, max 2 decimals).',
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
        'transfer.invalid_amount': 'Please enter an amount greater than zero.',
        'transfer.insufficient_balance': 'Sender account balance is insufficient for this transaction.',
        'transfer.select_recipient': 'Please select a recipient account.',
        'transfer.cannot_same': 'Cannot transfer to the same account.',
        'transfer.currency_mismatch': 'Cannot transfer between accounts with mismatched currencies.',
        'transfer.valid_iban': 'Recipient IBAN is incomplete — TR followed by 24 digits.',
        'transfer.cannot_sender': 'Cannot transfer to the sender account.',
        'transfer.success': 'Transfer completed successfully!',
        'transfer.info_title': 'Transaction Information',
        'transfer.info_instant': 'Your transfers are processed instantly, 24/7, over our <strong>instant settlement</strong> network.',
        'transfer.info_cancel': 'Per our security standards, transfers made within the last <strong>24 hours</strong> can be cancelled.',
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
        'report.status_pending': 'PENDING',
        'report.status_failed': 'FAILED',
        'report.history_none': 'No transaction history found for this account.',
        'report.history_loading': 'Loading transactions...',
        'report.load_error': 'Account history could not be loaded.',
        'modal.create_title': 'Create New Account',
        'modal.sub': 'Takes ~10 seconds. Just type the 24 digits after TR.',
        'modal.owner_name': 'Account Holder Name',
        'modal.owner_placeholder': 'e.g. John Doe',
        'modal.iban': 'IBAN Number',
        'modal.iban_placeholder': 'TR000000000000000000000000',
        'modal.balance': 'Initial Balance',
        'modal.currency': 'Currency',
        'modal.cancel': 'Cancel',
        'modal.dismiss': 'Close',
        'modal.confirm_title': 'Please confirm',
        'modal.create': 'Create Account',
        'general.error': 'An error occurred.',
        'general.loading': 'Loading...',
        'general.retry_in': 'Please retry in {0} seconds.',
        'general.timeout': 'Request timed out after 30 seconds. Please try again.',
        'general.offline': 'You appear to be offline. Check your connection and try again.',
        'general.back_online': 'Connection restored.',
        'general.load_more': 'Load more',
        'general.showing_of': 'Showing {0} of {1}',
        'general.copy_iban': 'Copy IBAN',
        'general.dismiss': 'Dismiss',
        'a11y.skip': 'Skip to content',
        'a11y.language': 'Language',
        'a11y.auth': 'Authentication',
        'a11y.primary': 'Primary',
        'a11y.mobile': 'Mobile',
        'a11y.currency_filter': 'Currency filter',
        'a11y.sort': 'Sort accounts',
        'a11y.quick_amounts': 'Quick amounts',
        'a11y.quick_ranges': 'Quick ranges',
        'a11y.transfer_steps': 'Transfer steps',
        'a11y.report_views': 'Report views',
        'auth.show_password': 'Show password',
        'auth.hide_password': 'Hide password',
        'theme.switch_to_light': 'Switch to light theme',
        'theme.switch_to_dark': 'Switch to dark theme',
        'brand.live': 'All systems operational',
        'brand.sub': 'Corporate-grade accounts, secure transfers and audit-ready reporting — in one governed platform.',
        'brand.stat1': 'founded',
        'brand.stat2': 'uptime SLA',
        'brand.stat3': 'corporate support',
        'brand.badge2': 'Governed demo environment',
        'brand.footer': 'Secure demo environment. No real funds move. | Privacy · Terms · Cookies',
        'corp.topbar_notice': 'Licensed institution simulation — secure demo environment',
        'corp.branch': 'Corporate Internet Branch',
        'corp.footer_desc': 'Governed, audit-ready digital banking for institutions and individuals.',
        'corp.col1': 'Corporate',
        'corp.col1a': 'About X Bank',
        'corp.col1b': 'Investor Relations',
        'corp.col1c': 'Sustainability',
        'corp.col2': 'Legal',
        'corp.col2b': 'Privacy Notice',
        'corp.col2c': 'Cookie Policy',
        'corp.col3': 'Contact',
        'health.up': 'API operational',
        'health.down': 'API unreachable',
        'auth.secure_hint': 'Protected by rate-limiting & brute-force guard.',
        'account.eyebrow': 'Total portfolio',
        'account.hero_empty': 'Open your first account to see totals here.',
        'account.refresh': 'Refresh',
        'account.quick_transfer': 'Quick transfer →',
        'account.list_title': 'My accounts',
        'account.cur_all': 'All',
        'account.empty_filter': 'No accounts match this filter.',
        'account.sort_new': 'Default order',
        'account.sort_bal_desc': 'Balance ↓',
        'account.sort_bal_asc': 'Balance ↑',
        'account.card_hint': 'Tap an account for details.',
        'account.copied': 'IBAN copied to clipboard.',
        'account.detail_title': 'Account details',
        'hero.fast': 'INSTANT',
        'hero.online': 'Online',
        'transfer.step1': 'Sender',
        'transfer.step2': 'Recipient',
        'transfer.step3': 'Amount',
        'transfer.max': 'Max',
        'transfer.summary': 'Transfer summary',
        'transfer.receipt_title': 'Transfer receipt',
        'transfer.own_iban_note': 'This IBAN belongs to one of your accounts ({0}).',
        'transfer.sum_from': 'From',
        'transfer.sum_to': 'To',
        'transfer.sum_amount': 'Amount',
        'transfer.sum_hint': 'Fill the form to preview your transfer.',
        'transfer.sum_ready': 'Ready to send. Please review.',
        'transfer.secure_note': 'Idempotency-protected · double-submit safe.',
        'report.print': 'Print / PDF',
        'report.tip_title': 'Tip',
        'report.tip': 'Use the generator for audits — results are page-scoped with totals.',
        'report.stat_avg': 'Average',
        'report.chart': 'Volume by day',
    },
    tr: {
        'app.title': 'X Bank — Kurumsal Dijital Bankacılık',
        'welcome': 'Kurumsal İnternet Şubesi',
        'welcome.subtitle': 'Devam etmek için kurumsal bilgilerinizle giriş yapın.',
        'auth.login': 'Giriş Yap',
        'auth.register': 'Kayıt Ol',
        'auth.username': 'Kullanıcı Adı',
        'auth.password': 'Şifre',
        'auth.username.placeholder': 'Kullanıcı adınızı girin',
        'auth.password.placeholder': 'Şifrenizi girin',
        'auth.register.username.placeholder': 'Yeni bir kullanıcı adı seçin',
        'auth.register.password.placeholder': 'Güçlü bir şifre belirleyin',
        'auth.password.hint': 'Şifre en az 8 karakter olmalı; en az bir büyük harf, bir küçük harf ve bir rakam içermelidir.',
        'auth.register.submit': 'Hesap Oluştur & Kaydol',
        'auth.login.submit': 'Güvenli Giriş',
        'auth.login.success': 'Giriş başarılı. X Bank’a hoş geldiniz!',
        'auth.login.failed': 'Giriş başarısız: ',
        'auth.register.success': 'Kayıt başarıyla tamamlandı. Şimdi giriş yapabilirsiniz.',
        'auth.register.failed': 'Kayıt başarısız: ',
        'auth.logout': 'Çıkış Yap',
        'auth.logout.success': 'Başarıyla çıkış yapıldı.',
        'auth.session_expired': 'Oturumunuzun süresi doldu, lütfen tekrar giriş yapın.',
        'nav.accounts': 'Hesaplar',
        'nav.transfer': 'Para Transferi',
        'nav.reports': 'Rapor & Analitik',
        'nav.user.role': 'Kurumsal Müşteri',
        'brand.legal': 'BANK',
        'brand.tagline': 'Kurumsal güç. Bireysel hassasiyet.',
        'brand.bullet1': 'Mevzuata uyumlu denetim izi',
        'brand.bullet2': 'Çok katmanlı kimlik doğrulama ve oturum koruması',
        'brand.bullet3': '7/24 kurumsal nakit yönetimi',
        'account.title': 'Hesap Yönetimi',
        'account.subtitle': 'Aktif banka hesaplarınızı görüntüleyin ve yeni hesap açın.',
        'account.open_new': 'Yeni Hesap Aç',
        'account.balance_label': 'Kullanılabilir Bakiye',
        'account.active': 'Aktif',
        'account.inactive': 'Pasif',
        'account.no_accounts': 'Hiç hesap bulunamadı. Yeni bir hesap açarak başlayabilirsiniz.',
        'account.load_error': 'Hesap bilgileri yüklenemedi. Lütfen sayfayı yenileyin.',
        'account.created': 'Hesabınız başarıyla oluşturuldu.',
        'portfolio.summary': '{0} hesap',
        'account.iban_hint': 'TR otomatik yazılır — yalnızca 24 rakamı girmeniz yeterli.',
        'account.iban_must_be_26': 'IBAN eksik — TR harflerinden sonra 24 rakam olmalı.',
        'account.invalid_balance': 'Lütfen geçerli bir başlangıç bakiyesi girin (0 veya üzeri, en fazla 2 ondalık).',
        'transfer.title': 'Para Transferi',
        'transfer.subtitle': 'Hesaplarınız arasında veya diğer IBAN’lara güvenli ve anında transfer yapın.',
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
        'transfer.swap_warning': 'Yalnızca iki kayıtlı hesap seçiliyken değişim yapabilirsiniz.',
        'transfer.select_sender': 'Lütfen bir gönderen hesabı seçin.',
        'transfer.invalid_amount': 'Lütfen sıfırdan büyük bir tutar girin.',
        'transfer.insufficient_balance': 'Gönderen hesap bakiyesi bu işlem için yetersiz.',
        'transfer.select_recipient': 'Lütfen bir alıcı hesabı seçin.',
        'transfer.cannot_same': 'Aynı hesaba transfer yapılamaz.',
        'transfer.currency_mismatch': 'Para birimleri eşleşmeyen hesaplar arasında transfer yapılamaz.',
        'transfer.valid_iban': 'Alıcı IBAN’ı eksik — TR harflerinden sonra 24 rakam olmalı.',
        'transfer.cannot_sender': 'Gönderen hesaba transfer yapılamaz.',
        'transfer.success': 'Transfer başarıyla tamamlandı!',
        'transfer.info_title': 'İşlem Bilgileri',
        'transfer.info_instant': 'Transferleriniz <strong>anlık mutabakat ağımız</strong> üzerinden 7/24 işlenir.',
        'transfer.info_cancel': 'Güvenlik standartlarımız gereği son <strong>24 saat</strong> içinde yapılan transferler iptal edilebilir.',
        'transfer.info_currency': 'Hesaplar arası transferlerde para birimleri eşleşmelidir.',
        'transfer.cancel_confirm': 'Bu transferi iptal edip tutarı iade etmek istediğinize emin misiniz?',
        'transfer.cancelled': 'Transfer başarıyla iptal edildi ve bakiyeler güncellendi.',
        'transfer.cancelled_prefix': '[İPTAL EDİLDİ] ',
        'transfer.outgoing': 'Transfer: Alıcı IBAN ({0})',
        'transfer.incoming': 'Gelen Transfer: Gönderen IBAN ({0})',
        'transfer.cancel_btn': 'İptal Et',
        'report.title': 'Rapor & Hesap Detayları',
        'report.subtitle': 'Hesap hareketlerinizi inceleyin ve belirli tarih aralıkları için detaylı raporlar oluşturun.',
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
        'report.status_pending': 'BEKLEMEDE',
        'report.status_failed': 'BAŞARISIZ',
        'report.history_none': 'Bu hesap için işlem geçmişi bulunamadı.',
        'report.history_loading': 'İşlemler yükleniyor...',
        'report.load_error': 'Hesap geçmişi yüklenemedi.',
        'modal.create_title': 'Yeni Hesap Oluştur',
        'modal.sub': 'Yaklaşık 10 saniye sürer. TR’den sonra 24 rakamı yazmanız yeterli.',
        'modal.owner_name': 'Hesap Sahibi Adı',
        'modal.owner_placeholder': 'örn. Ad Soyad',
        'modal.iban': 'IBAN Numarası',
        'modal.iban_placeholder': 'TR000000000000000000000000',
        'modal.balance': 'Başlangıç Bakiyesi',
        'modal.currency': 'Para Birimi',
        'modal.cancel': 'İptal',
        'modal.dismiss': 'Kapat',
        'modal.confirm_title': 'Lütfen onaylayın',
        'modal.create': 'Hesap Oluştur',
        'general.error': 'Bir hata oluştu.',
        'general.loading': 'Yükleniyor...',
        'general.retry_in': 'Lütfen {0} saniye sonra tekrar deneyin.',
        'general.timeout': 'İstek 30 saniye içinde zaman aşımına uğradı. Lütfen tekrar deneyin.',
        'general.offline': 'Çevrimdışı görünüyorsunuz. Bağlantınızı kontrol edip tekrar deneyin.',
        'general.back_online': 'Bağlantı yeniden kuruldu.',
        'general.load_more': 'Daha fazla yükle',
        'general.showing_of': '{1} kayıttan {0} gösteriliyor',
        'general.copy_iban': 'IBAN’ı kopyala',
        'general.dismiss': 'Kapat',
        'a11y.skip': 'İçeriğe atla',
        'a11y.language': 'Dil',
        'a11y.auth': 'Kimlik doğrulama',
        'a11y.primary': 'Ana menü',
        'a11y.mobile': 'Mobil menü',
        'a11y.currency_filter': 'Para birimi filtresi',
        'a11y.sort': 'Hesap sıralama',
        'a11y.quick_amounts': 'Hızlı tutarlar',
        'a11y.quick_ranges': 'Hızlı aralıklar',
        'a11y.transfer_steps': 'Transfer adımları',
        'a11y.report_views': 'Rapor görünümleri',
        'auth.show_password': 'Şifreyi göster',
        'auth.hide_password': 'Şifreyi gizle',
        'theme.switch_to_light': 'Açık temaya geç',
        'theme.switch_to_dark': 'Koyu temaya geç',
        'brand.live': 'Tüm sistemler çalışıyor',
        'brand.sub': 'Kurumsal sınıf hesaplar, güvenli transferler ve denetime hazır raporlama — tek yönetişim platformunda.',
        'brand.stat1': 'kuruluş',
        'brand.stat2': 'çalışma süresi SLA',
        'brand.stat3': 'kurumsal destek',
        'brand.badge2': 'Yönetişimli demo ortamı',
        'brand.footer': 'Güvenli demo ortamı. Gerçek para hareketi yok. | Gizlilik · Koşullar · Çerezler',
        'corp.topbar_notice': 'Lisanslı kuruluş simülasyonu — güvenli demo ortamı',
        'corp.branch': 'Kurumsal İnternet Şubesi',
        'corp.footer_desc': 'Kurumlar ve bireyler için yönetişimli, denetime hazır dijital bankacılık.',
        'corp.col1': 'Kurumsal',
        'corp.col1a': 'X Bank Hakkında',
        'corp.col1b': 'Yatırımcı İlişkileri',
        'corp.col1c': 'Sürdürülebilirlik',
        'corp.col2': 'Yasal',
        'corp.col2b': 'Gizlilik Bildirimi',
        'corp.col2c': 'Çerez Politikası',
        'corp.col3': 'İletişim',
        'health.up': 'API çalışıyor',
        'health.down': 'API erişilemiyor',
        'auth.secure_hint': 'Hız sınırlama ve kaba kuvvet korumasıyla güvende.',
        'account.eyebrow': 'Toplam portföy',
        'account.hero_empty': 'Toplamları görmek için ilk hesabınızı açın.',
        'account.refresh': 'Yenile',
        'account.quick_transfer': 'Hızlı transfer →',
        'account.list_title': 'Hesaplarım',
        'account.cur_all': 'Tümü',
        'account.empty_filter': 'Bu filtreye uygun hesap bulunamadı.',
        'account.sort_new': 'Varsayılan sıra',
        'account.sort_bal_desc': 'Bakiye ↓',
        'account.sort_bal_asc': 'Bakiye ↑',
        'account.card_hint': 'Detay için hesaba dokunun.',
        'account.copied': 'IBAN panoya kopyalandı.',
        'account.detail_title': 'Hesap detayları',
        'hero.fast': 'ANLIK',
        'hero.online': 'Çevrimiçi',
        'transfer.step1': 'Gönderen',
        'transfer.step2': 'Alıcı',
        'transfer.step3': 'Tutar',
        'transfer.max': 'Maks',
        'transfer.summary': 'Transfer özeti',
        'transfer.receipt_title': 'Transfer dekontu',
        'transfer.own_iban_note': 'Bu IBAN, hesaplarınızdan birine ait ({0}).',
        'transfer.sum_from': 'Gönderen',
        'transfer.sum_to': 'Alıcı',
        'transfer.sum_amount': 'Tutar',
        'transfer.sum_hint': 'Önizleme için formu doldurun.',
        'transfer.sum_ready': 'Gönderime hazır. Lütfen kontrol edin.',
        'transfer.secure_note': 'Idempotency korumalı · çift gönderim güvenli.',
        'report.print': 'Yazdır / PDF',
        'report.tip_title': 'İpucu',
        'report.tip': 'Denetimler için oluşturucuyu kullanın — sonuçlar sayfa bazında toplanır.',
        'report.stat_avg': 'Ortalama',
        'report.chart': 'Günlük hacim',
    }
};

let currentLang = localStorage.getItem('lang') || 'en';
if (currentLang !== 'tr' && currentLang !== 'en') currentLang = 'en';

function locale() {
    return currentLang === 'tr' ? 'tr-TR' : 'en-US';
}

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
    currentLang = (lang === 'tr') ? 'tr' : 'en';
    localStorage.setItem('lang', currentLang);
    document.documentElement.lang = currentLang;
    translateStaticPage();
    document.dispatchEvent(new CustomEvent('languagechange', { detail: { lang: currentLang } }));
}

function translateStaticPage() {
    document.title = __('app.title');
    document.querySelectorAll('[data-i18n]').forEach(el => {
        const key = el.getAttribute('data-i18n');
        if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
            el.placeholder = __(key);
        } else {
            // innerHTML: dictionary strings are developer-controlled and some
            // contain markup (<strong>); textContent would strip it.
            el.innerHTML = __(key);
        }
    });
    document.querySelectorAll('[data-i18n-title]').forEach(el => {
        el.title = __(el.getAttribute('data-i18n-title'));
    });
    document.querySelectorAll('[data-i18n-aria]').forEach(el => {
        el.setAttribute('aria-label', __(el.getAttribute('data-i18n-aria')));
    });
}

function getLanguage() {
    return currentLang;
}

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

// --- Idempotency Key Helpers ---
// crypto.randomUUID() exists only in secure contexts (HTTPS/localhost).
// Fallback keeps money-movement endpoints working over plain-HTTP LAN URLs:
// output is hex+hyphens (36 chars), satisfying the backend SAFE_KEY charset
// ([A-Za-z0-9\-_.:]+) and the 128-char cap.
function newUuid() {
    try {
        if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
            return crypto.randomUUID();
        }
    } catch (e) { /* fall through to Math.random fallback */ }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = Math.floor(Math.random() * 16);
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
    });
}

function getIdempotencyKey(keyName) {
    let key = localStorage.getItem(keyName);
    if (!key) {
        key = newUuid();
        localStorage.setItem(keyName, key);
    }
    return key;
}

function renewIdempotencyKey(keyName) {
    const key = newUuid();
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
// Paged-list state: backend caps size at 100, so long lists need "load more".
// Filters/sorts apply to the items loaded so far.
let accountPager = { page: 0, last: true, total: 0 };
let historyCache = { accountId: null, items: [] };
let historyPager = { accountId: null, page: 0, last: true, total: 0 };

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
    if (!token) return;
    renderPortfolioSummary();
    loadAccounts();
    populateTransferDropdowns();
    populateReportDropdown();
    const resultsEl = document.getElementById('report-results');
    if (window.__lastReport && resultsEl && !resultsEl.classList.contains('d-none')) {
        renderReportResults(window.__lastReport);
    }
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
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }
        headers['Accept-Language'] = getLanguage();
        const response = await fetch(`${API_BASE}${endpoint}`, {
            ...options,
            headers,
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
            if (token) {
                logout();
                throw new Error(__('auth.session_expired'));
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
            // Backend tx timeout is 30s; the idempotency key dedupes any
            // late-completing write, so surfacing (and retrying) is safe.
            throw new Error(__('general.timeout'));
        }
        console.error('API Error:', error);
        throw error;
    } finally {
        if (timeoutId) clearTimeout(timeoutId);
    }
}

function extractErrorMessage(data) {
    if (!data || typeof data !== 'object') {
        return (typeof data === 'string' && data) ? data : '';
    }
    if (data.message) return data.message;
    if (data.detail) return data.detail;
    if (data.errors && typeof data.errors === 'object') {
        const parts = Object.entries(data.errors).map(([f, m]) => `${f}: ${m}`);
        if (parts.length > 0) return parts.join(' · ');
    }
    if (data.title && data.status) return `${data.title} (${data.status})`;
    return '';
}

// --- Accounts Operations ---
function renderPortfolioSummary() {
    const summaryEl = document.getElementById('portfolio-summary');
    const heroTotal = document.getElementById('hero-total');
    const heroCount = document.getElementById('hero-accounts-count');
    const heroEmpty = document.getElementById('hero-empty');
    const heroUpdated = document.getElementById('hero-last-updated');
    if (!summaryEl) {
        return;
    }
    if (accounts.length === 0) {
        summaryEl.classList.add('d-none');
        summaryEl.innerHTML = '';
        if (heroTotal) heroTotal.textContent = '—';
        if (heroCount) heroCount.textContent = __('account.no_accounts');
        if (heroEmpty) heroEmpty.classList.remove('d-none');
        return;
    }
    if (heroEmpty) heroEmpty.classList.add('d-none');
    const totals = {};
    accounts.forEach(acc => {
        const balance = Number(acc.balance) || 0;
        totals[acc.currency] = (totals[acc.currency] || 0) + balance;
    });
    const parts = Object.entries(totals)
        .map(([currency, total]) => `${formatMoney(total)} ${escapeHtml(currency)}`);
    summaryEl.innerHTML = `<span class="portfolio-total">${parts.join(' · ')}</span>` +
        `<span class="portfolio-meta">${__('portfolio.summary', accounts.length)}</span>`;
    summaryEl.classList.remove('d-none');
    if (heroTotal) heroTotal.textContent = parts.join(' · ');
    if (heroCount) heroCount.textContent = `${__('portfolio.summary', accounts.length)} · ${Object.keys(totals).length} currency`;
    if (heroUpdated) heroUpdated.textContent = `${new Date().toLocaleString(locale())} · ${accounts.length} accounts synced`;
}

function renderAccountSkeletons(listElement, count) {
    listElement.innerHTML = '';
    for (let i = 0; i < count; i++) {
        const skeleton = document.createElement('div');
        skeleton.className = 'card card-skeleton';
        skeleton.setAttribute('aria-hidden', 'true');
        listElement.appendChild(skeleton);
    }
}

async function loadAccounts(loadMore = false) {
    const listElement = document.getElementById('accounts-list');

    try {
        if (!loadMore) {
            accountPager = { page: 0, last: true, total: 0 };
            accounts = [];
            listElement.innerHTML = '';
        }
        if (listElement.children.length === 0 && accountPager.page === 0) {
            renderAccountSkeletons(listElement, 3);
        }
        const page = loadMore ? accountPager.page + 1 : 0;
        const response = await fetchApi(`/accounts?page=${page}&size=${PAGE_SIZE}`);
        const content = response.content || response;
        const pageItems = Array.isArray(content) ? content : [];
        accounts = loadMore ? accounts.concat(pageItems) : pageItems;
        accountPager = {
            page,
            last: (response && typeof response.last === 'boolean') ? response.last : true,
            total: (response && typeof response.totalElements === 'number') ? response.totalElements : accounts.length
        };
        listElement.innerHTML = '';
        renderPortfolioSummary();

        if (accounts.length === 0) {
            const countEl = document.getElementById('account-count');
            if (countEl) countEl.textContent = '0';
            listElement.innerHTML = `
                <div class="empty-state" style="grid-column: 1 / -1;">
                    <span class="empty-art"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="6" width="18" height="13" rx="2"/><path d="M3 10h18M7 15h4"/></svg></span>
                    <p>${__('account.no_accounts')}</p>
                </div>
            `;
            return;
        }

        const view = (window.__accountView || {});
        let visible = [...accounts];
        if (view.currency && view.currency !== 'all') {
            visible = visible.filter(a => String(a.currency || '').toUpperCase() === view.currency);
        }
        if (view.sort === 'balance-desc') visible.sort((a, b) => Number(b.balance) - Number(a.balance));
        else if (view.sort === 'balance-asc') visible.sort((a, b) => Number(a.balance) - Number(b.balance));

        const countEl = document.getElementById('account-count');
        if (countEl) countEl.textContent = visible.length;

        if (visible.length === 0 && accounts.length > 0) {
            listElement.innerHTML = `
                <div class="empty-state" style="grid-column: 1 / -1;">
                    <span class="empty-art"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="6" width="18" height="13" rx="2"/><path d="M3 10h18"/></svg></span>
                    <p>${__('account.empty_filter')}</p>
                </div>
            `;
            return;
        }

        visible.forEach(acc => {
            const card = document.createElement('div');
            card.className = `card ${acc.active ? 'card-active' : 'card-inactive'}`;
            card.setAttribute('tabindex', '0');
            card.setAttribute('role', 'button');
            card.setAttribute('aria-label', `${acc.ownerName} ${formatIbanDisplay(acc.iban)}`);

            card.innerHTML = `
                <span class="card-top-accent" aria-hidden="true"></span>
                <div class="card-header">
                    <div class="card-bank-info">
                        <span class="card-bank-name">X BANK</span>
                        <span class="card-owner">${escapeHtml(acc.ownerName)}</span>
                    </div>
                    <div class="card-chip" aria-hidden="true"></div>
                </div>
                <div class="card-body">
                    <span class="card-balance-label">${__('account.balance_label')}</span>
                    <div class="card-balance">
                        <span>${formatMoney(acc.balance)}</span>
                        <span class="card-currency">${escapeHtml(acc.currency)}</span>
                    </div>
                </div>
                <div class="card-footer">
                    <span class="card-iban">${escapeHtml(formatIbanDisplay(acc.iban))}<button type="button" class="copy-btn" data-copy="${escapeHtml(acc.iban)}" title="${escapeHtml(__('general.copy_iban'))}" aria-label="${escapeHtml(__('general.copy_iban'))}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg></button></span>
                    <span class="card-status-badge ${acc.active ? 'badge-active' : 'badge-inactive'}">
                        ${acc.active ? __('account.active') : __('account.inactive')}
                    </span>
                </div>
            `;

            const goDetail = () => {
                openAccountDetail(acc.id);
            };
            card.addEventListener('click', (e) => {
                if (e.target.closest('.copy-btn')) return;
                goDetail();
            });
            card.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    goDetail();
                }
            });

            listElement.appendChild(card);
        });

        if (!accountPager.last && accounts.length > 0) {
            const moreWrap = document.createElement('div');
            moreWrap.className = 'load-more-wrap';
            moreWrap.innerHTML = `
                <p class="grid-hint">${__('general.showing_of', accounts.length, accountPager.total)}</p>
                <button type="button" class="btn btn-secondary" id="btn-more-accounts">${__('general.load_more')}</button>
            `;
            listElement.appendChild(moreWrap);
            document.getElementById('btn-more-accounts')
                .addEventListener('click', () => loadAccounts(true));
        }
    } catch (err) {
        showAlert(err.message, 'danger');
        const summaryEl = document.getElementById('portfolio-summary');
        if (summaryEl) {
            summaryEl.classList.add('d-none');
            summaryEl.innerHTML = '';
        }
        listElement.innerHTML = `
            <div class="empty-state" style="grid-column: 1 / -1; color: var(--danger);">
                <span class="empty-art"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M12 3l10 18H2L12 3z"/><path d="M12 10v5M12 18.5v.5"/></svg></span>
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
        const firstField = document.getElementById('acc-owner-name');
        if (firstField) {
            firstField.focus();
        }
        document.addEventListener('keydown', closeOnEscape);
    }

    function closeOnEscape(e) {
        if (e.key === 'Escape') {
            closeModal();
        }
    }

    function closeModal() {
        modal.classList.remove('active');
        document.removeEventListener('keydown', closeOnEscape);
        if (openBtn) {
            openBtn.focus();
        }
    }

    openBtn.addEventListener('click', openModal);
    closeBtn.addEventListener('click', closeModal);
    cancelBtn.addEventListener('click', closeModal);

    // Close modal if clicked overlay
    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });

    // Format IBAN input dynamically (TR locked in, digits only)
    const ibanInput = document.getElementById('acc-iban');
    ibanInput.addEventListener('input', (e) => {
        e.target.value = sanitizeIban(e.target.value);
    });
    ibanInput.addEventListener('focus', (e) => {
        if (!e.target.value) {
            e.target.value = 'TR';
            e.target.dispatchEvent(new Event('input'));
        }
    });

    // Handle Form Submit
    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        const ownerName = document.getElementById('acc-owner-name').value.trim();
        const iban = document.getElementById('acc-iban').value.trim();
        // Round to 2 decimals: backend @Digits(integer=13, fraction=2).
        const balance = Math.round(parseFloat(document.getElementById('acc-balance').value) * 100) / 100;
        const currency = document.getElementById('acc-currency').value;

        if (!Number.isFinite(balance) || balance < 0) {
            showAlert(__('account.invalid_balance'), 'danger');
            return;
        }

        if (!isValidIban(iban)) {
            showAlert(__('account.iban_must_be_26'), 'danger');
            return;
        }

        spinner.classList.remove('d-none');
        submitBtn.disabled = true;
        submitBtn.setAttribute('aria-busy', 'true');

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
            submitBtn.removeAttribute('aria-busy');
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
        indicator.textContent = `${__('account.balance_label')}: ${formatMoney(selectedAcc.balance)} ${selectedAcc.currency}`;
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
                const note = document.getElementById('iban-lookup-note');
                if (note) {
                    note.classList.add('d-none');
                    note.innerHTML = '';
                }
                lastIbanLookup = '';
            } else {
                registeredGroup.classList.add('d-none');
                manualGroup.classList.remove('d-none');
                receiverSelect.required = false;
                manualIbanInput.required = true;
                lookupManualIban(manualIbanInput.value);
            }
        });
    });

    // Format manual IBAN field (TR locked in, digits only)
    let ibanLookupTimer;
    manualIbanInput.addEventListener('input', (e) => {
        e.target.value = sanitizeIban(e.target.value);
        clearTimeout(ibanLookupTimer);
        ibanLookupTimer = setTimeout(() => lookupManualIban(e.target.value), 500);
    });
    manualIbanInput.addEventListener('focus', (e) => {
        if (!e.target.value) {
            e.target.value = 'TR';
            e.target.dispatchEvent(new Event('input'));
        }
    });
    // Re-render the recognition note in the new language.
    document.addEventListener('languagechange', () => {
        const note = document.getElementById('iban-lookup-note');
        if (note && !note.classList.contains('d-none') && lastIbanLookup) {
            const v = lastIbanLookup;
            lastIbanLookup = '';
            lookupManualIban(v);
        }
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
        // Round to 2 decimals: backend @Digits(integer=13, fraction=2).
        const amount = Math.round(parseFloat(document.getElementById('transfer-amount').value) * 100) / 100;
        const currency = document.getElementById('transfer-currency').value;

        const senderAcc = accounts.find(a => a.id == senderId);
        if (!senderAcc) {
            showAlert(__('transfer.select_sender'), 'danger');
            return;
        }

        if (!Number.isFinite(amount) || amount <= 0) {
            showAlert(__('transfer.invalid_amount'), 'danger');
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
            if (!isValidIban(receiverIban)) {
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
            submitBtn.setAttribute('aria-busy', 'true');

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
                if (attempt < 2 && isRetryableError(err)) {
                    continue;
                }
            } finally {
                spinner.classList.add('d-none');
                submitBtn.disabled = false;
                submitBtn.removeAttribute('aria-busy');
            }
        }

        if (lastError) {
            showAlert(lastError.message, 'danger');
        }
    });
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

            // The volume chart measures its canvas on draw; a hidden tab has
            // zero width, so redraw when returning to the generator view.
            if (target === 'generator-tab' && window.__lastReport
                    && !document.getElementById('report-results').classList.contains('d-none')) {
                drawReportChart(window.__lastReport.transfers || []);
            }
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

            // Cancellation eligibility mirrors backend Transfer.cancel():
            // only COMPLETED transfers within the 24-hour window.
            const isEligibleForCancel = t.status === 'COMPLETED' && isOutgoing && (new Date() - new Date(t.createdAt)) < 24 * 60 * 60 * 1000;
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
                    ${isEligibleForCancel ? `<button class="btn-cancel-transfer" onclick="cancelTransfer(${t.id}, ${accountId})">${__('transfer.cancel_btn')}</button>` : ''}
                </div>
            `;

            historyList.appendChild(item);

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
window.cancelTransfer = async function (transferId, accountId) {
    const confirmed = await confirmDialog(__('transfer.cancel_confirm'));
    if (!confirmed) {
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
            if (!isRetryableError(err)) {
                break;
            }
        }
    }

    if (lastError) {
        showAlert(lastError.message, 'danger');
    }
};

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
                <div><dt>${__('modal.owner_name')}</dt><dd>${escapeHtml(acc.ownerName)}</dd></div>
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
    if (!note || !token) return;
    const v = String(iban || '').trim();
    if (!isValidIban(v)) {
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
function isRetryableError(err) {
    // Retry only network failures (no status), 409 optimistic-lock conflicts
    // and server errors. 409 is safe: every write endpoint is idempotency
    // guarded and failed keys are never stored, so the retry dedupes.
    // 4xx validation/auth errors would just fail identically again, 429 must
    // respect Retry-After, and offline retries are pointless.
    if (!err || err.offline) return false;
    if (err.status === 409) return true;
    return !err.status || err.status >= 500;
}

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
    return new Intl.NumberFormat(locale(), { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(amount);
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

function formatDateTimeLocal(date) {
    const pad = (n) => n.toString().padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

// --- Authentication Operations ---
function checkAuthStatus() {
    // Take over from the head boot-guard (data-boot): explicit classes rule now.
    document.documentElement.removeAttribute('data-boot');
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

        loginSpinner.classList.remove('d-none');
        btnLoginSubmit.disabled = true;
        btnLoginSubmit.setAttribute('aria-busy', 'true');

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
            btnLoginSubmit.removeAttribute('aria-busy');
        }
    });

    // Register Form Submit
    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const usernameVal = document.getElementById('register-username').value.trim();
        const passwordVal = document.getElementById('register-password').value;

        // Client mirror of backend PasswordPolicy defaults (min 8, upper,
        // lower, digit): instant feedback instead of a round-trip 400.
        if (!meetsPasswordPolicy(passwordVal)) {
            showAlert(__('auth.password.hint'), 'danger');
            return;
        }

        registerSpinner.classList.remove('d-none');
        btnRegisterSubmit.disabled = true;
        btnRegisterSubmit.setAttribute('aria-busy', 'true');

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
            btnRegisterSubmit.removeAttribute('aria-busy');
        }
    });

    // Logout Click: revoke server-side first so the JWT lands on the
    // blacklist, then clear local state even if the call fails.
    btnLogout.addEventListener('click', async () => {
        if (token) {
            try {
                await fetch(`${API_BASE}/auth/logout`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${token}`
                    }
                });
            } catch (e) {
                console.error('Logout revoke failed:', e);
            }
        }
        logout();
        showAlert(__('auth.logout.success'));
    });
}

function logout() {
    token = null;
    userId = null;
    username = null;
    accounts = [];
    accountPager = { page: 0, last: true, total: 0 };
    historyCache = { accountId: null, items: [] };
    historyPager = { accountId: null, page: 0, last: true, total: 0 };
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
    if (pw.length >= 8) s++;
    if (pw.length >= 12) s++;
    if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) s++;
    if (/\d/.test(pw)) s++;
    if (/[^A-Za-z0-9]/.test(pw)) s++;
    return Math.min(s, 5);
}

function meetsPasswordPolicy(pw) {
    return !!pw && pw.length >= 8
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
    bindCounter('acc-iban', 'acc-iban-counter');
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
            if (token) loadAccounts();
        });
    });
    if (sort) {
        sort.addEventListener('change', () => {
            window.__accountView.sort = sort.value;
            if (token) loadAccounts();
        });
    }
    const refreshBtn = document.getElementById('btn-refresh-accounts');
    if (refreshBtn) refreshBtn.addEventListener('click', () => { if (token) loadAccounts(); });

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
            start.setDate(end.getDate() - days);
            const s = document.getElementById('report-start-date');
            const e = document.getElementById('report-end-date');
            if (s) s.value = formatDateTimeLocal(start);
            if (e) e.value = formatDateTimeLocal(end);
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
            const res = await fetch('actuator/health', {
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
