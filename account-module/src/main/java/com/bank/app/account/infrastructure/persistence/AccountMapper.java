package com.bank.app.account.infrastructure.persistence;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    private static final Logger log = LoggerFactory.getLogger(AccountMapper.class);

    @Nullable
    public Account toDomain(@Nullable AccountJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        try {
            return new Account(
                    entity.getId(),
                    entity.getUserId(),
                    new Iban(entity.getIban()),
                    entity.getOwnerName(),
                    new Money(entity.getBalance(), Currency.valueOf(entity.getCurrency())),
                    AccountStatus.valueOf(entity.getStatus()),
                    entity.getVersion()
            );
        } catch (Exception e) {
            log.warn("Account mapping failed for id={}: {}", entity.getId(), e.getMessage());
            return null;
        }
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
