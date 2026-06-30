package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.springframework.stereotype.Component;

@Component
public class TransferJpaMapper {

    public TransferJpaEntity toJpaEntity(Transfer transfer) {
        if (transfer == null) {
            throw new IllegalArgumentException("Transfer must not be null");
        }
        return new TransferJpaEntity(
                transfer.getId(),
                transfer.getSenderAccountId(),
                transfer.getReceiverAccountId(),
                transfer.getAmount().amount(),
                transfer.getAmount().currency().name(),
                transfer.getStatus().name(),
                transfer.getVersion()
        );
    }

    public Transfer toDomain(TransferJpaEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity must not be null");
        }
        return new Transfer(
                entity.getId(),
                entity.getSenderAccountId(),
                entity.getReceiverAccountId(),
                Money.of(entity.getAmount(), Currency.valueOf(entity.getCurrency())),
                TransferStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
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
