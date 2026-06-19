package com.bank.app.transfer.application.port.in;

import com.bank.app.transfer.application.dto.PagedResponse;
import com.bank.app.transfer.application.dto.TransferResponse;

public interface GetTransferHistoryPort {
    PagedResponse<TransferResponse> execute(Long accountId, int page, int size);
}
