package com.bank.app.transfer.application.port.in;

import com.bank.app.transfer.application.dto.PagedResponse;
import com.bank.app.transfer.application.dto.TransferResponse;

import java.time.LocalDateTime;

public interface GetTransferHistoryPort {
    PagedResponse<TransferResponse> execute(Long accountId, int page, int size);
}
