package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.AccountTransferOperationPort;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.common.domain.Money;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.Map;

/**
 * Backward-compatible facade that delegates to the refactored services.
 * @deprecated Use {@link AccountQueryService} and {@link AccountTransferOperationPort} directly.
 */
@Deprecated
public class AccountInternalService {

    private final AccountQueryService queryService;
    private final AccountTransferOperationPort transferOperation;

    public AccountInternalService(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort,
                                  SecurityContextPort securityContextPort) {
        this.queryService = new AccountQueryService(loadAccountPort);
        this.transferOperation = new AccountTransferOperationUseCase(loadAccountPort, saveAccountPort, securityContextPort);
    }

    public AccountInfo getAccountInfo(@NonNull Long accountId) {
        return queryService.getAccountInfo(accountId);
    }

    public AccountInfo getAccountInfoForTransfer(String ibanValue) {
        return queryService.getAccountInfoForTransfer(ibanValue);
    }

    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        return queryService.getIbansForAccounts(accountIds);
    }

    public void debitAndCredit(@NonNull Long senderId, @NonNull Long receiverId, @NonNull Money amount) {
        transferOperation.executeTransfer(senderId, receiverId, amount);
    }

    public void reverseBalancesForCancellation(@NonNull Long senderId, @NonNull Long receiverId,
            @NonNull Money amount) {
        transferOperation.reverseTransfer(senderId, receiverId, amount);
    }
}
