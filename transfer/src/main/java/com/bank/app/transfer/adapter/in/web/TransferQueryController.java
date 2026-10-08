package com.bank.app.transfer.adapter.in.web;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.dto.TransferReportTotalsResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.in.GenerateTransferReportTotalsQuery;
import com.bank.app.transfer.application.port.in.GenerateTransferReportWithTotalsQuery;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.service.TransferReportEtagService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * Query side of the transfer web adapter (split from the former fat
 * {@code TransferController}: reads live here, commands in
 * {@code TransferCommandController}).
 */
@RestController
@ApiVersion("v1")
@Validated
@RequestMapping("/transfers")
@Tag(name = "Transfer API", description = "API for managing money transfers")
public class TransferQueryController {

    private final GetTransferDetailQuery getTransferDetailQuery;
    private final GetTransferHistoryQuery getTransferHistoryQuery;
    private final GenerateTransferReportQuery generateTransferReportQuery;
    private final GenerateTransferReportTotalsQuery generateTransferReportTotalsQuery;
    private final GenerateTransferReportWithTotalsQuery generateTransferReportWithTotalsQuery;

    public TransferQueryController(GetTransferDetailQuery getTransferDetailQuery,
            GetTransferHistoryQuery getTransferHistoryQuery,
            GenerateTransferReportQuery generateTransferReportQuery,
            GenerateTransferReportTotalsQuery generateTransferReportTotalsQuery,
            GenerateTransferReportWithTotalsQuery generateTransferReportWithTotalsQuery) {
        this.getTransferDetailQuery = getTransferDetailQuery;
        this.getTransferHistoryQuery = getTransferHistoryQuery;
        this.generateTransferReportQuery = generateTransferReportQuery;
        this.generateTransferReportTotalsQuery = generateTransferReportTotalsQuery;
        this.generateTransferReportWithTotalsQuery = generateTransferReportWithTotalsQuery;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Queries transfer details")
    public ResponseEntity<TransferDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(getTransferDetailQuery.execute(id));
    }

    @GetMapping("/history/{accountId}")
    @Operation(summary = "Lists the transfer history of an account",
            description = "Offset pagination for shallow windows. Deep windows (page*size > 10000) are rejected with 400; "
                    + "use GET /transfers/report with cursorCreatedAt+cursorId (keyset) for unbounded scrolling.")
    public ResponseEntity<PageResponse<TransferResponse>> getHistory(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(getTransferHistoryQuery.execute(accountId, page, size));
    }

    @GetMapping("/report")
    @Operation(summary = "Generates a paginated transfer report by date range",
            description = "Page totals cover only returned transfers; hasNext indicates another page at query time. Separate pages are not a snapshot. Supports keyset cursor (cursorCreatedAt+cursorId) to avoid OFFSET sort cost. "
                    + "Pass includeTotals=true to compute whole-range totalCount/totalVolume in the same call (same as /report/combined, single round trip).")
    public ResponseEntity<TransferReportResponse> getReport(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "false") boolean includeTotals,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        ReportCriteria criteria = ReportCriteria.criteriaOf(
                accountId, startDate, endDate, page, size, cursorCreatedAt, cursorId);
        TransferReportResponse body = includeTotals
                ? generateTransferReportWithTotalsQuery.execute(criteria)
                : generateTransferReportQuery.execute(criteria);
        String etag = TransferReportEtagService.etagFor(body);
        if (ifNoneMatch != null && ("*".equals(ifNoneMatch) || ifNoneMatch.equals(etag))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(body);
    }

    @GetMapping("/report/combined")
    @Operation(summary = "Items + whole-range totals in one call",
            description = "API-2: single authorization and read transaction; avoids the two-request skew between /report and /report/totals. "
                    + "New clients should prefer GET /report?includeTotals=true, which serves the same payload from the primary endpoint.")
    public ResponseEntity<TransferReportResponse> getReportCombined(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(required = false) Long cursorId) {
        ReportCriteria criteria = ReportCriteria.criteriaOf(
                accountId, startDate, endDate, page, size, cursorCreatedAt, cursorId);
        TransferReportResponse body = generateTransferReportWithTotalsQuery.execute(criteria);
        return ResponseEntity.ok().eTag(TransferReportEtagService.etagFor(body)).body(body);
    }

    @GetMapping("/report/totals")
    @Operation(summary = "Aggregates whole-range transfer totals by date range",
            description = "Single indexed COUNT+SUM scan over the same window the paginated report uses. Currency comes from the account; cross-currency legs are rejected by the domain, so one total is exact.")
    public ResponseEntity<TransferReportTotalsResponse> getReportTotals(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(generateTransferReportTotalsQuery
                .execute(new ReportCriteria(accountId, startDate, endDate)));
    }
}
