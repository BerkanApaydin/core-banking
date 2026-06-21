package com.bank.app.transfer.infrastructure.persistence;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.domain.Transfer;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class TransferMapper {

    @Nullable
    public Transfer toDomain(@Nullable TransferJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Transfer(
                entity.getId(),
                entity.getSenderAccountId(),
                entity.getReceiverAccountId(),
                new Money(entity.getAmount(), Currency.valueOf(entity.getCurrency())),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getVersion());
    }

    @Nullable
    public TransferJpaEntity toJpaEntity(@Nullable Transfer domain) {
        if (domain == null) {
            return null;
        }
        TransferJpaEntity entity = new TransferJpaEntity(
                domain.getId(),
                domain.getSenderAccountId(),
                domain.getReceiverAccountId(),
                domain.getAmount().amount(),
                domain.getAmount().currency().name(),
                domain.getStatus(),
                domain.getCreatedAt());
        entity.setVersion(domain.getVersion());
        return entity;
    }
}
