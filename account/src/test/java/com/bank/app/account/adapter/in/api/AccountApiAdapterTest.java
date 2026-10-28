package com.bank.app.account.adapter.in.api;

import com.bank.app.account.application.dto.AccountView;
import com.bank.app.account.application.port.in.GetAccountInfoQuery;
import com.bank.app.account.application.port.in.AdjustAccountBalancesUseCase;
import com.bank.app.account.api.AccountAdjustmentResult;
import com.bank.app.account.api.AccountNotFoundException;
import com.bank.app.account.api.AccountSnapshot;
import com.bank.app.account.api.AccountSnapshotCache;
import com.bank.app.common.application.port.out.TransactionBoundaryPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.exception.BusinessFailureKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountApiAdapter (published language bridge)")
class AccountApiAdapterTest {

    @Mock
    private GetAccountInfoQuery getAccountInfoQuery;

    @Mock
    private AdjustAccountBalancesUseCase adjustAccountBalancesUseCase;

    @Mock
    private AccountSnapshotCache snapshotCache;

    @Mock
    private TransactionBoundaryPort transactionBoundary;

    private AccountApiAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountApiAdapter(getAccountInfoQuery, adjustAccountBalancesUseCase,
                snapshotCache, transactionBoundary);
    }

    @Test
    void shouldTranslateAccountInfoToSnapshot() {
        when(getAccountInfoQuery.getAccountInfo(1L))
                .thenReturn(new AccountView(1L, 10L, "TRY", "ACTIVE"));

        AccountSnapshot snapshot = adapter.getSnapshotById(1L);

        assertEquals(new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"), snapshot);
    }

    @Test
    void shouldTranslateIbanLookupToSnapshot() {
        when(getAccountInfoQuery.getAccountInfoForTransfer("TR450006100519786456841234"))
                .thenReturn(new AccountView(1L, 10L, "TRY", "ACTIVE"));

        AccountSnapshot snapshot = adapter.getSnapshotByIban("TR450006100519786456841234");

        assertEquals(1L, snapshot.id());
        assertEquals(10L, snapshot.userId());
    }

    @Test
    void shouldPropagatePublishedNotFoundById() {
        when(getAccountInfoQuery.getAccountInfo(999L))
                .thenThrow(new AccountNotFoundException(999L));

        AccountNotFoundException ex = assertThrows(
                AccountNotFoundException.class,
                () -> adapter.getSnapshotById(999L));

        assertEquals("Account not found. ID: 999", ex.getMessage());
        assertEquals(BusinessFailureKind.NOT_FOUND, ex.getFailureKind());
        assertEquals("ACCOUNT_NOT_FOUND_ID", ex.getErrorCode());
    }

    @Test
    void shouldPropagatePublishedNotFoundByIban() {
        when(getAccountInfoQuery.getAccountInfoForTransfer("TR000"))
                .thenThrow(new AccountNotFoundException("TR000"));

        AccountNotFoundException ex = assertThrows(
                AccountNotFoundException.class,
                () -> adapter.getSnapshotByIban("TR000"));

        assertEquals("Account not found. IBAN: ***", ex.getMessage());
        assertEquals(BusinessFailureKind.NOT_FOUND, ex.getFailureKind());
        assertEquals("ACCOUNT_NOT_FOUND_IBAN", ex.getErrorCode());
    }

    @Test
    void shouldDelegateIbanBatchLookup() {
        when(getAccountInfoQuery.getIbansForAccounts(Set.of(1L)))
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
    void shouldRejectNullAmountOnBalanceAdjustment() {
        // This is the published Open Host Service boundary: a null amount must
        // fail fast here instead of reaching the money-moving use case.
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> adapter.adjustBalances(1L, 2L, null));

        assertEquals("Amount must not be null", ex.getMessage());
        verifyNoInteractions(adjustAccountBalancesUseCase, snapshotCache);
    }

    @Test
    void shouldRejectNullAmountOnCancellationReversal() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> adapter.reverseForCancellation(1L, 2L, null));

        assertEquals("Amount must not be null", ex.getMessage());
        verifyNoInteractions(adjustAccountBalancesUseCase, snapshotCache);
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
        // snapshot. The port defers the eviction; running the captured hook
        // simulates the commit.
        Money amount = Money.of("50.00", Currency.TRY);
        AccountAdjustmentResult expected = new AccountAdjustmentResult(1L, 2L,
                Money.of("950.00", Currency.TRY), Money.of("550.00", Currency.TRY));
        when(adjustAccountBalancesUseCase.debitAndCredit(1L, 2L, amount)).thenReturn(expected);
        when(transactionBoundary.isTransactionActive()).thenReturn(true);

        assertSame(expected, adapter.adjustBalances(1L, 2L, amount));

        // No eviction before commit.
        verify(snapshotCache, never()).evictById(anyLong());
        ArgumentCaptor<Runnable> afterCommit = ArgumentCaptor.forClass(Runnable.class);
        verify(transactionBoundary).runAfterCommit(afterCommit.capture());
        afterCommit.getValue().run();
        verify(snapshotCache).evictById(1L);
        verify(snapshotCache).evictById(2L);
        verify(snapshotCache, never()).evictAll();
    }

    @Test
    void shouldDeferReversalEvictionUntilAfterCommitInsideTransaction() {
        Money amount = Money.of("50.00", Currency.TRY);
        AccountAdjustmentResult expected = new AccountAdjustmentResult(1L, 2L,
                Money.of("1000.00", Currency.TRY), Money.of("500.00", Currency.TRY));
        when(adjustAccountBalancesUseCase.reverseForCancellation(1L, 2L, amount)).thenReturn(expected);
        when(transactionBoundary.isTransactionActive()).thenReturn(true);

        assertSame(expected, adapter.reverseForCancellation(1L, 2L, amount));

        verify(snapshotCache, never()).evictById(anyLong());
        ArgumentCaptor<Runnable> afterCommit = ArgumentCaptor.forClass(Runnable.class);
        verify(transactionBoundary).runAfterCommit(afterCommit.capture());
        afterCommit.getValue().run();
        verify(snapshotCache).evictById(1L);
        verify(snapshotCache).evictById(2L);
    }

    @Test
    void shouldNotEvictOnReadPaths() {
        when(getAccountInfoQuery.getAccountInfo(1L))
                .thenReturn(new AccountView(1L, 10L, "TRY", "ACTIVE"));

        adapter.getSnapshotById(1L);
        adapter.getIbansForAccounts(Set.of(1L));

        verifyNoInteractions(snapshotCache);
    }
}
