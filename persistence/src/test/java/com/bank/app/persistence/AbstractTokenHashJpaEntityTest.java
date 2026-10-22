package com.bank.app.persistence;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void shouldPinTheTokenHashColumnShape() throws Exception {
        // token_hash is varchar(64) NOT NULL: the column is the real contract,
        // the setter is not.
        Column column = AbstractTokenHashJpaEntity.class
                .getDeclaredField("tokenHash")
                .getAnnotation(Column.class);

        assertNotNull(column);
        assertEquals("token_hash", column.name());
        assertFalse(column.nullable());
        assertEquals(64, column.length());
    }

    @Test
    void shouldRoundTripAFullLengthSha256HexDigest() {
        String digest = sha256HexOf("a-raw-session-token");

        TestTokenEntity entity = new TestTokenEntity();
        entity.setTokenHash(digest);

        assertEquals(64, digest.length());
        assertEquals(digest, entity.getTokenHash());
        assertEquals(digest, entity.getId());
    }

    @Test
    void shouldAcceptAnOverlongTokenHashWithoutComplaining() {
        // The setter has no length guard: an over-long digest is only rejected
        // by Postgres at flush time ("value too long for type character
        // varying(64)"), long after the failure is far from its cause.
        TestTokenEntity entity = new TestTokenEntity();

        entity.setTokenHash("a".repeat(65));

        assertEquals(65, entity.getTokenHash().length());
    }

    @Test
    void shouldAcceptAShortTokenHashWithoutComplaining() {
        // A truncated digest is accepted silently and becomes the primary
        // key of the token row — a corrupted hash then matches no token.
        TestTokenEntity entity = new TestTokenEntity();

        entity.setTokenHash("a".repeat(63));

        assertEquals(63, entity.getTokenHash().length());
    }

    private static String sha256HexOf(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
