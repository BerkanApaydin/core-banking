package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TransferJpaMapper {

    public TransferJpaEntity toJpaEntity(Transfer transfer) {
        if (transfer == null) {
            throw new IllegalArgumentException("Transfer must not be null");
        }
        TransferJpaEntity entity = new TransferJpaEntity(
                transfer.getId(),
                transfer.getSenderAccountId(),
                transfer.getReceiverAccountId(),
                transfer.getAmount().amount(),
                transfer.getAmount().currency().name(),
                transfer.getStatus().name(),
                transfer.getVersion()
        );
        entity.setBusinessCreatedAt(transfer.getCreatedAt());
        return entity;
    }

    public Transfer toDomain(TransferJpaEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity must not be null");
        }
        LocalDateTime createdAt = entity.getBusinessCreatedAt() != null
                ? entity.getBusinessCreatedAt()
                : entity.getCreatedAt();
        return new Transfer(
                entity.getId(),
                entity.getSenderAccountId(),
                entity.getReceiverAccountId(),
                Money.of(entity.getAmount(), Currency.valueOf(entity.getCurrency())),
                TransferStatus.valueOf(entity.getStatus()),
                createdAt,
                entity.getVersion()
        );
    }

    public void updateJpaEntity(TransferJpaEntity entity, Transfer transfer) {
        if (entity == null || transfer == null) {
            throw new IllegalArgumentException("Entity and Transfer must not be null");
        }
        entity.setStatus(transfer.getStatus().name());
        entity.setVersion(transfer.getVersion());
    }
}
