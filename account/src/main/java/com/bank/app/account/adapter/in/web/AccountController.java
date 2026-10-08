package com.bank.app.account.adapter.in.web;

import com.bank.app.account.adapter.in.web.dto.CreateAccountWebRequest;
import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.in.GetAccountByIdQuery;
import com.bank.app.account.application.port.in.GetAccountByIbanQuery;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.common.adapter.in.idempotency.Idempotent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import java.net.URI;

@RestController
@ApiVersion("v1")
@Validated
@RequestMapping("/accounts")
@Tag(name = "Account API", description = "API for managing bank accounts")
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;
    private final GetAccountByIdQuery getAccountByIdQuery;
    private final GetAccountByIbanQuery getAccountByIbanQuery;
    private final GetAccountsByUserQuery getAccountsByUserQuery;
    private final AccountAuthorizationService accountAuthorizationService;

    public AccountController(CreateAccountUseCase createAccountUseCase,
                             GetAccountByIdQuery getAccountByIdQuery,
                             GetAccountByIbanQuery getAccountByIbanQuery,
                             GetAccountsByUserQuery getAccountsByUserQuery,
                             AccountAuthorizationService accountAuthorizationService) {
        this.createAccountUseCase = createAccountUseCase;
        this.getAccountByIdQuery = getAccountByIdQuery;
        this.getAccountByIbanQuery = getAccountByIbanQuery;
        this.getAccountsByUserQuery = getAccountsByUserQuery;
        this.accountAuthorizationService = accountAuthorizationService;
    }

    @PostMapping
    @Idempotent
    @Operation(summary = "Creates a new account", description = "Opens a new account with a server-generated simulation IBAN. Duplicate requests can be prevented with the Idempotency-Key header.")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = false,
            description = "Optional de-duplication key; reuse returns the original response.")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountWebRequest webRequest) {
        CreateAccountRequest request = new CreateAccountRequest(
                accountAuthorizationService.getCurrentUserId(), webRequest.ownerName(),
                webRequest.initialBalance(), webRequest.currency());
        AccountResponse created = createAccountUseCase.execute(request);
        // Relative Location keeps this unit-testable without a request context
        // and resolves against /api/v1 on the wire.
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "Lists my accounts with pagination",
            description = "Returns only accounts owned by the authenticated user, newest first.")
    public ResponseEntity<PageResponse<AccountResponse>> listAccounts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(getAccountsByUserQuery.execute(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Queries account by ID", description = "Returns only an account owned by the authenticated user; unknown and other-owned IDs both return 404.")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(getAccountByIdQuery.execute(id));
    }

    @GetMapping("/iban/{iban}")
    @Operation(summary = "Queries account by IBAN", description = "Returns only an account owned by the authenticated user; unknown and other-owned IBANs both return 404.")
    public ResponseEntity<AccountResponse> getAccountByIban(@PathVariable String iban) {
        return ResponseEntity.ok(getAccountByIbanQuery.execute(iban));
    }
}
