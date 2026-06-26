package com.bank.app.account.application.port.in;

import java.util.Collection;
import java.util.Map;

public interface AccountQueryUseCase {
    AccountInfo getAccountInfo(Long accountId);
    AccountInfo getAccountInfoForTransfer(String ibanValue);
    Map<Long, String> getIbansForAccounts(Collection<Long> accountIds);
}
