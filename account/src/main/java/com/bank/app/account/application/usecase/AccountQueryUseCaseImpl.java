package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.AccountQueryUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.common.domain.Iban;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@ReadOnlyUseCase
public class AccountQueryUseCaseImpl implements AccountQueryUseCase {
    private final LoadAccountPort loadAccountPort;

    public AccountQueryUseCaseImpl(LoadAccountPort loadAccountPort) {
        this.loadAccountPort = loadAccountPort;
    }

    @Override
    public AccountInfo getAccountInfo(Long accountId) {
        // S9: lookup differs, the orElseThrow+map shape does not.
        // Projection read (no aggregate hydration): authorization paths need
        // only id/owner/currency/status, never the balance.
        return findOrThrow(loadAccountPort.findInfoById(accountId),
                () -> new AccountNotFoundException(accountId));
    }

    @Override
    public AccountInfo getAccountInfoForTransfer(String ibanValue) {
        Iban iban = new Iban(ibanValue);
        return findOrThrow(loadAccountPort.findInfoByIban(iban),
                () -> new AccountNotFoundException(ibanValue));
    }

    private AccountInfo findOrThrow(Optional<AccountInfo> lookup,
                                    Supplier<AccountNotFoundException> missing) {
        return lookup.orElseThrow(missing);
    }

    @Override
    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        // Id-to-IBAN pairs straight from the projection: batch enrichment
        // without hydrating one aggregate per row.
        return loadAccountPort.findIbansByIds(accountIds);
    }
}
