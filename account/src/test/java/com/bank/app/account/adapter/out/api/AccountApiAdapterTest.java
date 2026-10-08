package com.bank.app.account.adapter.out.api;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.AccountQueryUseCase;
import com.bank.app.account.application.port.in.AdjustAccountBalancesUseCase;
import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.accountapi.AccountNotFoundException;
import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.accountapi.AccountSnapshotCache;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountApiAdapter (published language bridge)")
class AccountApiAdapterTest {

    @Mock
    private AccountQueryUseCase accountQueryUseCase;

    @Mock
    private AdjustAccountBalancesUseCase adjustAccountBalancesUseCase;

    @Mock
    private AccountSnapshotCache snapshotCache;

    private AccountApiAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountApiAdapter(accountQueryUseCase, adjustAccountBalancesUseCase, snapshotCache);
    }

    @Test
    void shouldTranslateAccountInfoToSnapshot() {
        when(accountQueryUseCase.getAccountInfo(1L))
                .thenReturn(new AccountInfo(1L, 10L, "TRY", "ACTIVE"));

        AccountSnapshot snapshot = adapter.getSnapshotById(1L);

        assertEquals(new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"), snapshot);
    }

    @Test
    void shouldTranslateIbanLookupToSnapshot() {
        when(accountQueryUseCase.getAccountInfoForTransfer("TR450006100519786456841234"))
                .thenReturn(new AccountInfo(1L, 10L, "TRY", "ACTIVE"));

        AccountSnapshot snapshot = adapter.getSnapshotByIban("TR450006100519786456841234");

        assertEquals(1L, snapshot.id());
        assertEquals(10L, snapshot.userId());
    }

    @Test
    void shouldTranslateDomainNotFoundToPublishedLanguageById() {
        when(accountQueryUseCase.getAccountInfo(999L))
                .thenThrow(new com.bank.app.account.domain.exception.AccountNotFoundException(999L));

        AccountNotFoundException ex = assertThrows(
                AccountNotFoundException.class,
                () -> adapter.getSnapshotById(999L));

        assertEquals("Account not found. ID: 999", ex.getMessage());
        assertEquals(BusinessFailureKind.NOT_FOUND, ex.getFailureKind());
        assertEquals("ACCOUNT_NOT_FOUND_ID", ex.getErrorCode());
    }

    @Test
    void shouldTranslateDomainNotFoundToPublishedLanguageByIban() {
        when(accountQueryUseCase.getAccountInfoForTransfer("TR000"))
                .thenThrow(new com.bank.app.account.domain.exception.AccountNotFoundException("TR000"));

        AccountNotFoundException ex = assertThrows(
                AccountNotFoundException.class,
                () -> adapter.getSnapshotByIban("TR000"));

        assertEquals("Account not found. IBAN: TR000", ex.getMessage());
        assertEquals(BusinessFailureKind.NOT_FOUND, ex.getFailureKind());
        assertEquals("ACCOUNT_NOT_FOUND_IBAN", ex.getErrorCode());
    }

    @Test
    void shouldDelegateIbanBatchLookup() {
        when(accountQueryUseCase.getIbansForAccounts(Set.of(1L)))
                .thenReturn(Map.of(1L, "TR450006100519786456841234"));

        assertEquals("TR450006100519786456841234",
                adapter.getIbansForAccounts(Set.of(1L)).get(1L));
    }

    @Test
    void shouldDelegateBalanceAdjustment() {
        Money amount = Money.of("50.00", Currency.TRY);
        AccountAdjustmentResult expected = new AccountAdjustmentResult(1L, 2L,
                Money.of("950.00", Currency.TRY), Money.of("550.00", Currency.TRY));
        when(adjustAccountBalancesUseCase.debitAndCredit(1L, 2L, amount)).thenReturn(expected);

        assertSame(expected, adapter.adjustBalances(1L, 2L, amount));
    }

    @Test
    void shouldDelegateCancellationReversal() {
        Money amount = Money.of("50.00", Currency.TRY);
        AccountAdjustmentResult expected = new AccountAdjustmentResult(1L, 2L,
                Money.of("1000.00", Currency.TRY), Money.of("500.00", Currency.TRY));
        when(adjustAccountBalancesUseCase.reverseForCancellation(1L, 2L, amount)).thenReturn(expected);

        assertSame(expected, adapter.reverseForCancellation(1L, 2L, amount));
    }

    @Test
    void shouldEvictMutatedSnapshotsOnBalanceAdjustment() {
        Money amount = Money.of("50.00", Currency.TRY);
        AccountAdjustmentResult expected = new AccountAdjustmentResult(1L, 2L,
                Money.of("950.00", Currency.TRY), Money.of("550.00", Currency.TRY));
        when(adjustAccountBalancesUseCase.debitAndCredit(1L, 2L, amount)).thenReturn(expected);

        adapter.adjustBalances(1L, 2L, amount);

        verify(snapshotCache).evictById(1L);
        verify(snapshotCache).evictById(2L);
        verify(snapshotCache, never()).evictAll();
    }

    @Test
    void shouldEvictMutatedSnapshotsOnCancellationReversal() {
        Money amount = Money.of("50.00", Currency.TRY);
        AccountAdjustmentResult expected = new AccountAdjustmentResult(1L, 2L,
                Money.of("1000.00", Currency.TRY), Money.of("500.00", Currency.TRY));
        when(adjustAccountBalancesUseCase.reverseForCancellation(1L, 2L, amount)).thenReturn(expected);

        adapter.reverseForCancellation(1L, 2L, amount);

        verify(snapshotCache).evictById(1L);
        verify(snapshotCache).evictById(2L);
        verify(snapshotCache, never()).evictAll();
    }

    @Test
    void shouldDeferEvictionUntilAfterCommitInsideTransaction() {
        // AFTER_COMMIT rule: a rollback must never evict a still-valid
        // snapshot. Kills the registerSynchronization + afterCommit mutants.
        Money amount = Money.of("50.00", Currency.TRY);
        AccountAdjustmentResult expected = new AccountAdjustmentResult(1L, 2L,
                Money.of("950.00", Currency.TRY), Money.of("550.00", Currency.TRY));
        when(adjustAccountBalancesUseCase.debitAndCredit(1L, 2L, amount)).thenReturn(expected);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(expected, adapter.adjustBalances(1L, 2L, amount));

            // No eviction before commit.
            verify(snapshotCache, never()).evictById(anyLong());
            for (TransactionSynchronization sync :
                    TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(snapshotCache).evictById(1L);
            verify(snapshotCache).evictById(2L);
            verify(snapshotCache, never()).evictAll();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void shouldDeferReversalEvictionUntilAfterCommitInsideTransaction() {
        Money amount = Money.of("50.00", Currency.TRY);
        AccountAdjustmentResult expected = new AccountAdjustmentResult(1L, 2L,
                Money.of("1000.00", Currency.TRY), Money.of("500.00", Currency.TRY));
        when(adjustAccountBalancesUseCase.reverseForCancellation(1L, 2L, amount)).thenReturn(expected);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(expected, adapter.reverseForCancellation(1L, 2L, amount));

            verify(snapshotCache, never()).evictById(anyLong());
            for (TransactionSynchronization sync :
                    TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(snapshotCache).evictById(1L);
            verify(snapshotCache).evictById(2L);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void shouldNotEvictOnReadPaths() {
        when(accountQueryUseCase.getAccountInfo(1L))
                .thenReturn(new AccountInfo(1L, 10L, "TRY", "ACTIVE"));

        adapter.getSnapshotById(1L);
        adapter.getIbansForAccounts(Set.of(1L));

        verifyNoInteractions(snapshotCache);
    }
}
