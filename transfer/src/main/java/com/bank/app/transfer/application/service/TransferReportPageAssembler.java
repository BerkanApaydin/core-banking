package com.bank.app.transfer.application.service;

import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.domain.Transfer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Shared page assembly for the transfer report queries (R-1).
 *
 * <p>{@code GenerateTransferReportQueryImpl} and
 * {@code GenerateTransferReportWithTotalsQueryImpl} previously duplicated the
 * fetch / {@code hasNext} / enrich / page-volume / cursor logic line-for-line;
 * only the whole-range totals differed. All page-scoped steps now live here so
 * a new filter or cursor rule changes in exactly one place.
 */
public final class TransferReportPageAssembler {

    private TransferReportPageAssembler() {
    }

    public record ReportPage(
            List<Transfer> transfers,
            List<TransferResponse> responses,
            BigDecimal pageVolume,
            boolean hasNext,
            String nextCursorCreatedAt,
            Long nextCursorId) {
    }

    public static int clampSize(int requested, int maxPageSize) {
        int cap = maxPageSize > 0 ? maxPageSize : 100;
        return Math.max(Math.min(requested, cap), 1);
    }

    public static ReportPage assemble(LoadTransferPort loadTransferPort,
            TransferViewEnricher viewEnricher,
            ReportCriteria criteria,
            int size) {
        Objects.requireNonNull(loadTransferPort, "LoadTransferPort must not be null");
        Objects.requireNonNull(viewEnricher, "TransferViewEnricher must not be null");
        Objects.requireNonNull(criteria, "Criteria must not be null");

        List<Transfer> fetched = criteria.isKeyset()
                ? loadTransferPort.findHistoryBetweenKeyset(
                        criteria.accountId(), criteria.startDate(), criteria.endDate(),
                        criteria.cursorCreatedAt(), criteria.cursorId(), size)
                : loadTransferPort.findHistoryBetween(
                        criteria.accountId(), criteria.startDate(), criteria.endDate(),
                        Math.max(criteria.page(), 0), size);

        boolean hasNext = fetched.size() > size;
        List<Transfer> transfers = hasNext ? fetched.subList(0, size) : fetched;

        List<TransferResponse> responseList = viewEnricher.enrich(transfers);

        BigDecimal pageVolume = transfers.stream()
                .map(t -> t.getAmount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String nextCursorCreatedAt = null;
        Long nextCursorId = null;
        // hasNext implies a non-empty page (size >= 1, so the trimmed subList
        // cannot be empty): no defensive isEmpty check — dead conditions hide
        // real branches from mutation testing.
        if (hasNext) {
            Transfer last = transfers.get(transfers.size() - 1);
            nextCursorCreatedAt = last.getCreatedAt().toString();
            nextCursorId = last.getId();
        }

        return new ReportPage(transfers, responseList, pageVolume, hasNext,
                nextCursorCreatedAt, nextCursorId);
    }
}
