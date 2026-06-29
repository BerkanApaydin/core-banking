package com.bank.app.account.application.port.in;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;

public interface CreateAccountUseCase {
    AccountResponse execute(CreateAccountRequest request);
}
