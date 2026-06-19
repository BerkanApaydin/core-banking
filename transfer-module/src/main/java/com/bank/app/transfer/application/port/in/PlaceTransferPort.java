package com.bank.app.transfer.application.port.in;

import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;

public interface PlaceTransferPort {
    TransferResponse execute(TransferRequest request);
}
