package com.bank.app.account.application.port.in;

import com.bank.app.account.application.dto.AccountResponse;

import java.util.List;

public interface GetAccountQuery {
    AccountResponse getById(Long id);
    AccountResponse getByIban(String ibanValue);
    List<AccountResponse> getAll();
}
