package com.bank.app.transfer.domain;

import java.util.Objects;

public class TransferCompletedEvent {
    private final Transfer transfer;

    public TransferCompletedEvent(Transfer transfer) {
        this.transfer = Objects.requireNonNull(transfer, "Transfer null olamaz");
    }

    public Transfer getTransfer() {
        return transfer;
    }
}
