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
        if (submitBtn.disabled) return;

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

        const request = {
            senderIban: senderAcc.iban,
            receiverIban,
            amount,
            currency
        };
        const keyName = `transferKey:${userId}`;
        let started = false;
        spinner.classList.remove('d-none');
        submitBtn.disabled = true;
        submitBtn.setAttribute('aria-busy', 'true');
        try {
            const idempotencyKey = await getIdempotencyKey(keyName, request);
            if (!idempotencyKey) return;
            markIdempotencyStarted(keyName);
            started = true;
            const result = await fetchApi('/transfers', {
                method: 'POST',
                headers: { 'Idempotency-Key': idempotencyKey },
                body: JSON.stringify(request)
            });
            clearIdempotencyKey(keyName);
            showAlert(`${__('transfer.success')} (${__('transfer.amount')}: ${formatMoney(result.amount)} ${escapeHtml(result.currency)})`);
            form.reset();
            document.getElementById('sender-balance-indicator').textContent = '';
            await loadAccounts();
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

