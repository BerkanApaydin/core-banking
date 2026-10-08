package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.accountapi.AccountSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated contract for the snapshot wire format
 * ({@code id|userId|currency|status}).
 *
 * <p>Corrupt entries must decode to {@code null} so callers fall back to the
 * DB instead of failing the request or serving a stale snapshot.
 */
@DisplayName("SnapshotCodec")
class SnapshotCodecTest {

    private static final AccountSnapshot SNAPSHOT =
            new AccountSnapshot(42L, 7L, "TRY", "ACTIVE");

    @Test
    @DisplayName("should round-trip a well-formed snapshot")
    void shouldRoundTrip() {
        // Arrange
        String encoded = SnapshotCodec.encode(SNAPSHOT);

        // Act
        AccountSnapshot decoded = SnapshotCodec.decode(encoded);

        // Assert
        assertThat(decoded).isEqualTo(SNAPSHOT);
        assertThat(encoded).isEqualTo("42|7|TRY|ACTIVE");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("should decode null or empty to null (DB fallback)")
    void shouldDecodeNullOrEmptyToNull(String raw) {
        assertThat(SnapshotCodec.decode(raw)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1|2|TRY",
            "1|2|TRY|ACTIVE|EXTRA",
            "|2|TRY|ACTIVE",
            "x|2|TRY|ACTIVE",
            "1|y|TRY|ACTIVE",
            "99999999999999999999999999|2|TRY|ACTIVE",
            " 42 | 7 | TRY | ACTIVE "
    })
    @DisplayName("should decode malformed payloads to null")
    void shouldDecodeMalformedToNull(String raw) {
        assertThat(SnapshotCodec.decode(raw)).isNull();
    }

    @Test
    @DisplayName("should preserve zero ids and distinct statuses")
    void shouldPreserveZeroIdsAndStatuses() {
        // Arrange
        AccountSnapshot suspended = new AccountSnapshot(0L, 0L, "USD", "SUSPENDED");

        // Act
        AccountSnapshot decoded = SnapshotCodec.decode(SnapshotCodec.encode(suspended));

        // Assert
        assertThat(decoded).isEqualTo(suspended);
    }
}
