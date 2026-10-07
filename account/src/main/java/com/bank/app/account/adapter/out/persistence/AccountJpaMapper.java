package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.domain.Account;
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
                account.getBalance().currency(),
                account.getStatus(),
                account.getVersion()
        );
    }

    /**
     * Mutates a managed entity in place (7.1): same pattern as
     * {@code TransferPersistenceAdapter} — no detached merge, hence no
     * implicit pre-update SELECT. Identity fields (id, userId, iban) and the
     * managed version are never overwritten; Hibernate owns the version.
     */
    public void updateJpaEntity(AccountJpaEntity entity, Account account) {
        if (entity == null || account == null) {
            throw new IllegalArgumentException("Entity and account must not be null");
        }
        entity.setOwnerName(account.getOwnerName());
        entity.setBalance(account.getBalance().amount());
        entity.setCurrency(account.getBalance().currency());
        entity.setStatus(account.getStatus());
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
                .balance(Money.exact(entity.getBalance(), entity.getCurrency()))
                .status(entity.getStatus())
                .version(entity.getVersion())
                .build();
    }
}
