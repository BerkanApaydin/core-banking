package com.bank.app.transfer.application.port.in;

import com.bank.app.transfer.application.dto.PagedResponse;
import com.bank.app.transfer.application.dto.TransferResponse;

public interface GetTransferHistoryQuery {
    default PagedResponse<TransferResponse> execute(Long accountId) {
        return execute(accountId, 0, 20);
    }
    PagedResponse<TransferResponse> execute(Long accountId, int page, int size);
}
