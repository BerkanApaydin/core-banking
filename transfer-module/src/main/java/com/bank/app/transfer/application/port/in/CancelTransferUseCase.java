package com.bank.app.transfer.application.port.in;

public interface CancelTransferUseCase {
    void execute(Long transferId);
}
