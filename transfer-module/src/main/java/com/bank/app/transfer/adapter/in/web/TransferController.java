package com.bank.app.transfer.adapter.in.web;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.transfer.adapter.in.web.dto.TransferWebRequest;
import com.bank.app.transfer.application.dto.PagedResponse;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import com.bank.app.common.adapter.in.idempotency.Idempotent;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@ApiVersion("v1")
@RequestMapping("/transfers")
@Tag(name = "Transfer API", description = "Para transfer işlemlerini yöneten API")
public class TransferController {

    private final PlaceTransferUseCase placeTransferUseCase;
    private final CancelTransferUseCase cancelTransferUseCase;
    private final GetTransferDetailQuery getTransferDetailQuery;
    private final GetTransferHistoryQuery getTransferHistoryQuery;
    private final GenerateTransferReportQuery generateTransferReportQuery;

    public TransferController(PlaceTransferUseCase placeTransferUseCase,
            CancelTransferUseCase cancelTransferUseCase,
            GetTransferDetailQuery getTransferDetailQuery,
            GetTransferHistoryQuery getTransferHistoryQuery,
            GenerateTransferReportQuery generateTransferReportQuery) {
        this.placeTransferUseCase = placeTransferUseCase;
        this.cancelTransferUseCase = cancelTransferUseCase;
        this.getTransferDetailQuery = getTransferDetailQuery;
        this.getTransferHistoryQuery = getTransferHistoryQuery;
        this.generateTransferReportQuery = generateTransferReportQuery;
    }

    @PostMapping
    @Idempotent
    @Operation(summary = "Para transferi gerçekleştirir", description = "Gönderici ve alıcı IBAN bilgileriyle para transfer işlemini başlatır ve kaydeder. Idempotency-Key başlığıyla tekrarlı istekler önlenebilir.")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferWebRequest webRequest) {
        TransferRequest request = new TransferRequest(
                webRequest.senderIban(), webRequest.receiverIban(),
                webRequest.amount(), webRequest.currency());
        TransferResponse response = placeTransferUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/cancel")
    @Idempotent
    @Operation(summary = "Mevcut bir transferi iptal eder", description = "Gönderilen transfer ID'sine ait tamamlanmış işlemi 24 saat içinde iptal eder ve bakiyeleri iade eder.")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        cancelTransferUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Transfer detaylarını sorgular")
    public ResponseEntity<TransferDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(getTransferDetailQuery.execute(id));
    }

    @GetMapping("/history/{accountId}")
    @Operation(summary = "Hesabın transfer geçmişini listeler")
    public ResponseEntity<PagedResponse<TransferResponse>> getHistory(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(getTransferHistoryQuery.execute(accountId, page, size));
    }

    @GetMapping("/report")
    @Operation(summary = "Tarih aralığına göre transfer raporu üretir")
    public ResponseEntity<TransferReportResponse> getReport(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity
                .ok(generateTransferReportQuery.execute(new ReportCriteria(accountId, startDate, endDate, page, size)));
    }
}
