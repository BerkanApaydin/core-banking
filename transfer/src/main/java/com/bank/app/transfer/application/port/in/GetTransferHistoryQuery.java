package com.bank.app.transfer.application.port.in;

import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.transfer.application.dto.TransferResponse;

public interface GetTransferHistoryQuery {
    default PageResponse<TransferResponse> execute(Long accountId) {
        return execute(accountId, 0, 20);
    }
    PageResponse<TransferResponse> execute(Long accountId, int page, int size);
}
