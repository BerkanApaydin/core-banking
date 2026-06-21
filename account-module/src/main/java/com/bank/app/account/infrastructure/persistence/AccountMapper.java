package com.bank.app.account.infrastructure.persistence;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    @Nullable
    public Account toDomain(@Nullable AccountJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Account(
                entity.getId(),
                entity.getUserId(),
                new Iban(entity.getIban()),
                entity.getOwnerName(),
                new Money(entity.getBalance(), Currency.valueOf(entity.getCurrency())),
                AccountStatus.valueOf(entity.getStatus()),
                entity.getVersion()
        );
    }

    @Nullable
    public AccountJpaEntity toJpaEntity(@Nullable Account domain) {
        if (domain == null) {
            return null;
        }
        return new AccountJpaEntity(
                domain.getId(),
                domain.getUserId(),
                domain.getIban().value(),
                domain.getOwnerName(),
                domain.getBalance().amount(),
                domain.getBalance().currency().name(),
                domain.getStatus().name(),
                domain.getVersion()
        );
    }
}
