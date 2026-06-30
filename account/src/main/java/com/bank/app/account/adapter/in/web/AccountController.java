package com.bank.app.account.adapter.in.web;

import com.bank.app.account.adapter.in.web.dto.CreateAccountWebRequest;
import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.in.GetAccountByIdQuery;
import com.bank.app.account.application.port.in.GetAccountByIbanQuery;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.common.adapter.in.api.ApiVersion;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@ApiVersion("v1")
@RequestMapping("/accounts")
@Tag(name = "Account API", description = "API for managing bank accounts")
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;
    private final GetAccountByIdQuery getAccountByIdQuery;
    private final GetAccountByIbanQuery getAccountByIbanQuery;
    private final GetAccountsByUserQuery getAccountsByUserQuery;

    public AccountController(CreateAccountUseCase createAccountUseCase,
                             GetAccountByIdQuery getAccountByIdQuery,
                             GetAccountByIbanQuery getAccountByIbanQuery,
                             GetAccountsByUserQuery getAccountsByUserQuery) {
        this.createAccountUseCase = createAccountUseCase;
        this.getAccountByIdQuery = getAccountByIdQuery;
        this.getAccountByIbanQuery = getAccountByIbanQuery;
        this.getAccountsByUserQuery = getAccountsByUserQuery;
    }

    @PostMapping
    @Operation(summary = "Creates a new account", description = "Opens a new account with the given information and verified IBAN.")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountWebRequest webRequest) {
        CreateAccountRequest request = new CreateAccountRequest(
                webRequest.userId(), webRequest.iban(), webRequest.ownerName(),
                webRequest.initialBalance(), webRequest.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(createAccountUseCase.execute(request));
    }

    @GetMapping
    @Operation(summary = "Lists all accounts with pagination")
    public ResponseEntity<PageResponse<AccountResponse>> listAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(getAccountsByUserQuery.execute(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Queries account by ID")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(getAccountByIdQuery.execute(id));
    }

    @GetMapping("/iban/{iban}")
    @Operation(summary = "Queries account by IBAN")
    public ResponseEntity<AccountResponse> getAccountByIban(@PathVariable String iban) {
        return ResponseEntity.ok(getAccountByIbanQuery.execute(iban));
    }
}
