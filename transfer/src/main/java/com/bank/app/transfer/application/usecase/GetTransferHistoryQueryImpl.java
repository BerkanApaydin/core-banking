package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferViewEnricher;
import com.bank.app.transfer.domain.Transfer;
import java.util.List;
import java.util.Objects;

@ReadOnlyUseCase
public class GetTransferHistoryQueryImpl implements GetTransferHistoryQuery {

    private final LoadTransferPort loadTransferPort;
    private final TransferViewEnricher viewEnricher;
    private final TransferAuthorizationService transferAuthorizationService;
    private final int maxPageSize;

    public GetTransferHistoryQueryImpl(LoadTransferPort loadTransferPort,
                                     TransferViewEnricher viewEnricher,
                                     TransferAuthorizationService transferAuthorizationService,
                                     int maxPageSize) {
        this.loadTransferPort = loadTransferPort;
        this.viewEnricher = viewEnricher;
        this.transferAuthorizationService = transferAuthorizationService;
        this.maxPageSize = maxPageSize > 0 ? maxPageSize : 100;
    }

    @Override
    public PageResponse<TransferResponse> execute(Long accountId) {
        return execute(accountId, 0, 20);
    }

    @Override
    public PageResponse<TransferResponse> execute(Long accountId, int page, int size) {
        Objects.requireNonNull(accountId, "Account ID must not be null");

        int cappedPage = Math.max(page, 0);
        int cappedSize = Math.max(Math.min(size, maxPageSize), 1);

        AccountInfo account = transferAuthorizationService.authorizeAccountAccess(accountId, "You are not authorized to view this account's transaction history.");

        List<Transfer> transfers = loadTransferPort.findHistory(accountId, cappedPage, cappedSize);

        List<TransferResponse> items = viewEnricher.enrich(transfers);

        long totalItems = loadTransferPort.countHistory(accountId);

        return PageResponse.of(items, cappedPage, cappedSize, totalItems);
    }
}
