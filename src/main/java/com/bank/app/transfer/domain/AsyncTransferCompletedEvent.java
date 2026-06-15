package com.bank.app.transfer.domain;

import org.springframework.context.ApplicationEvent;

public class AsyncTransferCompletedEvent extends ApplicationEvent {
    private final Transfer transfer;

    public AsyncTransferCompletedEvent(Transfer transfer) {
        super(transfer);
        this.transfer = transfer;
    }

    public Transfer getTransfer() {
        return transfer;
    }
}
