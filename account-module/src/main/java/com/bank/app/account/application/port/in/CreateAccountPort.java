package com.bank.app.account.application.port.in;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;

public interface CreateAccountPort {
    AccountResponse execute(CreateAccountRequest request);
}
