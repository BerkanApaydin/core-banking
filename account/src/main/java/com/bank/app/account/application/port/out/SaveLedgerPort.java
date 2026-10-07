package com.bank.app.account.application.port.out;

import com.bank.app.account.domain.LedgerEntry;

public interface SaveLedgerPort {

    LedgerEntry save(LedgerEntry entry);
}
