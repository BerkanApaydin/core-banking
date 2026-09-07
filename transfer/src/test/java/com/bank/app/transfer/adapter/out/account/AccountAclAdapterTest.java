package com.bank.app.transfer.adapter.out.account;

import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.accountapi.AccountApi;
import com.bank.app.accountapi.AccountSnapshot;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.port.out.AccountAclPort;
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
    private AccountApi accountApi;

    private InMemoryAccountInfoCacheAdapter cache;

    private AccountAclAdapter adapter;

    @BeforeEach
    void setUp() {
        cache = new InMemoryAccountInfoCacheAdapter();
        adapter = new AccountAclAdapter(accountApi, cache);
    }

    @Nested
    @DisplayName("getAccountInfo")
    class GetAccountInfo {
        @Test
        void shouldMapAccountInfoWhenAccountExists() {
            when(accountApi.getSnapshotById(1L))
                    .thenReturn(new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"));

            AccountAclPort.AccountInfo result = adapter.getAccountInfo(1L);

            assertEquals(1L, result.id());
            assertEquals(10L, result.userId());
            assertEquals("TRY", result.currency());
            assertEquals("ACTIVE", result.status());
            verify(accountApi).getSnapshotById(1L);
            verifyNoMoreInteractions(accountApi);
        }
    }

    @Nested
    @DisplayName("getAccountInfoForTransfer")
    class GetAccountInfoForTransfer {
        @Test
        void shouldMapAccountInfoForValidIban() {
            when(accountApi.getSnapshotByIban("TR330006100519786456841234"))
                    .thenReturn(new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"));

            AccountAclPort.AccountInfo result =
                    adapter.getAccountInfoForTransfer("TR330006100519786456841234");

            assertEquals(1L, result.id());
            assertEquals("TRY", result.currency());
            verify(accountApi).getSnapshotByIban("TR330006100519786456841234");
        }
    }

    @Nested
    @DisplayName("getIbansForAccounts")
    class GetIbansForAccounts {
        @Test
        void shouldDelegateIbanLookup() {
            when(accountApi.getIbansForAccounts(Set.of(1L, 2L)))
                    .thenReturn(Map.of(1L, "TR330006100519786456841234", 2L, "TR660006100519786456841235"));

            var result = adapter.getIbansForAccounts(Set.of(1L, 2L));

            assertEquals("TR330006100519786456841234", result.get(1L));
            assertEquals("TR660006100519786456841235", result.get(2L));
            verify(accountApi).getIbansForAccounts(Set.of(1L, 2L));
        }
    }

    @Nested
    @DisplayName("debitAndCredit")
    class DebitAndCredit {
        @Test
        void shouldDelegateToAccountApi() {
            Money amount = Money.of("200.00", Currency.TRY);
            AccountAdjustmentResult apiResult = new AccountAdjustmentResult(1L, 2L,
                    Money.of("800.00", Currency.TRY), Money.of("1200.00", Currency.TRY));
            when(accountApi.adjustBalances(1L, 2L, amount)).thenReturn(apiResult);

            AccountAclPort.MutationResult result = adapter.debitAndCredit(1L, 2L, amount);

            assertEquals(1L, result.senderAccountId());
            assertEquals(2L, result.receiverAccountId());
            assertEquals(Money.of("800.00", Currency.TRY), result.senderNewBalance());
            assertEquals(Money.of("1200.00", Currency.TRY), result.receiverNewBalance());
            verify(accountApi).adjustBalances(1L, 2L, amount);
            verifyNoMoreInteractions(accountApi);
        }

        @Test
        void shouldRejectNullAmount() {
            assertThrows(NullPointerException.class, () -> adapter.debitAndCredit(1L, 2L, null));
            verifyNoInteractions(accountApi);
        }
    }

    @Nested
    @DisplayName("reverseBalancesForCancellation")
    class ReverseBalances {
        @Test
        void shouldDelegateToAccountApi() {
            Money amount = Money.of("200.00", Currency.TRY);
            AccountAdjustmentResult apiResult = new AccountAdjustmentResult(1L, 2L,
                    Money.of("1000.00", Currency.TRY), Money.of("1000.00", Currency.TRY));
            when(accountApi.reverseForCancellation(1L, 2L, amount)).thenReturn(apiResult);

            AccountAclPort.MutationResult result = adapter.reverseBalancesForCancellation(1L, 2L, amount);

            assertEquals(1L, result.senderAccountId());
            assertEquals(2L, result.receiverAccountId());
            verify(accountApi).reverseForCancellation(1L, 2L, amount);
            verifyNoMoreInteractions(accountApi);
        }
    }

    @Nested
    @DisplayName("granular cache invalidation")
    class GranularInvalidation {
        @Test
        void shouldEvictOnlyMutatedAccountsOnDebitAndCredit() {
            Money amount = Money.of("200.00", Currency.TRY);
            AccountAdjustmentResult apiResult = new AccountAdjustmentResult(1L, 2L,
                    Money.of("800.00", Currency.TRY), Money.of("1200.00", Currency.TRY));
            when(accountApi.adjustBalances(1L, 2L, amount)).thenReturn(apiResult);

            // unrelated cached entry must survive the mutation
            cache.putById(99L, new AccountAclPort.AccountInfo(99L, 30L, "TRY", "ACTIVE"));
            cache.putById(1L, new AccountAclPort.AccountInfo(1L, 10L, "TRY", "ACTIVE"));

            adapter.debitAndCredit(1L, 2L, amount);

            assertTrue(cache.getById(1L).isEmpty(), "mutated sender must be evicted");
            assertTrue(cache.getById(99L).isPresent(), "unrelated account must stay cached");
        }

        @Test
        void shouldEvictOnlyMutatedAccountsOnReverse() {
            Money amount = Money.of("200.00", Currency.TRY);
            AccountAdjustmentResult apiResult = new AccountAdjustmentResult(1L, 2L,
                    Money.of("1000.00", Currency.TRY), Money.of("1000.00", Currency.TRY));
            when(accountApi.reverseForCancellation(1L, 2L, amount)).thenReturn(apiResult);

            cache.putById(99L, new AccountAclPort.AccountInfo(99L, 30L, "TRY", "ACTIVE"));

            adapter.reverseBalancesForCancellation(1L, 2L, amount);

            assertTrue(cache.getById(99L).isPresent(), "unrelated account must stay cached");
        }

        @Test
        void shouldEvictIbanEntryOnDebitAndCredit() {
            String senderIban = "TR330006100519786456841234";
            Money amount = Money.of("200.00", Currency.TRY);
            AccountAdjustmentResult apiResult = new AccountAdjustmentResult(1L, 2L,
                    Money.of("800.00", Currency.TRY), Money.of("1200.00", Currency.TRY));
            when(accountApi.getSnapshotByIban(senderIban))
                    .thenReturn(new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"));
            when(accountApi.adjustBalances(1L, 2L, amount)).thenReturn(apiResult);

            adapter.getAccountInfoForTransfer(senderIban);

            adapter.debitAndCredit(1L, 2L, amount);

            // IBAN entry must be re-fetched (stale status like ACTIVE-after-suspend
            // must never be served); second read hits the AccountApi again.
            adapter.getAccountInfoForTransfer(senderIban);
            verify(accountApi, times(2)).getSnapshotByIban(senderIban);
        }
    }
}
