package com.bank.app.account.application.port;

import com.bank.app.account.domain.Account;

public interface SaveAccountPort {
    void save(Account account);
}
