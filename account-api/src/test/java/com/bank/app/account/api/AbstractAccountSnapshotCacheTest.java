package com.bank.app.account.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * Exercises {@link AbstractAccountSnapshotCache} invalidation semantics
 * directly: the Caffeine/in-memory backends only provide storage, so mutants
 * in the shared base must die here, not in downstream modules.
 */
class AbstractAccountSnapshotCacheTest {

    private static final AccountSnapshot SNAPSHOT_1 = new AccountSnapshot(1L, 10L, "TRY", "ACTIVE");
    private static final AccountSnapshot SNAPSHOT_2 = new AccountSnapshot(2L, 20L, "USD", "SUSPENDED");

    private TestCache cache;

    static class TestCache extends AbstractAccountSnapshotCache {
        final Map<String, AccountSnapshot> snapshots = new ConcurrentHashMap<>();
        final Map<Long, String> mappings = new ConcurrentHashMap<>();

        @Override
        protected Optional<AccountSnapshot> readSnapshot(String key) {
            return Optional.ofNullable(snapshots.get(key));
        }

        @Override
        protected void writeSnapshot(String key, AccountSnapshot snapshot) {
            snapshots.put(key, snapshot);
        }

        @Override
        protected void removeSnapshot(String key) {
            snapshots.remove(key);
        }

        @Override
        protected Optional<String> readIbanMapping(Long accountId) {
            return Optional.ofNullable(mappings.get(accountId));
        }

        @Override
        protected void writeIbanMapping(Long accountId, String iban) {
            mappings.put(accountId, iban);
        }

        @Override
        protected void removeIbanMapping(Long accountId) {
            mappings.remove(accountId);
        }

        @Override
        protected void clearStorage() {
            snapshots.clear();
            mappings.clear();
        }
    }

    @BeforeEach
    void setUp() {
        cache = new TestCache();
    }

    @Test
    void shouldMissThenHitById() {
        assertThat(cache.getById(1L)).isEmpty();

        cache.putById(1L, SNAPSHOT_1);

        assertThat(cache.getById(1L)).contains(SNAPSHOT_1);
    }

    @Test
    void shouldIgnoreNullIdOperations() {
        cache.putById(null, SNAPSHOT_1);

        assertThat(cache.getById(null)).isEmpty();
        assertThat(cache.snapshots).isEmpty();
    }

    @Test
    void shouldIgnoreNullSnapshotOnPut() {
        cache.putById(1L, null);
        cache.putByIban("TR1", null);

        assertThat(cache.getById(1L)).isEmpty();
        assertThat(cache.getByIban("TR1")).isEmpty();
    }

    @Test
    void shouldNormalizeIbanKeys() {
        cache.putByIban("tr45 0006 1005 1978 6456 8412 34", SNAPSHOT_1);

        assertThat(cache.getByIban("TR450006100519786456841234")).contains(SNAPSHOT_1);
    }

    @Test
    void shouldReturnEmptyForNullIban() {
        assertThat(cache.getByIban(null)).isEmpty();
    }

    @Test
    void shouldCacheIbansBatch() {
        var ids = Set.of(1L, 2L);
        assertThat(cache.getIbans(ids)).isEmpty();

        var ibans = Map.of(1L, "TR1", 2L, "TR2");
        cache.putIbans(ids, ibans);

        assertThat(cache.getIbans(ids)).contains(ibans);
    }

    @Test
    void shouldShareMappingsAcrossDifferentIdSets() {
        // One mapping per id (not one key per id-set): a different combination
        // over the same accounts is a hit, not a new cache entry.
        cache.putIbans(Set.of(1L, 2L), Map.of(1L, "TR1", 2L, "TR2"));

        assertThat(cache.getIbans(Set.of(2L, 1L))).contains(Map.of(1L, "TR1", 2L, "TR2"));
        assertThat(cache.getIbans(Set.of(2L))).contains(Map.of(2L, "TR2"));
    }

    @Test
    void shouldMissBulkReadOnPartialHit() {
        cache.putIbans(Set.of(1L), Map.of(1L, "TR1"));

        assertThat(cache.getIbans(Set.of(1L, 2L))).isEmpty();
    }

    @Test
    void shouldIgnoreNullOrEmptyBatchPuts() {
        cache.putIbans(null, Map.of(1L, "TR1"));
        cache.putIbans(Set.of(1L), null);
        cache.putIbans(Set.of(1L), Map.of());

        assertThat(cache.mappings).isEmpty();
    }

    @Test
    void shouldReturnEmptyBatchForNullIds() {
        assertThat(cache.getIbans(null)).isEmpty();
    }

    @Test
    void shouldMissBulkReadWhenAnyIdIsNull() {
        cache.putIbans(Set.of(1L), Map.of(1L, "TR1"));

        assertThat(cache.getIbans(java.util.Arrays.asList(1L, null))).isEmpty();
    }

    @Test
    void shouldIgnoreBatchPutsWithoutWritableEntries() {
        Map<Long, String> entries = new LinkedHashMap<>();
        entries.put(1L, "");
        entries.put(null, "TR1");

        cache.putIbans(Set.of(1L), entries);

        assertThat(cache.mappings).isEmpty();
    }

    @Test
    void shouldEvictIdAndItsIbanEntriesButKeepUnrelated() {
        cache.putById(1L, SNAPSHOT_1);
        cache.putByIban("TR450006100519786456841234", SNAPSHOT_1);
        cache.putById(2L, SNAPSHOT_2);

        cache.evictById(1L);

        assertThat(cache.getById(1L)).isEmpty();
        assertThat(cache.getByIban("TR450006100519786456841234")).isEmpty();
        assertThat(cache.getById(2L)).contains(SNAPSHOT_2);
    }

    @Test
    void shouldDropIbanMappingOnEvictById() {
        cache.putIbans(Set.of(1L, 2L), Map.of(1L, "TR1", 2L, "TR2"));

        cache.evictById(1L);

        assertThat(cache.getIbans(Set.of(1L))).isEmpty();
        assertThat(cache.getIbans(Set.of(2L))).contains(Map.of(2L, "TR2"));
    }

    @Test
    void shouldIgnoreNullEvictions() {
        cache.putById(1L, SNAPSHOT_1);

        cache.evictById(null);

        assertThat(cache.getById(1L)).contains(SNAPSHOT_1);
    }

    @Test
    void shouldNotLeakStaleIndexAcrossEvictAll() {
        cache.putByIban("TR1", SNAPSHOT_1);
        cache.evictAll();
        cache.putByIban("TR1", SNAPSHOT_2);

        // Entry now belongs to id 2; evicting id 1 must not touch it.
        cache.evictById(1L);

        assertThat(cache.getByIban("TR1")).contains(SNAPSHOT_2);
    }

    /**
     * A failing region clear must not be reported as success: the exception
     * has to reach the caller so the refresh path can surface it.
     */
    @Test
    void shouldPropagateAFailedEvictAll() {
        FailingClearCache failing = new FailingClearCache();
        failing.putByIban("TR1", SNAPSHOT_1);

        assertThatThrownBy(failing::evictAll)
                .as("a failing region clear must not be reported as success")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldEvictAll() {
        cache.putById(1L, SNAPSHOT_1);
        cache.putByIban("TR1", SNAPSHOT_1);
        cache.putIbans(Set.of(1L), Map.of(1L, "TR1"));

        cache.evictAll();

        assertThat(cache.getById(1L)).isEmpty();
        assertThat(cache.getByIban("TR1")).isEmpty();
        assertThat(cache.getIbans(Set.of(1L))).isEmpty();
    }

    /**
     * A write that is paused between the snapshot write and the reverse-index
     * registration must not be able to have its index entry dropped by a
     * concurrent eviction of the same account: the eviction waits for the
     * write to finish, so the IBAN entry it drops is the one that exists.
     */
    @Test
    void shouldNotInterleaveEvictionWithIbanRegistration() throws Exception {
        BlockingTestCache blocking = new BlockingTestCache();
        blocking.blockNextIbanWrite = true;

        Thread writer = daemonThread(() -> blocking.putByIban("TR1", SNAPSHOT_1));
        writer.start();
        assertThat(blocking.writeStarted.await(5, TimeUnit.SECONDS)).isTrue();

        var evictionDone = new CountDownLatch(1);
        Thread evictor = daemonThread(() -> {
            blocking.evictById(1L);
            evictionDone.countDown();
        });
        evictor.start();

        try {
            // The eviction of the SAME account must wait for the write.
            assertThat(evictionDone.await(200, TimeUnit.MILLISECONDS))
                    .as("eviction of the same account must not interleave with the IBAN registration")
                    .isFalse();
        } finally {
            blocking.releaseWrite.countDown();
        }
        writer.join(200);
        evictor.join(200);

        assertThat(evictionDone.await(200, TimeUnit.MILLISECONDS))
                .as("eviction must complete once the write releases the stripe")
                .isTrue();
        // The write completed, so the eviction removed the IBAN snapshot.
        assertThat(blocking.getByIban("TR1")).isEmpty();
    }

    /**
     * A batch mapping write holds the stripe of every account it touches, so
     * an eviction of one of those accounts cannot slip between two mapping
     * writes and leave a stale mapping behind.
     */
    @Test
    void shouldNotInterleaveEvictionWithBatchMappingWrite() throws Exception {
        BlockingTestCache blocking = new BlockingTestCache();
        blocking.blockNextMappingWrite = true;
        Map<Long, String> ibans = new LinkedHashMap<>();
        ibans.put(1L, "TR1");
        ibans.put(2L, "TR2");

        Thread writer = daemonThread(() -> blocking.putIbans(Set.of(1L, 2L), ibans));
        writer.start();
        assertThat(blocking.mappingWriteStarted.await(5, TimeUnit.SECONDS)).isTrue();

        var evictionDone = new CountDownLatch(1);
        Thread evictor = daemonThread(() -> {
            blocking.evictById(1L);
            evictionDone.countDown();
        });
        evictor.start();

        try {
            assertThat(evictionDone.await(200, TimeUnit.MILLISECONDS))
                    .as("eviction of an account in the batch must wait for the mapping write")
                    .isFalse();
        } finally {
            blocking.releaseMappingWrite.countDown();
        }
        writer.join(200);
        evictor.join(200);

        assertThat(evictionDone.await(200, TimeUnit.MILLISECONDS))
                .as("eviction must complete once the batch write releases its stripes")
                .isTrue();
        // The batch write completed, so the eviction removed its mapping.
        assertThat(blocking.getIbans(Set.of(1L))).isEmpty();
    }

    /**
     * The per-account lock must not become an instance-wide monitor: an
     * eviction of an unrelated account proceeds while another account's write
     * is in flight.
     */
    @Test
    void shouldNotBlockUnrelatedAccounts() throws Exception {
        BlockingTestCache blocking = new BlockingTestCache();
        blocking.blockNextIbanWrite = true;

        Thread writer = daemonThread(() -> blocking.putByIban("TR1", SNAPSHOT_1));
        writer.start();
        assertThat(blocking.writeStarted.await(5, TimeUnit.SECONDS)).isTrue();

        var evictionDone = new CountDownLatch(1);
        Thread evictor = daemonThread(() -> {
            blocking.evictById(2L);
            evictionDone.countDown();
        });
        evictor.start();

        try {
            assertThat(evictionDone.await(200, TimeUnit.MILLISECONDS))
                    .as("an eviction of an unrelated account must not wait for another account's write")
                    .isTrue();
        } finally {
            blocking.releaseWrite.countDown();
        }
        writer.join(5_000);
        evictor.join(5_000);
    }

    /**
     * A full reset is a global barrier: it must not interleave with an
     * in-flight per-account write.
     */
    @Test
    void shouldMakeEvictAllWaitForAnInFlightWrite() throws Exception {
        BlockingTestCache blocking = new BlockingTestCache();
        blocking.blockNextIbanWrite = true;

        Thread writer = daemonThread(() -> blocking.putByIban("TR1", SNAPSHOT_1));
        writer.start();
        assertThat(blocking.writeStarted.await(5, TimeUnit.SECONDS)).isTrue();

        var resetDone = new CountDownLatch(1);
        Thread resetter = daemonThread(() -> {
            blocking.evictAll();
            resetDone.countDown();
        });
        resetter.start();

        try {
            assertThat(resetDone.await(200, TimeUnit.MILLISECONDS))
                    .as("evict-all must wait for the in-flight write")
                    .isFalse();
        } finally {
            blocking.releaseWrite.countDown();
        }
        writer.join(200);
        resetter.join(200);
        assertThat(resetDone.await(200, TimeUnit.MILLISECONDS))
                .as("evict-all must complete once the write releases the stripe")
                .isTrue();
    }

    /**
     * The account locks must be free again after a full reset and after a
     * granular eviction — otherwise the next mutation would block forever.
     */
    @Test
    void shouldReleaseEveryStripeAfterBatchWrite() throws Exception {
        cache.putIbans(Set.of(1L, 2L), Map.of(1L, "TR1", 2L, "TR2"));

        var done = new CountDownLatch(1);
        Thread worker = daemonThread(() -> {
            cache.evictAll();
            done.countDown();
        });
        worker.start();

        assertThat(done.await(200, TimeUnit.MILLISECONDS))
                .as("a batch write must release every stripe it acquired")
                .isTrue();
        worker.join(200);
    }

    /**
     * A failed batch mapping write must still release every stripe it
     * acquired; otherwise the next full reset would block forever.
     */
    @Test
    void shouldReleaseEveryStripeWhenBatchWriteFails() throws Exception {
        FailingMappingWriteCache failing = new FailingMappingWriteCache();

        assertThatThrownBy(() -> failing.putIbans(Set.of(1L, 2L),
                Map.of(1L, "TR1", 2L, "TR2")))
                .isInstanceOf(IllegalStateException.class);

        var done = new CountDownLatch(1);
        Thread worker = daemonThread(() -> {
            failing.evictAll();
            done.countDown();
        });
        worker.start();

        assertThat(done.await(1, TimeUnit.SECONDS))
                .as("a failed batch write must release every stripe it acquired")
                .isTrue();
        worker.join(200);
    }

    @Test
    void shouldReleaseAccountLocksAfterEvictAllAndEvictById() throws Exception {
        cache.evictById(1L);
        cache.evictAll();

        var done = new CountDownLatch(1);
        Thread worker = daemonThread(() -> {
            cache.evictById(1L);
            done.countDown();
        });
        worker.start();

        assertThat(done.await(200, TimeUnit.MILLISECONDS))
                .as("the account lock must be released after eviction")
                .isTrue();
        worker.join(200);
    }

    @Test
    void shouldReleaseEveryStripeAfterEvictAll() throws Exception {
        cache.evictAll();

        var done = new CountDownLatch(1);
        Thread worker = daemonThread(() -> {
            cache.putById(0L, SNAPSHOT_1);
            done.countDown();
        });
        worker.start();

        assertThat(done.await(200, TimeUnit.MILLISECONDS))
                .as("evict-all must release the first stripe as well")
                .isTrue();
        worker.join(200);
    }

    @Test
    void shouldReleaseTheStripeLockAfterPutById() throws Exception {
        cache.putById(0L, SNAPSHOT_1);

        var done = new CountDownLatch(1);
        Thread worker = daemonThread(() -> {
            cache.putById(0L, SNAPSHOT_2);
            done.countDown();
        });
        worker.start();

        assertThat(done.await(200, TimeUnit.MILLISECONDS))
                .as("putById must release its stripe lock")
                .isTrue();
        worker.join(200);
    }

    @Test
    void shouldMissBulkReadWhenAStoredMappingIsEmpty() {
        cache.mappings.put(1L, "");

        assertThat(cache.getIbans(Set.of(1L))).isEmpty();
    }

    @Test
    void shouldIgnoreNullIbanMappingsButWriteValidSiblings() {
        Map<Long, String> entries = new LinkedHashMap<>();
        entries.put(1L, null);
        entries.put(2L, "TR2");

        cache.putIbans(Set.of(1L, 2L), entries);

        assertThat(cache.mappings).doesNotContainKey(1L).containsEntry(2L, "TR2");
        assertThat(cache.getIbans(Set.of(2L))).contains(Map.of(2L, "TR2"));
    }

    @Test
    void shouldServeTheLatestSnapshotAfterARefresh() {
        cache.putById(1L, SNAPSHOT_1);
        cache.putByIban("TR1", SNAPSHOT_1);

        AccountSnapshot refreshed = new AccountSnapshot(1L, 10L, "TRY", "SUSPENDED");
        cache.putById(1L, refreshed);
        cache.putByIban("TR1", refreshed);

        assertThat(cache.getById(1L)).contains(refreshed);
        assertThat(cache.getByIban("TR1")).contains(refreshed);
    }

    @Test
    void shouldEvictTheIbanEntryWhenTheEvictedIdMatchesTheSnapshotId() {
        AccountSnapshot snapshotOfAccount99 = new AccountSnapshot(99L, 10L, "TRY", "ACTIVE");
        cache.putByIban("TR99", snapshotOfAccount99);

        cache.evictById(99L);

        assertThat(cache.getByIban("TR99")).isEmpty();
    }

    @Test
    void shouldCacheOnlyTheAccountsThatCarryAMapping() {
        cache.putIbans(Set.of(1L, 2L), Map.of(1L, "TR1"));

        assertThat(cache.getIbans(List.of(1L))).contains(Map.of(1L, "TR1"));
        assertThat(cache.getIbans(List.of(2L)))
                .as("the mapping is authoritative: account 2 was never mapped, so it stays a miss")
                .isEmpty();
    }

    @Test
    void shouldCacheAMappingForAnAccountOutsideTheRequestedSet() {
        cache.putIbans(Set.of(1L), Map.of(2L, "TR2"));

        assertThat(cache.getIbans(List.of(1L)))
                .as("the requested id carries no mapping of its own")
                .isEmpty();
        assertThat(cache.getIbans(List.of(2L)))
                .as("the map is authoritative: an account nobody asked for is cached anyway")
                .contains(Map.of(2L, "TR2"));
    }

    @Test
    void shouldTreatAnEmptyBulkRequestAsACacheMiss() {
        assertThat(cache.getIbans(List.of()))
                .as("an empty page is indistinguishable from a miss, so it costs a DB round-trip")
                .isEmpty();
    }

    @Test
    void shouldReturnAnImmutableIbanMapping() {
        cache.putIbans(Set.of(1L), Map.of(1L, "TR1"));

        Map<Long, String> cached = cache.getIbans(List.of(1L)).orElseThrow();

        assertThat(cached).containsExactly(entry(1L, "TR1"));
        assertThatThrownBy(() -> cached.put(2L, "TR2"))
                .as("a published-language result must not be mutable by the caller")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * A blank IBAN normalizes to the empty key, so every blank-IBAN caller
     * shares ONE cache entry: the last snapshot written under it is served
     * to all of them.
     */
    @Test
    void shouldLetBlankIbansShareOneCacheEntry() {
        cache.putByIban("", new AccountSnapshot(1L, 10L, "TRY", "ACTIVE"));
        AccountSnapshot second = new AccountSnapshot(2L, 20L, "USD", "ACTIVE");
        cache.putByIban("   ", second);

        assertThat(cache.getByIban(""))
                .as("both blank IBANs address the same key, so the last write wins")
                .contains(second);
    }

    private static Thread daemonThread(Runnable body) {
        Thread thread = new Thread(body);
        thread.setDaemon(true);
        return thread;
    }

    /** Clears the storage region and then fails, as a backend error would. */
    static class FailingClearCache extends TestCache {
        @Override
        protected void clearStorage() {
            super.clearStorage();
            throw new IllegalStateException("storage clear failed");
        }
    }

    /** Fails the first mapping write, as a backend error would. */
    static class FailingMappingWriteCache extends TestCache {
        @Override
        protected void writeIbanMapping(Long accountId, String iban) {
            throw new IllegalStateException("mapping write failed");
        }
    }

    /** Pauses an IBAN snapshot write after the snapshot itself is stored. */
    static class BlockingTestCache extends TestCache {
        final CountDownLatch writeStarted = new CountDownLatch(1);
        final CountDownLatch releaseWrite = new CountDownLatch(1);
        final CountDownLatch mappingWriteStarted = new CountDownLatch(1);
        final CountDownLatch releaseMappingWrite = new CountDownLatch(1);
        volatile boolean blockNextIbanWrite;
        volatile boolean blockNextMappingWrite;

        @Override
        protected void writeSnapshot(String key, AccountSnapshot snapshot) {
            super.writeSnapshot(key, snapshot);
            if (blockNextIbanWrite && key.startsWith("iban-")) {
                writeStarted.countDown();
                try {
                    releaseWrite.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        protected void writeIbanMapping(Long accountId, String iban) {
            super.writeIbanMapping(accountId, iban);
            if (blockNextMappingWrite) {
                blockNextMappingWrite = false;
                mappingWriteStarted.countDown();
                try {
                    releaseMappingWrite.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
