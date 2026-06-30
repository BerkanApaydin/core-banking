package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import org.springframework.stereotype.Component;

@Component
public class AccountJpaMapper {

    public AccountJpaEntity toJpaEntity(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("account must not be null");
        }
        return new AccountJpaEntity(
                account.getId(),
                account.getUserId().value(),
                Iban.normalize(account.getIban().value()),
                account.getOwnerName(),
                account.getBalance().amount(),
                account.getBalance().currency().name(),
                account.getStatus().name(),
                account.getVersion()
        );
    }

    public Account toDomain(AccountJpaEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        return Account.builder()
                .id(entity.getId())
                .userId(new UserId(entity.getUserId()))
                .iban(new Iban(entity.getIban()))
                .ownerName(entity.getOwnerName())
                .balance(Money.of(entity.getBalance(), Currency.valueOf(entity.getCurrency())))
                .status(AccountStatus.valueOf(entity.getStatus()))
                .version(entity.getVersion())
                .build();
    }
}
