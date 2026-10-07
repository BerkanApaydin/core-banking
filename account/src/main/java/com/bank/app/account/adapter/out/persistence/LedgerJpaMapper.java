package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.domain.LedgerEntry;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import org.springframework.stereotype.Component;

@Component
public class LedgerJpaMapper {

    public LedgerEntryJpaEntity toJpaEntity(LedgerEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        LedgerEntryJpaEntity entity = new LedgerEntryJpaEntity();
        entity.setId(entry.getId());
        entity.setTransactionRef(entry.getTransactionRef());
        entity.setAccountId(entry.getAccountId());
        entity.setDirection(entry.getDirection());
        entity.setAmount(entry.getAmount().amount());
        entity.setCurrency(entry.getAmount().currency());
        entity.setBalanceAfter(entry.getBalanceAfter().amount());
        entity.setBusinessAt(entry.getOccurredAt());
        return entity;
    }

    public LedgerEntry toDomain(LedgerEntryJpaEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        Currency currency = entity.getCurrency();
        return new LedgerEntry(
                entity.getId(),
                entity.getTransactionRef(),
                entity.getAccountId(),
                entity.getDirection(),
                Money.exact(entity.getAmount(), currency),
                Money.exact(entity.getBalanceAfter(), currency),
                entity.getBusinessAt());
    }
}
