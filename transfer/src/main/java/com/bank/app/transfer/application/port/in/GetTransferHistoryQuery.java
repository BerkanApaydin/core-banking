package com.bank.app.transfer.application.port.in;

import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.transfer.application.dto.TransferResponse;

public interface GetTransferHistoryQuery {
    PageResponse<TransferResponse> execute(Long accountId, int page, int size);
}
