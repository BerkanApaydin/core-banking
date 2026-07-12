package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.domain.Transfer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ReadOnlyUseCase
public class GenerateTransferReportUseCaseImpl implements GenerateTransferReportQuery {

    private final LoadTransferPort loadTransferPort;
    private final AccountAclPort accountAclPort;
    private final TransferAuthorizationService transferAuthorizationService;

    public GenerateTransferReportUseCaseImpl(LoadTransferPort loadTransferPort,
                                         AccountAclPort accountAclPort,
                                         TransferAuthorizationService transferAuthorizationService) {
        this.loadTransferPort = loadTransferPort;
        this.accountAclPort = accountAclPort;
        this.transferAuthorizationService = transferAuthorizationService;
    }

    @Override
    public TransferReportResponse execute(ReportCriteria criteria) {
        Objects.requireNonNull(criteria, "Criteria must not be null");
        Long accountId = Objects.requireNonNull(criteria.accountId(), "Account ID must not be null");
        LocalDateTime startDate = Objects.requireNonNull(criteria.startDate(), "Start date must not be null");
        LocalDateTime endDate = Objects.requireNonNull(criteria.endDate(), "End date must not be null");

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must not be after end date.");
        }
        if (startDate.plusMonths(12).isBefore(endDate)) {
            throw new IllegalArgumentException("Report range must be at most 12 months.");
        }

        int page = Math.max(criteria.page(), 0);
        int size = Math.min(criteria.size(), 100);

        AccountInfo account = transferAuthorizationService.authorizeAccountAccess(accountId, "You are not authorized to generate a report for this account.");

        List<Transfer> transfers = loadTransferPort.findHistoryBetween(
            accountId,
            startDate,
            endDate,
            page,
            size
        );

        // Batch load account IBANs to avoid N+1 query problem
        Set<Long> accountIds = transfers.stream()
                .flatMap(t -> Stream.of(t.getSenderAccountId(), t.getReceiverAccountId()))
                .collect(Collectors.toSet());

        Map<Long, String> ibansMap =         accountAclPort.getIbansForAccounts(accountIds);

        List<TransferResponse> responseList = transfers.stream()
            .map(transfer -> TransferResponse.from(
                    transfer,
                    ibansMap.get(transfer.getSenderAccountId()),
                    ibansMap.get(transfer.getReceiverAccountId())
            ))
            .collect(Collectors.toList());

        BigDecimal totalVolume = transfers.stream()
            .map(t -> t.getAmount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TransferReportResponse(
            criteria.accountId(),
            transfers.size(),
            totalVolume,
            account.currency(),
            responseList
        );
    }
}
