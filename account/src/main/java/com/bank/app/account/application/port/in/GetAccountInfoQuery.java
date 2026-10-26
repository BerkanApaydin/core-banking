package com.bank.app.account.application.port.in;

import com.bank.app.account.application.dto.AccountView;
import java.util.Collection;
import java.util.Map;

public interface GetAccountInfoQuery {
    AccountView getAccountInfo(Long accountId);
    AccountView getAccountInfoForTransfer(String ibanValue);
    Map<Long, String> getIbansForAccounts(Collection<Long> accountIds);
}
