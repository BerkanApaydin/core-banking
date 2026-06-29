package com.bank.app.account.application.port.in;

import com.bank.app.account.application.dto.AccountResponse;
import java.util.List;

public interface GetAccountsByUserQuery {
    List<AccountResponse> execute(int page, int size);
}
