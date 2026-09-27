package com.bank.app.accountapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        final Map<String, AccountSnapshot> snapshots = new HashMap<>();
        final Map<String, Map<Long, String>> batches = new HashMap<>();

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
        protected Collection<String> ibanSnapshotKeysForAccount(Long accountId) {
            return snapshots.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("iban-"))
                    .filter(entry -> accountId.equals(entry.getValue().id()))
                    .map(Map.Entry::getKey)
                    .toList();
        }

        @Override
        protected Optional<Map<Long, String>> readBatch(String key) {
            return Optional.ofNullable(batches.get(key));
        }

        @Override
        protected void writeBatch(String key, Map<Long, String> batch) {
            batches.put(key, batch);
        }

        @Override
        protected void clearStorage() {
            snapshots.clear();
            batches.clear();
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
    void shouldIgnoreNullOrEmptyBatchPuts() {
        cache.putIbans(null, Map.of(1L, "TR1"));
        cache.putIbans(Set.of(1L), null);
        cache.putIbans(Set.of(1L), Map.of());

        assertThat(cache.batches).isEmpty();
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
    void shouldEvictSingleIbanEntryAndKeepOthers() {
        cache.putByIban("TR721111111111111111111111", SNAPSHOT_1);
        cache.putByIban("TR972222222222222222222222", SNAPSHOT_2);

        cache.evictByIban("TR721111111111111111111111");

        assertThat(cache.getByIban("TR721111111111111111111111")).isEmpty();
        assertThat(cache.getByIban("TR972222222222222222222222")).contains(SNAPSHOT_2);
    }

    @Test
    void shouldIgnoreNullEvictions() {
        cache.putById(1L, SNAPSHOT_1);

        cache.evictById(null);
        cache.evictByIban(null);

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

    @Test
    void shouldLeaveBatchEntriesOnGranularEvictByDesign() {
        cache.putIbans(Set.of(1L, 2L), Map.of(1L, "TR1", 2L, "TR2"));

        cache.evictIbansBatch();

        assertThat(cache.getIbans(Set.of(1L, 2L))).contains(Map.of(1L, "TR1", 2L, "TR2"));
    }
}
