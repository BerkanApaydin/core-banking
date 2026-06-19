package com.bank.app.account.infrastructure.web;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.CreateAccountPort;
import com.bank.app.account.application.port.in.GetAccountPort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Account API", description = "Hesap yönetimi işlemlerini yöneten API")
public class AccountController {

    private final CreateAccountPort createAccountPort;
    private final GetAccountPort getAccountPort;

    public AccountController(CreateAccountPort createAccountPort, GetAccountPort getAccountPort) {
        this.createAccountPort = createAccountPort;
        this.getAccountPort = getAccountPort;
    }

    @PostMapping
    @Operation(summary = "Yeni hesap oluşturur", description = "Verilen bilgiler ve doğrulanmış IBAN ile yeni bir hesap açılmasını sağlar.")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createAccountPort.execute(request));
    }

    @GetMapping
    @Operation(summary = "Tüm hesapları listeler")
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        return ResponseEntity.ok(getAccountPort.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Hesap ID'sine göre hesabı sorgular")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(getAccountPort.getById(id));
    }

    @GetMapping("/iban/{iban}")
    @Operation(summary = "IBAN numarasına göre hesabı sorgular")
    public ResponseEntity<AccountResponse> getAccountByIban(@PathVariable String iban) {
        return ResponseEntity.ok(getAccountPort.getByIban(iban));
    }
}
