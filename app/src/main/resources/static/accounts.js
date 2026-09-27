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
function renderFundingMode() {
    const balanceInput = document.getElementById('acc-balance');
    const hint = document.getElementById('acc-funding-hint');
    if (!balanceInput || !hint) return;
    balanceInput.readOnly = fundingMode !== 'enabled';
    if (balanceInput.readOnly) balanceInput.value = '0.00';
    const hintKey = fundingMode === 'enabled' ? 'account.funding_enabled'
        : fundingMode === 'disabled' ? 'account.funding_disabled'
        : fundingMode === 'unavailable' ? 'account.funding_unavailable'
        : 'account.funding_checking';
    hint.textContent = __(hintKey);
}

async function loadAccountCapabilities() {
    fundingMode = 'unknown';
    renderFundingMode();
    try {
        const capability = await fetchApi('/accounts/capabilities');
        if (!authenticated) return;
        fundingMode = capability.initialFundingEnabled === true ? 'enabled' : 'disabled';
    } catch (error) {
        if (!authenticated) return;
        fundingMode = 'unavailable';
    }
    renderFundingMode();
}

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
        renderFundingMode();
        const firstField = document.getElementById('acc-account-name');
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

    // Handle Form Submit
    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        const ownerName = document.getElementById('acc-account-name').value.trim();
        // Round to 2 decimals: backend @Digits(integer=13, fraction=2).
        const balance = Math.round(parseFloat(document.getElementById('acc-balance').value) * 100) / 100;
        const currency = document.getElementById('acc-currency').value;

        if (!Number.isFinite(balance) || balance < 0) {
            showAlert(__('account.invalid_balance'), 'danger');
            return;
        }

        if (fundingMode !== 'enabled' && balance > 0) {
            showAlert(__('account.funding_disabled'), 'danger');
            return;
        }
        spinner.classList.remove('d-none');
        submitBtn.disabled = true;
        submitBtn.setAttribute('aria-busy', 'true');

        const request = { ownerName, initialBalance: balance, currency };
        const keyName = `accountKey:${userId}`;
        let started = false;
        try {
            const idempotencyKey = await getIdempotencyKey(keyName, request);
            if (!idempotencyKey) return;
            markIdempotencyStarted(keyName);
            started = true;
            const createdAccount = await fetchApi('/accounts', {
                method: 'POST',
                headers: {
                    'Idempotency-Key': idempotencyKey
                },
                body: JSON.stringify(request)
            });

            clearIdempotencyKey(keyName);
            showAlert(__('account.created', createdAccount.iban));
            closeModal();
            loadAccounts();
        } catch (err) {
            if (started) markIdempotencyFailed(keyName, err);
            showAlert(started ? idempotencyErrorMessage(err) : err.message, 'danger');
        } finally {
            spinner.classList.add('d-none');
            submitBtn.disabled = false;
            submitBtn.removeAttribute('aria-busy');
        }
    });
}

