package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import java.util.Objects;

public class TransferCancelledEvent {
    private final Long transferId;
    private final Long senderAccountId;
    private final Long receiverAccountId;
    private final Money amount;

    public TransferCancelledEvent(Long transferId, Long senderAccountId, Long receiverAccountId, Money amount) {
        this.transferId = Objects.requireNonNull(transferId, "transferId null olamaz");
        this.senderAccountId = Objects.requireNonNull(senderAccountId, "senderAccountId null olamaz");
        this.receiverAccountId = Objects.requireNonNull(receiverAccountId, "receiverAccountId null olamaz");
        this.amount = Objects.requireNonNull(amount, "amount null olamaz");
    }

    public Long getTransferId() { return transferId; }
    public Long getSenderAccountId() { return senderAccountId; }
    public Long getReceiverAccountId() { return receiverAccountId; }
    public Money getAmount() { return amount; }
}
