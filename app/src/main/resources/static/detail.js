/* exported confirmDialog, detailTransferId, initDetailModals, lookupManualIban, openAccountDetail, openTransferDetail, renderReportResults */
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
        const acc = await fetchApi(`/accounts/${accountId}`, { etagCache: true });
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
        const t = await fetchApi(`/transfers/${transferId}`, { etagCache: true });
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
        // accounts (other-owned and missing share one 404, G-5), so a hit
        // means "one of my accounts". Anything else stays silent by design
        // — no existence oracle.
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

