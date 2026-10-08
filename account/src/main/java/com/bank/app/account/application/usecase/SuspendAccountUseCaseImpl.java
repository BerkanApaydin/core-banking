package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.SuspendAccountUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.common.domain.event.AuditEvent;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * First caller of {@link Account#suspend}: suspends an account as an admin
 * operation and evicts its snapshot in the same class, so the
 * cache-invalidation invariant holds structurally (see
 * CacheInvalidationArchitectureTest — a suspend path that forgets to evict
 * fails the build).
 *
 * <p>Idempotent: re-suspending an already suspended account succeeds without
 * side effects beyond re-persisting the unchanged aggregate.
 */
@TransactionalUseCase
public class SuspendAccountUseCaseImpl implements SuspendAccountUseCase {

    static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final UserContextService userContextService;
    private final ClockProviderPort clockProvider;
    private final DomainEventPublisherService domainEventPublisherService;
    private final AuditEventPort auditEventPort;
    private final AccountSnapshotCache snapshotCache;

    public SuspendAccountUseCaseImpl(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort,
            UserContextService userContextService,
            ClockProviderPort clockProvider,
            DomainEventPublisherService domainEventPublisherService,
            AuditEventPort auditEventPort,
            AccountSnapshotCache snapshotCache) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.userContextService = userContextService;
        this.clockProvider = clockProvider;
        this.domainEventPublisherService = domainEventPublisherService;
        this.auditEventPort = auditEventPort;
        this.snapshotCache = Objects.requireNonNull(snapshotCache, "AccountSnapshotCache must not be null");
    }

    @Override
    public AccountInfo suspend(Long accountId) {
        Objects.requireNonNull(accountId, "Account ID must not be null");
        Long adminId = requireAdmin();
        Account account = loadAccountPort.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        account.suspend(clockProvider.clock());
        Account saved = saveAccountPort.save(account);
        domainEventPublisherService.publishEvents(saved);
        auditEventPort.publish(new AuditEvent("ACCOUNT_SUSPENDED",
                "Suspended account ID " + saved.getId() + " (owner user ID "
                        + saved.getUserId().value() + ")",
                LocalDateTime.now(clockProvider.clock()),
                userContextService.getCurrentUsernameOrSystem(),
                adminId));
        // Same-class eviction (F-04): a stale ACTIVE snapshot must never
        // authorize this account again, not even within the cache TTL.
        snapshotCache.evictById(saved.getId());
        return new AccountInfo(saved.getId(), saved.getUserId().value(),
                saved.getBalance().currency().name(), saved.getStatus().name());
    }

    private Long requireAdmin() {
        Long adminId = userContextService.getCurrentUserId()
                .orElseThrow(() -> new AuthorizationException("error.login_required", null,
                        "You must be logged in to perform this action."));
        if (!userContextService.hasRole(ADMIN_ROLE)) {
            throw new AuthorizationException("error.admin_required", null, "Admin role required.");
        }
        return adminId;
    }
}
