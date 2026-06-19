package com.bank.app.transfer.application.port.out;

import com.bank.app.transfer.domain.Transfer;

public interface SaveTransferPort {
    Transfer save(Transfer transfer);
}
