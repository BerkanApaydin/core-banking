package com.bank.app.accountapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
        writer.join(5_000);
        evictor.join(5_000);

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
        writer.join(5_000);
        evictor.join(5_000);

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
            assertThat(evictionDone.await(5, TimeUnit.SECONDS))
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
        writer.join(5_000);
        resetter.join(5_000);
    }

    /**
     * The account locks must be free again after a full reset and after a
     * granular eviction — otherwise the next mutation would block forever.
     */
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

        assertThat(done.await(5, TimeUnit.SECONDS))
                .as("the account lock must be released after eviction")
                .isTrue();
        worker.join(5_000);
    }

    private static Thread daemonThread(Runnable body) {
        Thread thread = new Thread(body);
        thread.setDaemon(true);
        return thread;
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
