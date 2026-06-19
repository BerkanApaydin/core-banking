package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort.AccountInfo;
import com.bank.app.transfer.application.dto.PagedResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.application.port.in.GetTransferHistoryPort;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class GetTransferHistoryUseCase implements GetTransferHistoryPort {

    private final LoadTransferPort loadTransferPort;
    private final AccountOperationPort AccountOperationPort;
    private final SecurityContextPort securityContextPort;

    public GetTransferHistoryUseCase(LoadTransferPort loadTransferPort,
                                     AccountOperationPort AccountOperationPort,
                                     SecurityContextPort securityContextPort) {
        this.loadTransferPort = loadTransferPort;
        this.AccountOperationPort = AccountOperationPort;
        this.securityContextPort = securityContextPort;
    }

    public PagedResponse<TransferResponse> execute(Long accountId) {
        return execute(accountId, 0, 20);
    }

    public PagedResponse<TransferResponse> execute(Long accountId, int page, int size) {
        Objects.requireNonNull(accountId, "Account ID null olamaz");

        int cappedPage = Math.max(page, 0);
        int cappedSize = Math.min(size, 100);

        AccountInfo account = AccountOperationPort.getAccountInfo(accountId);

        securityContextPort.checkUserAuthorization(account.userId(), "Bu hesabın işlem geçmişini görme yetkiniz yok.");

        List<Transfer> transfers = loadTransferPort.findHistory(accountId, cappedPage, cappedSize);

        Set<Long> accountIds = transfers.stream()
                .flatMap(t -> Stream.of(t.getSenderAccountId(), t.getReceiverAccountId()))
                .collect(Collectors.toSet());

        Map<Long, String> ibansMap = AccountOperationPort.getIbansForAccounts(accountIds);

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
