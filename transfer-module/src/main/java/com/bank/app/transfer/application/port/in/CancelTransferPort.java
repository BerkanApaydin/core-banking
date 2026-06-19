package com.bank.app.transfer.application.port.in;

public interface CancelTransferPort {
    void execute(Long transferId);
}
