package com.bank.app.transfer.application.port;

import com.bank.app.transfer.domain.Transfer;

public interface SaveTransferPort {
    Transfer save(Transfer transfer);
}
