package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import java.time.LocalDateTime;

public record AsyncTransferCompletedEvent(
    Long transferId,
    Long senderAccountId,
    Long receiverAccountId,
    Money amount,
    TransferStatus status,
    LocalDateTime createdAt
) {
    public static AsyncTransferCompletedEvent from(Transfer transfer) {
        return new AsyncTransferCompletedEvent(
            transfer.getId(),
            transfer.getSenderAccountId(),
            transfer.getReceiverAccountId(),
            transfer.getAmount(),
            transfer.getStatus(),
            transfer.getCreatedAt()
        );
    }
}
