package com.bank.app.account.adapter.in.api;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.AccountQueryUseCase;
import com.bank.app.account.application.port.in.AdjustAccountBalancesUseCase;
import com.bank.app.accountapi.AccountNotFoundException;
import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.accountapi.AccountApi;
import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.common.domain.Money;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Inbound API adapter: implements the {@code account-api} published language
 * (Open Host Service) by delegating to the account application ports.
 * This is the only bridge downstream contexts use — they program against
 * {@link AccountApi}, never against account use-cases or domain types.
 */
@Component
public class AccountApiAdapter implements AccountApi {

    private final AccountQueryUseCase accountQueryUseCase;
    private final AdjustAccountBalancesUseCase adjustAccountBalancesUseCase;

    public AccountApiAdapter(AccountQueryUseCase accountQueryUseCase,
            AdjustAccountBalancesUseCase adjustAccountBalancesUseCase) {
        this.accountQueryUseCase = accountQueryUseCase;
        this.adjustAccountBalancesUseCase = adjustAccountBalancesUseCase;
    }

    @Override
    public AccountSnapshot getSnapshotById(Long accountId) {
        try {
            return toSnapshot(accountQueryUseCase.getAccountInfo(accountId));
        } catch (com.bank.app.account.domain.exception.AccountNotFoundException e) {
            // Translate to the published language: downstream contexts must
            // never observe account domain types, not even as exceptions.
            throw new AccountNotFoundException(accountId);
        }
    }

    @Override
    public AccountSnapshot getSnapshotByIban(String ibanValue) {
        try {
            return toSnapshot(accountQueryUseCase.getAccountInfoForTransfer(ibanValue));
        } catch (com.bank.app.account.domain.exception.AccountNotFoundException e) {
            throw new AccountNotFoundException(ibanValue);
        }
    }

    @Override
    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        return accountQueryUseCase.getIbansForAccounts(accountIds);
    }

    @Override
    public AccountAdjustmentResult adjustBalances(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        return adjustAccountBalancesUseCase.debitAndCredit(senderId, receiverId, amount);
    }

    @Override
    public AccountAdjustmentResult reverseForCancellation(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        return adjustAccountBalancesUseCase.reverseForCancellation(senderId, receiverId, amount);
    }

    private static AccountSnapshot toSnapshot(AccountInfo info) {
        return new AccountSnapshot(info.id(), info.userId(), info.currency(), info.status());
    }
}
