package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.ReadOnlyUseCase;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.bank.app.transfer.application.dto.PagedResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
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
    private final SecurityContextPort securityContextPort;

    public GetTransferHistoryUseCaseImpl(LoadTransferPort loadTransferPort,
                                     AccountAclPort accountAclPort,
                                     SecurityContextPort securityContextPort) {
        this.loadTransferPort = loadTransferPort;
        this.accountAclPort = accountAclPort;
        this.securityContextPort = securityContextPort;
    }

    @Override
    public PagedResponse<TransferResponse> execute(Long accountId) {
        return execute(accountId, 0, 20);
    }

    @Override
    public PagedResponse<TransferResponse> execute(Long accountId, int page, int size) {
        Objects.requireNonNull(accountId, "Account ID null olamaz");

        int cappedPage = Math.max(page, 0);
        int cappedSize = Math.max(Math.min(size, 100), 1);

        AccountInfo account = accountAclPort.getAccountInfo(accountId);

        securityContextPort.checkUserAuthorization(account.userId(), "Bu hesabın işlem geçmişini görme yetkiniz yok.");

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

        return new PagedResponse<>(items, cappedPage, cappedSize, totalItems);
    }
}
