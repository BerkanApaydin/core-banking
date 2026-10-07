package com.bank.app.account.adapter.in.web;

import com.bank.app.account.adapter.in.web.dto.AccountStatusResponse;
import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.SuspendAccountUseCase;
import com.bank.app.common.adapter.in.api.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only account lifecycle operations (ROLE_ADMIN, enforced in the use
 * case — see {@code SuspendAccountUseCaseImpl}, same pattern as the audit
 * module). Suspended accounts can neither send nor receive money; their
 * snapshots are evicted before this call returns, so authorization decisions
 * never observe the stale ACTIVE status.
 */
@RestController
@ApiVersion("v1")
@Validated
@RequestMapping("/admin/accounts")
@Tag(name = "Account Admin API", description = "Admin operations on bank accounts")
public class AccountAdminController {

    private final SuspendAccountUseCase suspendAccountUseCase;

    public AccountAdminController(SuspendAccountUseCase suspendAccountUseCase) {
        this.suspendAccountUseCase = suspendAccountUseCase;
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspends an account",
            description = "Admin-only. Idempotent: re-suspending an already suspended account succeeds. Suspended accounts are evicted from the snapshot cache immediately.")
    public ResponseEntity<AccountStatusResponse> suspendAccount(@PathVariable Long id) {
        AccountInfo suspended = suspendAccountUseCase.suspend(id);
        return ResponseEntity.ok(new AccountStatusResponse(suspended.id(), suspended.status()));
    }
}
