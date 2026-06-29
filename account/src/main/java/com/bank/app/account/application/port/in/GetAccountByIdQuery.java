package com.bank.app.account.application.port.in;

import com.bank.app.account.application.dto.AccountResponse;

public interface GetAccountByIdQuery {
    AccountResponse execute(Long id);
}
