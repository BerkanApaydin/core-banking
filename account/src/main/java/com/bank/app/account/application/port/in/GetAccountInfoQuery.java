package com.bank.app.account.application.port.in;

import com.bank.app.account.application.dto.AccountInfo;
import java.util.Collection;
import java.util.Map;

public interface GetAccountInfoQuery {
    AccountInfo getAccountInfo(Long accountId);
    AccountInfo getAccountInfoForTransfer(String ibanValue);
    Map<Long, String> getIbansForAccounts(Collection<Long> accountIds);
}
