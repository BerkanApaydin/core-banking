package com.bank.app.transfer.adapter.in.web;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.transfer.adapter.in.web.dto.TransferWebRequest;
import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.dto.TransferReportTotalsResponse;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.in.GenerateTransferReportTotalsQuery;
import com.bank.app.transfer.application.port.in.GenerateTransferReportWithTotalsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import com.bank.app.common.adapter.in.idempotency.Idempotent;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Objects;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

@RestController
@ApiVersion("v1")
@Validated
@RequestMapping("/transfers")
@Tag(name = "Transfer API", description = "API for managing money transfers")
public class TransferController {

    private final PlaceTransferUseCase placeTransferUseCase;
    private final CancelTransferUseCase cancelTransferUseCase;
    private final GetTransferDetailQuery getTransferDetailQuery;
    private final GetTransferHistoryQuery getTransferHistoryQuery;
    private final GenerateTransferReportQuery generateTransferReportQuery;
    private final GenerateTransferReportTotalsQuery generateTransferReportTotalsQuery;
    private final GenerateTransferReportWithTotalsQuery generateTransferReportWithTotalsQuery;

    public TransferController(PlaceTransferUseCase placeTransferUseCase,
            CancelTransferUseCase cancelTransferUseCase,
            GetTransferDetailQuery getTransferDetailQuery,
            GetTransferHistoryQuery getTransferHistoryQuery,
            GenerateTransferReportQuery generateTransferReportQuery,
            GenerateTransferReportTotalsQuery generateTransferReportTotalsQuery,
            GenerateTransferReportWithTotalsQuery generateTransferReportWithTotalsQuery) {
        this.placeTransferUseCase = placeTransferUseCase;
        this.cancelTransferUseCase = cancelTransferUseCase;
        this.getTransferDetailQuery = getTransferDetailQuery;
        this.getTransferHistoryQuery = getTransferHistoryQuery;
        this.generateTransferReportQuery = generateTransferReportQuery;
        this.generateTransferReportTotalsQuery = generateTransferReportTotalsQuery;
        this.generateTransferReportWithTotalsQuery = generateTransferReportWithTotalsQuery;
    }

    @PostMapping
    @Idempotent(required = true)
    @Operation(summary = "Executes a money transfer", description = "Initiates and records a money transfer with sender and receiver IBAN information. Idempotency-Key header is required.")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
            description = "Required de-duplication key; missing header is rejected with 409.")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferWebRequest webRequest) {
        TransferRequest request = new TransferRequest(
                webRequest.senderIban(), webRequest.receiverIban(),
                webRequest.amount(), webRequest.currency());
        TransferResponse response = placeTransferUseCase.execute(request);
        return ResponseEntity.created(URI.create("/api/v1/transfers/" + response.id())).body(response);
    }

    @PostMapping("/{id}/cancel")
    @Idempotent(required = true)
    @Operation(summary = "Cancels an existing transfer", description = "Cancels a completed transfer within the configured cancellation window by transfer ID and refunds balances. Idempotency-Key header is required.")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
            description = "Required de-duplication key; missing header is rejected with 409.")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        cancelTransferUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Queries transfer details")
    public ResponseEntity<TransferDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(getTransferDetailQuery.execute(id));
    }

    @GetMapping("/history/{accountId}")
    @Operation(summary = "Lists the transfer history of an account")
    public ResponseEntity<PageResponse<TransferResponse>> getHistory(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(getTransferHistoryQuery.execute(accountId, page, size));
    }

    @GetMapping("/report")
    @Operation(summary = "Generates a paginated transfer report by date range",
            description = "Page totals cover only returned transfers; hasNext indicates another page at query time. Separate pages are not a snapshot. Supports keyset cursor (cursorCreatedAt+cursorId) to avoid OFFSET sort cost.")
    public ResponseEntity<TransferReportResponse> getReport(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(required = false) Long cursorId,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        ReportCriteria criteria = (cursorCreatedAt != null || cursorId != null)
                ? new ReportCriteria(accountId, startDate, endDate, 0, size, cursorCreatedAt, cursorId)
                : new ReportCriteria(accountId, startDate, endDate, page, size);
        TransferReportResponse body = generateTransferReportQuery.execute(criteria);
        String etag = etagFor(body);
        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(body);
    }

    @GetMapping("/report/combined")
    @Operation(summary = "Items + whole-range totals in one call",
            description = "API-2: single authorization and read transaction; avoids the two-request skew between /report and /report/totals.")
    public ResponseEntity<TransferReportResponse> getReportCombined(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(required = false) Long cursorId) {
        ReportCriteria criteria = (cursorCreatedAt != null || cursorId != null)
                ? new ReportCriteria(accountId, startDate, endDate, 0, size, cursorCreatedAt, cursorId)
                : new ReportCriteria(accountId, startDate, endDate, page, size);
        TransferReportResponse body = generateTransferReportWithTotalsQuery.execute(criteria);
        return ResponseEntity.ok().eTag(etagFor(body)).body(body);
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

    // API-1/ETag: weak validator over stable report content (account + page +
    // volume + cursor). Clients send If-None-Match to get 304 without re-render.
    private static String etagFor(TransferReportResponse body) {
        int h = Objects.hash(body.accountId(), body.pageTransferCount(),
                body.pageVolume(), body.currency(), body.hasNext(),
                body.nextCursorCreatedAt(), body.nextCursorId(),
                body.transfers().size());
        return "W/\"report-" + Integer.toHexString(h) + "\"";
    }
}
