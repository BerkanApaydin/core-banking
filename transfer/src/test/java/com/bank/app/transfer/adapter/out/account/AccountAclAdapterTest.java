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
import com.bank.app.common.domain.AccountId;

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

            AccountAclPort.AccountInfo result = adapter.getAccountInfo(new AccountId(1L));

            assertEquals(new AccountId(1L), result.id());
            assertEquals(10L, result.userId());
            assertEquals("TRY", result.currency());
            assertEquals("ACTIVE", result.status());
            verify(accountApi).getSnapshotById(1L);
            verifyNoMoreInteractions(accountApi);
        }

        @Test
        void shouldWriteThroughCacheSoSecondReadSkipsAccountApi() {
            when(accountApi.getSnapshotById(1L))
                    .thenReturn(new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"));

            adapter.getAccountInfo(new AccountId(1L));
            adapter.getAccountInfo(new AccountId(1L));

            // Mutant (removed putById) serves the second read from the API again.
            verify(accountApi, times(1)).getSnapshotById(1L);
        }
    }

    @Nested
    @DisplayName("getAccountInfoForTransfer")
    class GetAccountInfoForTransfer {
        @Test
        void shouldMapAccountInfoForValidIban() {
            when(accountApi.getSnapshotByIban("TR450006100519786456841234"))
                    .thenReturn(new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"));

            AccountAclPort.AccountInfo result =
                    adapter.getAccountInfoForTransfer("TR450006100519786456841234");

            assertEquals(new AccountId(1L), result.id());
            assertEquals("TRY", result.currency());
            verify(accountApi).getSnapshotByIban("TR450006100519786456841234");
        }

        @Test
        void shouldWriteThroughIbanCacheSoSecondReadSkipsAccountApi() {
            when(accountApi.getSnapshotByIban("TR450006100519786456841234"))
                    .thenReturn(new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"));

            adapter.getAccountInfoForTransfer("TR450006100519786456841234");
            adapter.getAccountInfoForTransfer("TR450006100519786456841234");

            verify(accountApi, times(1)).getSnapshotByIban("TR450006100519786456841234");
        }
    }

    @Nested
    @DisplayName("getIbansForAccounts")
    class GetIbansForAccounts {
        @Test
        void shouldDelegateIbanLookup() {
            when(accountApi.getIbansForAccounts(Set.of(1L, 2L)))
                    .thenReturn(Map.of(1L, "TR450006100519786456841234", 2L, "TR180006100519786456841235"));

            var result = adapter.getIbansForAccounts(Set.of(1L, 2L));

            assertEquals("TR450006100519786456841234", result.get(1L));
            assertEquals("TR180006100519786456841235", result.get(2L));
            verify(accountApi).getIbansForAccounts(Set.of(1L, 2L));
        }

        @Test
        void shouldWriteThroughIbanMapSoSecondReadSkipsAccountApi() {
            when(accountApi.getIbansForAccounts(Set.of(1L, 2L)))
                    .thenReturn(Map.of(1L, "TR450006100519786456841234", 2L, "TR180006100519786456841235"));

            adapter.getIbansForAccounts(Set.of(1L, 2L));
            adapter.getIbansForAccounts(Set.of(1L, 2L));

            verify(accountApi, times(1)).getIbansForAccounts(Set.of(1L, 2L));
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

            AccountAclPort.MutationResult result = adapter.debitAndCredit(new AccountId(1L), new AccountId(2L), amount);

            assertEquals(new AccountId(1L), result.senderAccountId());
            assertEquals(new AccountId(2L), result.receiverAccountId());
            assertEquals(Money.of("800.00", Currency.TRY), result.senderNewBalance());
            assertEquals(Money.of("1200.00", Currency.TRY), result.receiverNewBalance());
            verify(accountApi).adjustBalances(1L, 2L, amount);
            verifyNoMoreInteractions(accountApi);
        }

        @Test
        void shouldRejectNullAmount() {
            assertThrows(NullPointerException.class, () -> adapter.debitAndCredit(new AccountId(1L), new AccountId(2L), null));
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

            AccountAclPort.MutationResult result = adapter.reverseBalancesForCancellation(new AccountId(1L), new AccountId(2L), amount);

            assertEquals(new AccountId(1L), result.senderAccountId());
            assertEquals(new AccountId(2L), result.receiverAccountId());
            verify(accountApi).reverseForCancellation(1L, 2L, amount);
            verifyNoMoreInteractions(accountApi);
        }
    }

    @Nested
    @DisplayName("cache invalidation ownership")
    class InvalidationOwnership {
        @Test
        void shouldNotEvictOnMutationBecauseAccountBoundaryOwnsInvalidation() {
            // Granular eviction lives in AccountApiAdapter (account boundary):
            // this adapter must not double-evict on the mutation hot path.
            Money amount = Money.of("200.00", Currency.TRY);
            AccountAdjustmentResult apiResult = new AccountAdjustmentResult(1L, 2L,
                    Money.of("800.00", Currency.TRY), Money.of("1200.00", Currency.TRY));
            when(accountApi.adjustBalances(1L, 2L, amount)).thenReturn(apiResult);

            adapter.debitAndCredit(new AccountId(1L), new AccountId(2L), amount);

            assertTrue(cache.getById(99L).isEmpty());
            verifyNoMoreInteractions(accountApi);
        }
    }
}
