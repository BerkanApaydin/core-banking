package com.bank.app.account.application.port.in;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.common.application.dto.PageResponse;

public interface GetAccountsByUserQuery {
    PageResponse<AccountResponse> execute(int page, int size);
}
