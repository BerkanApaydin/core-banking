package com.bank.app.transfer.adapter.in.web;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.transfer.adapter.in.web.dto.TransferWebRequest;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import com.bank.app.common.adapter.in.idempotency.Idempotent;

import java.net.URI;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

/**
 * Command side of the transfer web adapter (split from the former fat
 * {@code TransferController}: state-changing endpoints live here, reads in
 * {@code TransferQueryController}, so a report change can never affect the
 * money-movement deploy surface).
 */
@RestController
@ApiVersion("v1")
@Validated
@RequestMapping("/transfers")
@Tag(name = "Transfer API", description = "API for managing money transfers")
public class TransferCommandController {

    private final PlaceTransferUseCase placeTransferUseCase;
    private final CancelTransferUseCase cancelTransferUseCase;

    public TransferCommandController(PlaceTransferUseCase placeTransferUseCase,
            CancelTransferUseCase cancelTransferUseCase) {
        this.placeTransferUseCase = placeTransferUseCase;
        this.cancelTransferUseCase = cancelTransferUseCase;
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
}
