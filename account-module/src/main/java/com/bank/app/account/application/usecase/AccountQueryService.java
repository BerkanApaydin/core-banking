package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.AccountQueryPort;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.common.domain.Iban;
import com.bank.app.account.application.exception.AccountNotFoundException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
public class AccountQueryService implements AccountQueryPort {

    private final LoadAccountPort loadAccountPort;

    public AccountQueryService(LoadAccountPort loadAccountPort) {
        this.loadAccountPort = loadAccountPort;
    }

    @Override
    public AccountInfo getAccountInfo(Long accountId) {
        Account account = loadAccountPort.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return toAccountInfo(account);
    }

    @Override
    public AccountInfo getAccountInfoForTransfer(String ibanValue) {
        Iban iban = new Iban(ibanValue);
        Account account = loadAccountPort.findByIban(iban)
                .orElseThrow(() -> new AccountNotFoundException(ibanValue));
        return toAccountInfo(account);
    }

    @Override
    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        return loadAccountPort.findByIds(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, a -> a.getIban().value()));
    }

    private AccountInfo toAccountInfo(Account account) {
        return new AccountInfo(
                Objects.requireNonNull(account.getId()),
                Objects.requireNonNull(account.getUserId()),
                Objects.requireNonNull(account.getBalance().currency().name()),
                account.getStatus());
    }
}
