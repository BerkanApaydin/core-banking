package com.bank.app.transfer.application.port.in;

import com.bank.app.transfer.application.dto.TransferDetailResponse;

public interface GetTransferDetailPort {
    TransferDetailResponse execute(Long transferId);
}
