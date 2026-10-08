// ESLint gate for the vanilla-JS browser UI (M-1 slice: lint only, no bundler).
//
// The static scripts share ONE global scope via classic <script> tags in
// load order (pinned by app/src/test/js/frontend_contract.test.js), and every
// top-level name is unique across files (checked when this config was
// written). Each file therefore gets browser globals plus the top-level names
// of the OTHER files — a use of an undeclared name fails no-undef, and each
// file's own declarations never collide with its globals.
//
// Functions consumed cross-file but never referenced in their own file carry
// a `/* exported name, ... */` comment at the top of their file (standard
// practice for classic scripts); without it no-unused-vars would flag them.
//
// Deliberately NOT enabled: `eqeqeq`. Account ids arrive as numbers from the
// API but read back as strings from <select> values, so `a.id == select.value`
// is the intended bridge — `===` there would silently break selection restore.
//
// Run: `npm run lint` (CI runs it after `npm ci`).
const browserReadonly = {
    window: "readonly",
    document: "readonly",
    localStorage: "readonly",
    sessionStorage: "readonly",
    fetch: "readonly",
    location: "readonly",
    navigator: "readonly",
    history: "readonly",
    alert: "readonly",
    confirm: "readonly",
    console: "readonly",
    setTimeout: "readonly",
    clearTimeout: "readonly",
    setInterval: "readonly",
    clearInterval: "readonly",
    queueMicrotask: "readonly",
    URL: "readonly",
    URLSearchParams: "readonly",
    FormData: "readonly",
    Blob: "readonly",
    File: "readonly",
    TextEncoder: "readonly",
    TextDecoder: "readonly",
    crypto: "readonly",
    AbortController: "readonly",
    AbortSignal: "readonly",
    Event: "readonly",
    CustomEvent: "readonly",
    DOMParser: "readonly",
    MutationObserver: "readonly",
    IntersectionObserver: "readonly",
    ResizeObserver: "readonly",
    requestAnimationFrame: "readonly",
    cancelAnimationFrame: "readonly",
    Intl: "readonly",
    performance: "readonly",
    structuredClone: "readonly",
    getComputedStyle: "readonly",
    matchMedia: "readonly",
    scrollTo: "readonly",
};

// Top-level names per file (functions + module state). Keep in step with the
// sources: an unlisted cross-file use fails the build, which is the point.
const topLevel = {
    accounts: [
        "renderPortfolioSummary", "renderAccountSkeletons", "loadAccounts",
        "renderFundingMode", "loadAccountCapabilities", "initModal",
    ],
    app: [
        "getPreferredTheme", "updateThemeButtons", "setTheme", "initTheme",
        "initLanguageSwitcher", "showAlert", "initNavigation", "switchTab",
        "isOffline", "fetchApi", "browserCsrfToken", "trySilentRefresh",
        "restoreBrowserSession", "extractErrorMessage", "populateReportDropdown",
        "initReportSection", "invalidateGeneratedReport", "updateReportPagination",
        "loadReportPage", "loadAccountHistory", "cancelTransfer", "showModal",
        "hideModal", "confirmDialog", "settleConfirm", "resolveAccountRef",
        "openAccountDetail", "openTransferDetail", "lookupManualIban",
        "initDetailModals", "renderReportResults", "transferStatusMeta",
        "escapeHtml", "formatMoney", "sanitizeIban", "isValidIban",
        "hasValidIbanChecksum", "formatIbanDisplay", "formatDate",
        "formatDateInput", "buildReportDateRange", "plusMonths12",
        "checkAuthStatus", "initAuth", "logout", "syncMobileNav",
        "drawReportChart", "passwordScore", "meetsPasswordPolicy",
        "initUxEnhancements", "API_BASE", "accounts", "activeTab",
        "authenticated", "userId", "username", "fundingMode", "accountPager",
        "historyCache", "historyPager", "reportPager", "cancellationsInFlight",
        "navItems", "tabContents", "alertContainer", "REQUEST_TIMEOUT_MS",
        "PAGE_SIZE", "refreshInFlight", "__confirmResolver", "detailAccountId",
        "detailTransferId", "lastIbanLookup",
    ],
    boot: [],
    i18n: [
        "locale", "__", "setLanguage", "translateStaticPage", "getLanguage",
        "translations", "currentLang",
    ],
    idempotency: [
        "newUuid", "requestSignature", "draftStorageKey",
        "loadIdempotencyDrafts", "saveIdempotencyState",
        "syncIdempotencyWarning", "getIdempotencyKey", "markIdempotencyStarted",
        "isAmbiguousFailure", "markIdempotencyFailed", "idempotencyErrorMessage",
        "clearIdempotencyKey", "clearAllIdempotencyKeys",
        "IDEMPOTENCY_STORAGE_PREFIX", "IDEMPOTENCY_KEY_MAX_AGE_MS",
        "idempotencyMemory", "idempotencyActive",
    ],
    transfers: [
        "populateTransferDropdowns", "updateSenderBalance", "initTransferForm",
    ],
};

function globalsFor(ownFile) {
    const globals = { ...browserReadonly };
    for (const [file, names] of Object.entries(topLevel)) {
        if (file === ownFile) continue;
        for (const name of names) globals[name] = "readonly";
    }
    return globals;
}

const scriptRules = {
    "no-undef": "error",
    "no-unused-vars": ["error", { args: "after-used", caughtErrors: "none" }],
    "no-redeclare": "error",
    "no-unreachable": "error",
    "no-eval": "error",
    "no-implied-eval": "error",
    "no-new-func": "error",
};

function staticBlock(file) {
    return {
        files: [`app/src/main/resources/static/${file}.js`],
        languageOptions: {
            ecmaVersion: 2022,
            sourceType: "script",
            globals: globalsFor(file),
        },
        rules: scriptRules,
    };
}

/** @type {import("eslint").Linter.Config[]} */
export default [
    staticBlock("accounts"),
    staticBlock("app"),
    staticBlock("boot"),
    staticBlock("i18n"),
    staticBlock("idempotency"),
    staticBlock("transfers"),
    {
        files: ["app/src/test/js/*.test.js"],
        languageOptions: {
            ecmaVersion: 2022,
            sourceType: "script",
            globals: {
                require: "readonly",
                module: "writable",
                exports: "writable",
                __dirname: "readonly",
                __filename: "readonly",
                process: "readonly",
                console: "readonly",
                Buffer: "readonly",
                setTimeout: "readonly",
                clearTimeout: "readonly",
                setInterval: "readonly",
                URL: "readonly",
                URLSearchParams: "readonly",
                TextEncoder: "readonly",
                AbortController: "readonly",
            },
        },
        rules: {
            "no-undef": "error",
            "no-unused-vars": ["error", { args: "after-used", caughtErrors: "none" }],
            "no-eval": "error",
        },
    },
];
