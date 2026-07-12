package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.domain.Transfer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ReadOnlyUseCase
public class GetTransferHistoryUseCaseImpl implements GetTransferHistoryQuery {

    private final LoadTransferPort loadTransferPort;
    private final AccountAclPort accountAclPort;
    private final TransferAuthorizationService transferAuthorizationService;

    public GetTransferHistoryUseCaseImpl(LoadTransferPort loadTransferPort,
                                     AccountAclPort accountAclPort,
                                     TransferAuthorizationService transferAuthorizationService) {
        this.loadTransferPort = loadTransferPort;
        this.accountAclPort = accountAclPort;
        this.transferAuthorizationService = transferAuthorizationService;
    }

    @Override
    public PageResponse<TransferResponse> execute(Long accountId) {
        return execute(accountId, 0, 20);
    }

    @Override
    public PageResponse<TransferResponse> execute(Long accountId, int page, int size) {
        Objects.requireNonNull(accountId, "Account ID must not be null");

        int cappedPage = Math.max(page, 0);
        int cappedSize = Math.max(Math.min(size, 100), 1);

        AccountInfo account = transferAuthorizationService.authorizeAccountAccess(accountId, "You are not authorized to view this account's transaction history.");

        List<Transfer> transfers = loadTransferPort.findHistory(accountId, cappedPage, cappedSize);

        Set<Long> accountIds = transfers.stream()
                .flatMap(t -> Stream.of(t.getSenderAccountId(), t.getReceiverAccountId()))
                .collect(Collectors.toSet());

        Map<Long, String> ibansMap = accountAclPort.getIbansForAccounts(accountIds);

        List<TransferResponse> items = transfers.stream()
                .map(transfer -> TransferResponse.from(
                        transfer,
                        ibansMap.get(transfer.getSenderAccountId()),
                        ibansMap.get(transfer.getReceiverAccountId())
                ))
                .collect(Collectors.toList());

        long totalItems = loadTransferPort.countHistory(accountId);

        return PageResponse.of(items, cappedPage, cappedSize, totalItems);
    }
}
