package com.bank.app.transfer.application.usecase;

import com.bank.app.account.application.usecase.AccountInternalService;
import com.bank.app.account.application.usecase.AccountInternalService.AccountInfo;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.common.security.SecurityUtils;
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
public class GetTransferHistoryUseCase {

    private final LoadTransferPort loadTransferPort;
    private final AccountInternalService accountInternalService;
    private final SecurityUtils securityUtils;

    public GetTransferHistoryUseCase(LoadTransferPort loadTransferPort, 
                                     AccountInternalService accountInternalService,
                                     SecurityUtils securityUtils) {
        this.loadTransferPort = loadTransferPort;
        this.accountInternalService = accountInternalService;
        this.securityUtils = securityUtils;
    }

    public List<TransferResponse> execute(Long accountId) {
        return execute(accountId, 0, 20);
    }

    public List<TransferResponse> execute(Long accountId, int page, int size) {
        Objects.requireNonNull(accountId, "Account ID null olamaz");

        // Load account metadata through the internal service (decoupled from domain Account entity)
        AccountInfo account = accountInternalService.getAccountInfo(accountId);

        // Authorization check
        securityUtils.checkUserAuthorization(account.userId(), "Bu hesabın işlem geçmişini görme yetkiniz yok.");

        List<Transfer> transfers = loadTransferPort.findHistory(accountId, page, size);

        // Batch load account IBANs to avoid N+1 query problem
        Set<Long> accountIds = transfers.stream()
                .flatMap(t -> Stream.of(t.getSenderAccountId(), t.getReceiverAccountId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> ibansMap = accountInternalService.getIbansForAccounts(accountIds);

        return transfers.stream()
                .map(transfer -> TransferResponse.from(
                        transfer,
                        ibansMap.get(transfer.getSenderAccountId()),
                        ibansMap.get(transfer.getReceiverAccountId())
                ))
                .collect(Collectors.toList());
    }
}
