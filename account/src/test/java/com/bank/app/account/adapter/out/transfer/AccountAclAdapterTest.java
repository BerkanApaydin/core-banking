package com.bank.app.account.adapter.out.transfer;

import com.bank.app.account.application.port.in.AccountQueryUseCase;
import com.bank.app.account.application.port.in.ExecuteTransferUseCase;
import com.bank.app.account.application.port.in.ReverseTransferUseCase;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountAclAdapter")
class AccountAclAdapterTest {

    @Mock
    private AccountQueryUseCase accountQueryUseCase;

    @Mock
    private ExecuteTransferUseCase executeTransferUseCase;

    @Mock
    private ReverseTransferUseCase reverseTransferUseCase;

    private AccountAclAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountAclAdapter(accountQueryUseCase, executeTransferUseCase, reverseTransferUseCase);
    }

    @Nested
    @DisplayName("getAccountInfo")
    class GetAccountInfo {
        @Test
        void shouldDelegateToAccountQueryUseCase() {
            var source = new com.bank.app.account.application.port.in.AccountInfo(1L, 10L, "TRY", "ACTIVE");
            when(accountQueryUseCase.getAccountInfo(1L)).thenReturn(source);

            AccountAclPort.AccountInfo result = adapter.getAccountInfo(1L);

            assertEquals(1L, result.id());
            assertEquals(10L, result.userId());
            assertEquals("TRY", result.currency());
            assertEquals("ACTIVE", result.status());
            verify(accountQueryUseCase).getAccountInfo(1L);
        }
    }

    @Nested
    @DisplayName("getAccountInfoForTransfer")
    class GetAccountInfoForTransfer {
        @Test
        void shouldDelegateToQueryUseCaseWithIban() {
            var source = new com.bank.app.account.application.port.in.AccountInfo(2L, 20L, "USD", "ACTIVE");
            when(accountQueryUseCase.getAccountInfoForTransfer("TR11111")).thenReturn(source);

            AccountAclPort.AccountInfo result = adapter.getAccountInfoForTransfer("TR11111");

            assertEquals(2L, result.id());
            assertEquals("USD", result.currency());
            verify(accountQueryUseCase).getAccountInfoForTransfer("TR11111");
        }
    }

    @Nested
    @DisplayName("getIbansForAccounts")
    class GetIbansForAccounts {
        @Test
        void shouldDelegateAndReturnMap() {
            var expected = Map.of(1L, "TR11111", 2L, "TR22222");
            when(accountQueryUseCase.getIbansForAccounts(Set.of(1L, 2L))).thenReturn(expected);

            var result = adapter.getIbansForAccounts(Set.of(1L, 2L));

            assertEquals(expected, result);
            verify(accountQueryUseCase).getIbansForAccounts(Set.of(1L, 2L));
        }
    }

    @Nested
    @DisplayName("debitAndCredit")
    class DebitAndCredit {
        @Test
        void shouldDelegateExecuteTransfer() {
            Money amount = Money.of("500.00", Currency.TRY);
            adapter.debitAndCredit(1L, 2L, amount);
            verify(executeTransferUseCase).execute(1L, 2L, amount);
        }
    }

    @Nested
    @DisplayName("reverseBalancesForCancellation")
    class ReverseBalances {
        @Test
        void shouldDelegateReverseTransfer() {
            Money amount = Money.of("200.00", Currency.USD);
            adapter.reverseBalancesForCancellation(1L, 2L, amount);
            verify(reverseTransferUseCase).execute(1L, 2L, amount);
        }
    }
}
