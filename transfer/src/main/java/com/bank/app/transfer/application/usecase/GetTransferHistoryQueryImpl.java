package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferViewEnricher;
import com.bank.app.common.domain.AccountId;
import java.util.List;
import java.util.Objects;

@ReadOnlyUseCase
public class GetTransferHistoryQueryImpl implements GetTransferHistoryQuery {

    /**
     * Maximum offset window served by offset pagination. Single-sourced from
     * {@link ReportCriteria#MAX_OFFSET_WINDOW} so history and report share one
     * budget; callers beyond it must use the keyset report endpoint.
     */
    static final long MAX_OFFSET_WINDOW = ReportCriteria.MAX_OFFSET_WINDOW;

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
    public PageResponse<TransferResponse> execute(Long accountId, int page, int size) {
        Objects.requireNonNull(accountId, "Account ID must not be null");

        int cappedPage = Math.max(page, 0);
        int cappedSize = Math.max(Math.min(size, maxPageSize), 1);
        if ((long) cappedPage * cappedSize > MAX_OFFSET_WINDOW) {
            throw new IllegalArgumentException(
                    "History window exceeds " + MAX_OFFSET_WINDOW
                            + " rows; use keyset pagination via GET /transfers/report "
                            + "with cursorCreatedAt+cursorId");
        }

        AccountInfo account = transferAuthorizationService.authorizeAccountAccess(new AccountId(accountId),
                "You are not authorized to view this account's transaction history.");

        // Single range scan serves both the page and its exact total (the
        // port over-fetches nothing here; the window count rides along).
        LoadTransferPort.HistoryPage historyPage = loadTransferPort.findHistoryPage(accountId, cappedPage, cappedSize);

        List<TransferResponse> items = viewEnricher.enrich(historyPage.items());

        return PageResponse.of(items, cappedPage, cappedSize, historyPage.totalElements());
    }
}
