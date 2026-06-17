package com.bank.app.transfer.infrastructure.web;

import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.usecase.CancelTransferUseCase;
import com.bank.app.transfer.application.usecase.PlaceTransferUseCase;
import com.bank.app.transfer.application.usecase.GetTransferDetailUseCase;
import com.bank.app.transfer.application.usecase.GetTransferHistoryUseCase;
import com.bank.app.transfer.application.usecase.GenerateTransferReportUseCase;
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
import com.bank.app.common.idempotency.Idempotent;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfer API", description = "Para transfer işlemlerini yöneten API")
public class TransferController {

    private final PlaceTransferUseCase placeTransferUseCase;
    private final CancelTransferUseCase cancelTransferUseCase;
    private final GetTransferDetailUseCase getTransferDetailUseCase;
    private final GetTransferHistoryUseCase getTransferHistoryUseCase;
    private final GenerateTransferReportUseCase generateTransferReportUseCase;

    public TransferController(PlaceTransferUseCase placeTransferUseCase, 
                              CancelTransferUseCase cancelTransferUseCase,
                              GetTransferDetailUseCase getTransferDetailUseCase,
                              GetTransferHistoryUseCase getTransferHistoryUseCase,
                              GenerateTransferReportUseCase generateTransferReportUseCase) {
        this.placeTransferUseCase = placeTransferUseCase;
        this.cancelTransferUseCase = cancelTransferUseCase;
        this.getTransferDetailUseCase = getTransferDetailUseCase;
        this.getTransferHistoryUseCase = getTransferHistoryUseCase;
        this.generateTransferReportUseCase = generateTransferReportUseCase;
    }

    @PostMapping
    @Idempotent
    @Operation(summary = "Para transferi gerçekleştirir", description = "Gönderici ve alıcı IBAN bilgileriyle para transfer işlemini başlatır ve kaydeder. Idempotency-Key başlığıyla tekrarlı istekler önlenebilir.")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse response = placeTransferUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Mevcut bir transferi iptal eder", description = "Gönderilen transfer ID'sine ait tamamlanmış işlemi 24 saat içinde iptal eder ve bakiyeleri iade eder.")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        cancelTransferUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Transfer detaylarını sorgular")
    public ResponseEntity<TransferDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(getTransferDetailUseCase.execute(id));
    }

    @GetMapping("/history/{accountId}")
    @Operation(summary = "Hesabın transfer geçmişini listeler")
    public ResponseEntity<List<TransferResponse>> getHistory(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(size, 100);
        int cappedPage = Math.max(page, 0);
        return ResponseEntity.ok(getTransferHistoryUseCase.execute(accountId, cappedPage, cappedSize));
    }

    @GetMapping("/report")
    @Operation(summary = "Tarih aralığına göre transfer raporu üretir")
    public ResponseEntity<TransferReportResponse> getReport(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(generateTransferReportUseCase.execute(new ReportCriteria(accountId, startDate, endDate)));
    }
}

