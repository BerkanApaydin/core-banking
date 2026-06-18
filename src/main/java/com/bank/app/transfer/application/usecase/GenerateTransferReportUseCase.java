package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.AccountOperationsPort;
import com.bank.app.transfer.application.port.AccountOperationsPort.AccountInfo;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.common.security.port.SecurityContextPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class GenerateTransferReportUseCase {

    private final LoadTransferPort loadTransferPort;
    private final AccountOperationsPort accountOperationsPort;
    private final SecurityContextPort securityContextPort;

    public GenerateTransferReportUseCase(LoadTransferPort loadTransferPort,
                                         AccountOperationsPort accountOperationsPort,
                                         SecurityContextPort securityContextPort) {
        this.loadTransferPort = loadTransferPort;
        this.accountOperationsPort = accountOperationsPort;
        this.securityContextPort = securityContextPort;
    }

    public TransferReportResponse execute(ReportCriteria criteria) {
        Objects.requireNonNull(criteria, "Criteria null olamaz");
        Long accountId = Objects.requireNonNull(criteria.accountId(), "Account ID null olamaz");
        LocalDateTime startDate = Objects.requireNonNull(criteria.startDate(), "Start date null olamaz");
        LocalDateTime endDate = Objects.requireNonNull(criteria.endDate(), "End date null olamaz");

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Başlangıç tarihi bitiş tarihinden sonra olamaz.");
        }
        if (startDate.plusMonths(12).isBefore(endDate)) {
            throw new IllegalArgumentException("Rapor aralığı en fazla 12 ay olabilir.");
        }

        int page = Math.max(criteria.page(), 0);
        int size = Math.min(criteria.size(), 100);

        // Load account metadata through the internal service (decoupled from domain Account entity)
        AccountInfo account = accountOperationsPort.getAccountInfo(accountId);

        securityContextPort.checkUserAuthorization(account.userId(), "Bu hesabın raporunu oluşturma yetkiniz yok.");

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
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> ibansMap = accountOperationsPort.getIbansForAccounts(accountIds);

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
