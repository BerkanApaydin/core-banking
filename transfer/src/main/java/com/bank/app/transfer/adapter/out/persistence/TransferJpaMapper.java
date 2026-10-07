package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
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
                transfer.getAmount().currency(),
                transfer.getStatus(),
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
                Money.exact(entity.getAmount(), entity.getCurrency()),
                entity.getStatus(),
                createdAt,
                entity.getVersion()
        );
    }

    public void updateJpaEntity(TransferJpaEntity entity, Transfer transfer) {
        if (entity == null || transfer == null) {
            throw new IllegalArgumentException("Entity and Transfer must not be null");
        }
        entity.setStatus(transfer.getStatus());
        // The managed version belongs to Hibernate; the adapter checks the expected version.
    }
}
