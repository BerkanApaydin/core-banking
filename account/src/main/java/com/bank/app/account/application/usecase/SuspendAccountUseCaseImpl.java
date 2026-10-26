package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountView;
import com.bank.app.account.application.port.in.SuspendAccountUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.api.AccountNotFoundException;
import com.bank.app.account.api.AccountSnapshotCache;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.TransactionBoundaryPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.common.messages.MessageKeys;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * First caller of {@link Account#suspend}: suspends an account as an admin
 * operation and evicts its snapshot after commit in the same class, so the
 * cache-invalidation invariant holds structurally (see
 * CacheInvalidationArchitectureTest — a suspend path that forgets to evict,
 * or evicts before commit, fails the build).
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
    private final TransactionBoundaryPort transactionBoundary;

    public SuspendAccountUseCaseImpl(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort,
            UserContextService userContextService,
            ClockProviderPort clockProvider,
            DomainEventPublisherService domainEventPublisherService,
            AuditEventPort auditEventPort,
            AccountSnapshotCache snapshotCache,
            TransactionBoundaryPort transactionBoundary) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.userContextService = userContextService;
        this.clockProvider = clockProvider;
        this.domainEventPublisherService = domainEventPublisherService;
        this.auditEventPort = auditEventPort;
        this.snapshotCache = Objects.requireNonNull(snapshotCache, "AccountSnapshotCache must not be null");
        this.transactionBoundary = Objects.requireNonNull(transactionBoundary,
                "TransactionBoundaryPort must not be null");
    }

    @Override
    public AccountView suspend(Long accountId) {
        Objects.requireNonNull(accountId, "Account ID must not be null");
        Long adminId = requireAdmin();
        Account account = loadAccountPort.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        account.suspend(clockProvider.clock());
        Account saved = saveAccountPort.save(account);
        // Publish from the aggregate that recorded the event: the adapter
        // returns a rehydrated copy (no domain events) after the versioned
        // UPDATE, so publishing from `saved` silently dropped
        // AccountSuspendedEvent (R-A01).
        domainEventPublisherService.publishEvents(account);
        auditEventPort.publish(new AuditEvent(AuditEvent.ACCOUNT_SUSPENDED,
                "Suspended account ID " + saved.getId() + " (owner user ID "
                        + saved.getUserId().value() + ")",
                LocalDateTime.now(clockProvider.clock()),
                userContextService.getCurrentUsernameOrSystem(),
                adminId));
        // Same-class eviction (F-04): a stale ACTIVE snapshot must never
        // authorize this account again, not even within the cache TTL.
        evictSnapshotAfterCommit(saved.getId());
        return new AccountView(saved.getId(), saved.getUserId().value(),
                saved.getBalance().currency().name(), saved.getStatus().name());
    }

    /**
     * Deferred invalidation (F-04/F-11): evicting before commit lets a
     * concurrent read-through repopulate the cache from pre-commit (still
     * ACTIVE) state, and that stale snapshot then survives the eviction for
     * the full TTL. Mirrors {@code AccountApiAdapter.evictSnapshotsAfterCommit}:
     * eviction runs after commit when a transaction is active, directly
     * otherwise (tests, schedulers). The direct {@code evictById} call stays
     * in this class so {@code CacheInvalidationArchitectureTest} remains
     * structural instead of incidental.
     */
    private void evictSnapshotAfterCommit(Long accountId) {
        if (transactionBoundary.isTransactionActive()) {
            transactionBoundary.runAfterCommit(() -> snapshotCache.evictById(accountId));
        } else {
            snapshotCache.evictById(accountId);
        }
    }

    private Long requireAdmin() {
        Long adminId = userContextService.getCurrentUserId()
                .orElseThrow(() -> new AuthorizationException(MessageKeys.LOGIN_REQUIRED, null,
                        "You must be logged in to perform this action."));
        if (!userContextService.hasRole(ADMIN_ROLE)) {
            throw new AuthorizationException(MessageKeys.ADMIN_REQUIRED, null, "Admin role required.");
        }
        return adminId;
    }
}
