package com.bank.app.account.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.account.domain.exception.AccountClosedException;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.common.domain.exception.AuthorizationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuspendAccountUseCase")
class SuspendAccountUseCaseImplTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));

    @Mock private LoadAccountPort loadAccountPort;
    @Mock private SaveAccountPort saveAccountPort;
    @Mock private UserContextService userContextService;
    @Mock private ClockProviderPort clockProvider;
    @Mock private DomainEventPublisherService domainEventPublisherService;
    @Mock private AuditEventPort auditEventPort;
    @Mock private AccountSnapshotCache snapshotCache;

    @Captor private ArgumentCaptor<Account> accountCaptor;
    @Captor private ArgumentCaptor<AuditEvent> auditEventCaptor;

    private SuspendAccountUseCaseImpl useCase;
    private Account activeAccount;

    @BeforeEach
    void setUp() {
        lenient().when(clockProvider.clock()).thenReturn(FIXED_CLOCK);
        lenient().when(saveAccountPort.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        useCase = new SuspendAccountUseCaseImpl(loadAccountPort, saveAccountPort, userContextService,
                clockProvider, domainEventPublisherService, auditEventPort, snapshotCache);
        activeAccount = new Account(1L, new UserId(10L), new Iban("TR450006100519786456841234"),
                "Owner", Money.of("100.00", Currency.TRY), AccountStatus.ACTIVE);
    }

    private void asAdmin() {
        lenient().when(userContextService.getCurrentUserId()).thenReturn(Optional.of(99L));
        lenient().when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(true);
        lenient().when(userContextService.getCurrentUsernameOrSystem()).thenReturn("admin");
    }

    @Nested
    @DisplayName("suspend")
    class Suspend {

        @Test
        void shouldSuspendActiveAccountAndEvictSnapshot() {
            asAdmin();
            when(loadAccountPort.findByIdForUpdate(1L)).thenReturn(Optional.of(activeAccount));

            AccountInfo result = useCase.suspend(1L);

            assertEquals(1L, result.id());
            assertEquals("SUSPENDED", result.status());
            verify(saveAccountPort).save(accountCaptor.capture());
            assertEquals(AccountStatus.SUSPENDED, accountCaptor.getValue().getStatus());
            verify(domainEventPublisherService).publishEvents(activeAccount);
            verify(auditEventPort).publish(auditEventCaptor.capture());
            assertEquals("ACCOUNT_SUSPENDED", auditEventCaptor.getValue().action());
            assertTrue(auditEventCaptor.getValue().details().contains("1"));
            // Kills the PrimitiveReturns mutant (0L vs actual admin id).
            assertEquals(99L, auditEventCaptor.getValue().actorUserId());
            // F-04: the same class that mutates the status evicts the snapshot.
            verify(snapshotCache).evictById(1L);
            verify(snapshotCache, never()).evictAll();
        }

        @Test
        void shouldSucceedWhenAlreadySuspended() {
            asAdmin();
            activeAccount.suspend(FIXED_CLOCK);
            when(loadAccountPort.findByIdForUpdate(1L)).thenReturn(Optional.of(activeAccount));

            AccountInfo result = useCase.suspend(1L);

            assertEquals("SUSPENDED", result.status());
            verify(saveAccountPort).save(any(Account.class));
            verify(snapshotCache).evictById(1L);
        }

        @Test
        void shouldRejectClosedAccountWithoutSideEffects() {
            asAdmin();
            Account closed = new Account(2L, new UserId(10L), new Iban("TR180006100519786456841235"),
                    "Owner", Money.of("0.00", Currency.TRY), AccountStatus.CLOSED);
            when(loadAccountPort.findByIdForUpdate(2L)).thenReturn(Optional.of(closed));

            assertThrows(AccountClosedException.class, () -> useCase.suspend(2L));
            verifyNoInteractions(saveAccountPort, snapshotCache, auditEventPort, domainEventPublisherService);
        }

        @Test
        void shouldThrowWhenAccountMissing() {
            asAdmin();
            when(loadAccountPort.findByIdForUpdate(42L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class, () -> useCase.suspend(42L));
            verifyNoInteractions(saveAccountPort, snapshotCache, auditEventPort, domainEventPublisherService);
        }

        @Test
        void shouldRejectNonAdmin() {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.of(10L));
            when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(AuthorizationException.class, () -> useCase.suspend(1L));
            verifyNoInteractions(loadAccountPort, saveAccountPort, snapshotCache,
                    auditEventPort, domainEventPublisherService);
        }

        @Test
        void shouldRejectAnonymous() {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

            assertThrows(AuthorizationException.class, () -> useCase.suspend(1L));
            verifyNoInteractions(loadAccountPort, saveAccountPort, snapshotCache,
                    auditEventPort, domainEventPublisherService);
        }

        @Test
        void shouldRejectNullId() {
            assertThrows(NullPointerException.class, () -> useCase.suspend(null));
            verifyNoInteractions(loadAccountPort, saveAccountPort, snapshotCache,
                    auditEventPort, domainEventPublisherService);
        }
    }
}
