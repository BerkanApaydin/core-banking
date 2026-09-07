package com.bank.app.account.adapter.in.api;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.AccountQueryUseCase;
import com.bank.app.account.application.port.in.AdjustAccountBalancesUseCase;
import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private AccountQueryUseCase accountQueryUseCase;

    @Mock
    private AdjustAccountBalancesUseCase adjustAccountBalancesUseCase;

    private AccountApiAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountApiAdapter(accountQueryUseCase, adjustAccountBalancesUseCase);
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
        when(accountQueryUseCase.getAccountInfoForTransfer("TR330006100519786456841234"))
                .thenReturn(new AccountInfo(1L, 10L, "TRY", "ACTIVE"));

        AccountSnapshot snapshot = adapter.getSnapshotByIban("TR330006100519786456841234");

        assertEquals(1L, snapshot.id());
        assertEquals(10L, snapshot.userId());
    }

    @Test
    void shouldDelegateIbanBatchLookup() {
        when(accountQueryUseCase.getIbansForAccounts(Set.of(1L)))
                .thenReturn(Map.of(1L, "TR330006100519786456841234"));

        assertEquals("TR330006100519786456841234",
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
}
