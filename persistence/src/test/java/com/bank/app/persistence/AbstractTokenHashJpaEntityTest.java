package com.bank.app.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The assigned-id/Persistable contract of token rows.
 *
 * <p>Spring Data decides between {@code persist} and {@code merge} from
 * {@link org.springframework.data.domain.Persistable#isNew()}. Token hashes
 * are assigned before saving, so a wrong flag would make every issuance issue
 * a SELECT-before-INSERT (or worse, a merge) instead of a plain insert. The
 * flag must be true on construction and become false on load/persist.
 */
class AbstractTokenHashJpaEntityTest {

    static class TestTokenEntity extends AbstractTokenHashJpaEntity {
    }

    @Test
    void shouldBeNewBeforeItIsPersistedOrLoaded() {
        TestTokenEntity entity = new TestTokenEntity();

        assertTrue(entity.isNew());
    }

    @Test
    void shouldExposeTheTokenHashAsItsAssignedId() {
        TestTokenEntity entity = new TestTokenEntity();
        entity.setTokenHash("a".repeat(64));

        assertEquals("a".repeat(64), entity.getId());
        assertEquals(entity.getTokenHash(), entity.getId());
    }

    @Test
    void shouldReturnNullIdWhenNoHashIsAssignedYet() {
        assertNull(new TestTokenEntity().getId());
    }

    @Test
    void shouldNotBeNewAfterPersistOrLoadCallback() {
        TestTokenEntity entity = new TestTokenEntity();

        entity.markNotNew();

        assertFalse(entity.isNew());
    }

    @Test
    void shouldRemainNotNewWhenTheCallbackRunsAgain() {
        TestTokenEntity entity = new TestTokenEntity();
        entity.markNotNew();

        entity.markNotNew();

        assertFalse(entity.isNew());
    }

    @Test
    void shouldKeepIsNewIndependentOfHashMutation() {
        TestTokenEntity entity = new TestTokenEntity();
        entity.markNotNew();

        entity.setTokenHash("b".repeat(64));

        assertFalse(entity.isNew());
        assertEquals("b".repeat(64), entity.getId());
    }

    @Test
    void shouldRemainNewWhenTheHashIsAssignedBeforePersist() {
        TestTokenEntity entity = new TestTokenEntity();

        entity.setTokenHash("c".repeat(64));

        assertTrue(entity.isNew());
        assertEquals("c".repeat(64), entity.getId());
    }

    @Test
    void shouldExposeNullAndBlankHashesWithoutNormalizingThem() {
        TestTokenEntity entity = new TestTokenEntity();

        entity.setTokenHash(null);
        assertNull(entity.getId());
        assertTrue(entity.isNew());

        entity.setTokenHash("   ");
        assertEquals("   ", entity.getId());
        assertTrue(entity.isNew());
    }
}
