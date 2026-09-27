// Only appearance preferences persist in browser storage. A prior release's
// bearer token is discarded before application code or third-party scripts run.
(function () {
    try {
        localStorage.removeItem('token');
        localStorage.removeItem('userId');
        localStorage.removeItem('username');
        var theme = localStorage.getItem('theme');
        if (theme !== 'light' && theme !== 'dark' && window.matchMedia
                && window.matchMedia('(prefers-color-scheme: light)').matches) {
            theme = 'light';
        }
        if (theme === 'light') {
            document.documentElement.setAttribute('data-theme', 'light');
            var meta = document.getElementById('meta-theme-color');
            if (meta) meta.setAttribute('content', '#EDF0F6');
        }
        var lang = localStorage.getItem('lang');
        if (lang === 'tr' || lang === 'en') document.documentElement.lang = lang;
    } catch (e) { /* storage may be unavailable; defaults remain usable */ }
})();
