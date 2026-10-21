package com.bank.app.transfer.adapter.out.account;

import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.accountapi.AccountApi;
import com.bank.app.accountapi.AccountNotFoundException;
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

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.bank.app.common.domain.AccountId;

import static org.awaitility.Awaitility.await;
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

    @Nested
    @DisplayName("singleflight")
    class Singleflight {
        @Test
        void shouldCollapseConcurrentMissesIntoOneBackendRead() throws Exception {
            // A thundering herd on the same key must cost one database read,
            // not one per waiting thread.
            when(accountApi.getSnapshotById(1L)).thenReturn(new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"));

            int callers = 8;
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(callers);
            var results = new java.util.concurrent.ConcurrentHashMap<Long, AccountAclPort.AccountInfo>();

            for (int i = 0; i < callers; i++) {
                Thread caller = new Thread(() -> {
                    try {
                        start.await();
                        results.put(Thread.currentThread().getId(),
                                adapter.getAccountInfo(new AccountId(1L)));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
                caller.start();
            }
            start.countDown();

            assertTrue(done.await(5, TimeUnit.SECONDS));
            verify(accountApi, times(1)).getSnapshotById(1L);
            assertEquals(callers, results.size());
        }

        @Test
        void shouldPropagateTheOriginalFailureUnwrapped() {
            // A waiter must see the published-language exception type, not a
            // CompletionException wrapper — the transfer use cases translate
            // AccountNotFoundException into a 404.
            when(accountApi.getSnapshotById(1L)).thenThrow(new AccountNotFoundException(1L));

            assertThrows(AccountNotFoundException.class, () -> adapter.getAccountInfo(new AccountId(1L)));
        }

        @Test
        void shouldShareTheLeadersFailureWithWaitingCallers() throws Exception {
            // The waiter must observe the leader's failure unwrapped. The
            // leader blocks in the loader until the waiter has joined the
            // flight, so the waiter really takes the shared-future path
            // instead of starting a second flight.
            var loaderEntered = new CountDownLatch(1);
            var releaseLoader = new CountDownLatch(1);
            when(accountApi.getSnapshotById(1L)).thenAnswer(invocation -> {
                loaderEntered.countDown();
                releaseLoader.await(5, TimeUnit.SECONDS);
                throw new AccountNotFoundException(1L);
            });

            var counting = new CountingLookupCache();
            var shared = new AccountAclAdapter(accountApi, counting);
            var leaderFailure = new AtomicReference<Throwable>();
            var waiterFailure = new AtomicReference<Throwable>();

            Thread leader = daemonThread(() -> {
                try {
                    shared.getAccountInfo(new AccountId(1L));
                } catch (Throwable failure) {
                    leaderFailure.set(failure);
                }
            });
            leader.start();
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));

            Thread waiter = daemonThread(() -> {
                try {
                    shared.getAccountInfo(new AccountId(1L));
                } catch (Throwable failure) {
                    waiterFailure.set(failure);
                }
            });
            waiter.start();
            // Three lookups: the leader probes twice (outside and inside the
            // flight), the waiter probes once before joining the flight.
            assertTrue(counting.lookups.await(5, TimeUnit.SECONDS));
            await().atMost(Duration.ofSeconds(5)).until(() ->
                    waiter.getState() == Thread.State.WAITING
                            || waiter.getState() == Thread.State.TIMED_WAITING);
            releaseLoader.countDown();
            leader.join(5_000);
            waiter.join(5_000);

            assertTrue(leaderFailure.get() instanceof AccountNotFoundException);
            assertTrue(waiterFailure.get() instanceof AccountNotFoundException,
                    "the waiting caller must see the leader's failure unwrapped, got: " + waiterFailure.get());
        }

        @Test
        void shouldShareAnErrorWithWaitingCallers() throws Exception {
            // An Error (not a RuntimeException) must reach the waiter as an
            // Error too — rewrapping it in an ExecutionException would hide
            // a fatal condition behind a checked wrapper.
            var loaderEntered = new CountDownLatch(1);
            var releaseLoader = new CountDownLatch(1);
            when(accountApi.getSnapshotById(1L)).thenAnswer(invocation -> {
                loaderEntered.countDown();
                releaseLoader.await(5, TimeUnit.SECONDS);
                throw new AssertionError("boom");
            });

            var counting = new CountingLookupCache();
            var shared = new AccountAclAdapter(accountApi, counting);
            var waiterFailure = new AtomicReference<Throwable>();

            Thread leader = daemonThread(() -> {
                try {
                    shared.getAccountInfo(new AccountId(1L));
                } catch (Throwable ignored) {
                    // the leader sees the Error directly
                }
            });
            leader.start();
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));

            Thread waiter = daemonThread(() -> {
                try {
                    shared.getAccountInfo(new AccountId(1L));
                } catch (Throwable failure) {
                    waiterFailure.set(failure);
                }
            });
            waiter.start();
            assertTrue(counting.lookups.await(5, TimeUnit.SECONDS));
            await().atMost(Duration.ofSeconds(5)).until(() ->
                    waiter.getState() == Thread.State.WAITING
                            || waiter.getState() == Thread.State.TIMED_WAITING);
            releaseLoader.countDown();
            leader.join(5_000);
            waiter.join(5_000);

            assertTrue(waiterFailure.get() instanceof AssertionError,
                    "an Error must not be rewrapped, got: " + waiterFailure.get());
        }

        @Test
        void shouldSurfaceInterruptionToWaitingCallers() throws Exception {
            // A waiting caller that is interrupted must not block forever on
            // the shared flight: the interrupt is re-raised and the caller
            // gets a failure instead of a hang.
            var loaderEntered = new CountDownLatch(1);
            var releaseLoader = new CountDownLatch(1);
            when(accountApi.getSnapshotById(1L)).thenAnswer(invocation -> {
                loaderEntered.countDown();
                releaseLoader.await(5, TimeUnit.SECONDS);
                return new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");
            });

            var counting = new CountingLookupCache();
            var shared = new AccountAclAdapter(accountApi, counting);
            var waiterFailure = new AtomicReference<Throwable>();

            Thread leader = daemonThread(() -> shared.getAccountInfo(new AccountId(1L)));
            leader.start();
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));

            Thread waiter = daemonThread(() -> {
                try {
                    shared.getAccountInfo(new AccountId(1L));
                } catch (Throwable failure) {
                    waiterFailure.set(failure);
                }
            });
            waiter.start();
            assertTrue(counting.lookups.await(5, TimeUnit.SECONDS));
            waiter.interrupt();
            waiter.join(5_000);

            assertTrue(waiterFailure.get() instanceof IllegalStateException,
                    "an interrupted waiter must fail instead of hanging, got: " + waiterFailure.get());
            // The interrupt must be re-raised, otherwise the caller's own
            // cancellation handling never sees it.
            assertTrue(waiter.isInterrupted(), "the interrupt flag must be restored");
            releaseLoader.countDown();
        }

        @Test
        void shouldTimedOutWaiterInsteadOfBlockingForever() throws Exception {
            // A stuck leader must not pin every waiter thread indefinitely.
            var loaderEntered = new CountDownLatch(1);
            var releaseLoader = new CountDownLatch(1);
            when(accountApi.getSnapshotById(1L)).thenAnswer(invocation -> {
                loaderEntered.countDown();
                releaseLoader.await(5, TimeUnit.SECONDS);
                return new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");
            });

            var counting = new CountingLookupCache();
            var shared = new AccountAclAdapter(accountApi, counting, Duration.ofMillis(100));
            var waiterFailure = new AtomicReference<Throwable>();

            Thread leader = daemonThread(() -> shared.getAccountInfo(new AccountId(1L)));
            leader.start();
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));

            Thread waiter = daemonThread(() -> {
                try {
                    shared.getAccountInfo(new AccountId(1L));
                } catch (Throwable failure) {
                    waiterFailure.set(failure);
                }
            });
            waiter.start();
            assertTrue(counting.lookups.await(5, TimeUnit.SECONDS));
            waiter.join(2_000);

            assertTrue(waiterFailure.get() instanceof IllegalStateException,
                    "a timed-out waiter must fail instead of hanging, got: " + waiterFailure.get());
            releaseLoader.countDown();
            leader.join(5_000);
        }

        private static Thread daemonThread(Runnable body) {            Thread thread = new Thread(body);
            thread.setDaemon(true);
            return thread;
        }

        /** Counts the cache lookups so a test can tell when a waiter joined the flight. */
        static class CountingLookupCache extends InMemoryAccountInfoCacheAdapter {
            final CountDownLatch lookups = new CountDownLatch(3);

            @Override
            public java.util.Optional<AccountSnapshot> getById(Long accountId) {
                lookups.countDown();
                return super.getById(accountId);
            }
        }
    }
}
