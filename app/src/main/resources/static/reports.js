/* exported initReportSection, populateReportDropdown */
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

    // Last 30 UTC calendar days, including today (the report API compares UTC
    // LocalDateTime bounds, so the picker must not follow the browser zone).
    const now = new Date();
    const thirtyDaysAgo = new Date(now.getTime());
    thirtyDaysAgo.setUTCDate(now.getUTCDate() - 29);

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
        reportPager.cursors = [null];
        loadReportPage(0);
    });
    document.getElementById('report-page-previous').addEventListener('click', () => loadReportPage(reportPager.page - 1));
    document.getElementById('report-page-next').addEventListener('click', () => loadReportPage(reportPager.page + 1));
}

function invalidateGeneratedReport() {
    reportPager = { criteria: null, page: 0, hasNext: false, loading: false,
        requestVersion: reportPager.requestVersion + 1, cursors: [null] };
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
        const cursor = reportCursorForPage(reportPager.cursors, page);
        const query = buildReportQuery(criteria, page, PAGE_SIZE, cursor);
        const report = await fetchApi(`/transfers/report?${query}`, { etagCache: true });
        if (requestVersion !== reportPager.requestVersion) return;
        reportPager.page = page;
        reportPager.hasNext = report.hasNext === true;
        reportPager.cursors = storeReportCursor(reportPager.cursors, page, report);
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

// Monotonic generation token: switching accounts (or a cancelled transfer
// refresh) can leave an older history response in flight. Only the newest
// request may render.
let historyRequestVersion = 0;

async function loadAccountHistory(accountId, loadMore = false) {
    const historyList = document.getElementById('transaction-history-list');
    const selectedAcc = accounts.find(a => a.id == accountId);
    if (!selectedAcc) return;
    const requestVersion = ++historyRequestVersion;

    const sameAccount = historyPager.accountId !== null && String(historyPager.accountId) === String(accountId);
    if (!loadMore || !sameAccount) {
        historyPager = { accountId, page: 0, last: true, total: 0 };
        historyCache = { accountId, items: [] };
        historyList.innerHTML = `<div class="empty-state"><span class="spinner"></span><p>${__('report.history_loading')}</p></div>`;
    }
    const page = (loadMore && sameAccount) ? historyPager.page + 1 : 0;

    try {
        const response = await fetchApi(`/transfers/history/${accountId}?page=${page}&size=${PAGE_SIZE}`);
        if (requestVersion !== historyRequestVersion) return;
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
        if (requestVersion !== historyRequestVersion) return;
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

