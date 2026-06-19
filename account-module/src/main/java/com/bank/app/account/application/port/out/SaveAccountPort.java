package com.bank.app.account.application.port.out;

import com.bank.app.account.domain.Account;

public interface SaveAccountPort {
    Account save(Account account);
}
