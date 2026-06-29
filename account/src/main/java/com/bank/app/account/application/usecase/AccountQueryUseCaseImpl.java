package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.AccountQueryUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.common.domain.Iban;
import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.common.application.ReadOnlyUseCase;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;

@ReadOnlyUseCase
public class AccountQueryUseCaseImpl implements AccountQueryUseCase {
    private final LoadAccountPort loadAccountPort;

    public AccountQueryUseCaseImpl(LoadAccountPort loadAccountPort) {
        this.loadAccountPort = loadAccountPort;
    }

    @Override
    @Cacheable(value = "accountInfo", key = "'id-' + #accountId", unless = "#result == null")
    public AccountInfo getAccountInfo(Long accountId) {
        Account account = loadAccountPort.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return toAccountInfo(account);
    }

    @Override
    @Cacheable(value = "accountInfo", key = "'iban-' + #ibanValue", unless = "#result == null")
    public AccountInfo getAccountInfoForTransfer(String ibanValue) {
        Iban iban = new Iban(ibanValue);
        Account account = loadAccountPort.findByIban(iban).orElseThrow(() -> new AccountNotFoundException(ibanValue));
        return toAccountInfo(account);
    }

    @Override
    @Cacheable(value = "accountInfo", key = "'ibans-' + #accountIds.hashCode()", unless = "#result == null or #result.isEmpty()")
    public Map<Long, String> getIbansForAccounts(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        return loadAccountPort.findByIds(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, a -> a.getIban().value()));
    }

    private AccountInfo toAccountInfo(Account account) {
        return new AccountInfo(Objects.requireNonNull(account.getId()),
                Objects.requireNonNull(account.getUserId()).value(),
                Objects.requireNonNull(account.getBalance().currency().name()), account.getStatus().name());
    }
}