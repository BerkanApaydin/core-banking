package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.accountapi.AccountSnapshotCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Key-namespace contract for cached snapshots.
 *
 * <p>Guards against stale-read and mass-evict regressions: every key must live
 * under the shared prefix, IBAN keys must be normalized (whitespace and case
 * insensitive) so the same account never mints two entries.
 */
@DisplayName("SnapshotKeys")
class SnapshotKeysTest {

    @Test
    @DisplayName("should keep every key under the shared namespace prefix")
    void shouldShareNamespacePrefix() {
        // Act
        String idKey = SnapshotKeys.idKey(42L);
        String ibanKey = SnapshotKeys.ibanKey("TR770006200000000000000111");
        String mapKey = SnapshotKeys.mapKey(42L);
        String index = SnapshotKeys.ibansByIdIndex(42L);
        String reverseIndex = SnapshotKeys.idByIbanIndex("abc");
        String scan = SnapshotKeys.scanPattern();

        // Assert
        assertThat(idKey).startsWith(SnapshotKeys.KEY_PREFIX);
        assertThat(ibanKey).startsWith(SnapshotKeys.ibanKeyPrefix());
        assertThat(mapKey).startsWith(SnapshotKeys.KEY_PREFIX);
        assertThat(index).startsWith(SnapshotKeys.KEY_PREFIX);
        assertThat(reverseIndex).startsWith(SnapshotKeys.idByIbanPrefix());
        assertThat(scan).isEqualTo(SnapshotKeys.KEY_PREFIX + "*");
    }

    @Test
    @DisplayName("should normalize IBAN keys across case and whitespace")
    void shouldNormalizeIbanKeys() {
        // Arrange — one account written three ways.
        String canonical = "TR770006200000000000000111";
        String lower = canonical.toLowerCase(Locale.ROOT);
        String spaced = canonical.substring(0, 4) + " "
                + canonical.substring(4, 8) + " "
                + canonical.substring(8);

        // Act
        String fromLower = SnapshotKeys.ibanKey(lower);
        String fromSpaced = SnapshotKeys.ibanKey(spaced);
        String fromCanonical = SnapshotKeys.ibanKey(canonical);

        // Assert — one account, one cache entry.
        assertThat(fromLower).isEqualTo(fromCanonical);
        assertThat(fromSpaced).isEqualTo(fromCanonical);
    }

    @Test
    @DisplayName("should reject null IBAN values fast")
    void shouldRejectNullIban() {
        assertThatThrownBy(() -> SnapshotKeys.ibanKey(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should produce distinct keys per account")
    void shouldDistinguishAccounts() {
        assertThat(SnapshotKeys.idKey(1L)).isNotEqualTo(SnapshotKeys.idKey(2L));
        assertThat(SnapshotKeys.mapKey(1L)).isNotEqualTo(SnapshotKeys.mapKey(2L));
        assertThat(SnapshotKeys.ibansByIdIndex(1L))
                .isNotEqualTo(SnapshotKeys.ibansByIdIndex(2L));
    }

    @Test
    @DisplayName("should keep raw and derived IBAN key paths consistent")
    void shouldAlignRawAndDerivedPaths() {
        // Arrange — the normalized key form used by the reverse index.
        String canonical = "TR770006200000000000000111";
        String normalized = AccountSnapshotCache
                .ibanKey(canonical.toLowerCase(Locale.ROOT));

        // Act
        String viaRaw = SnapshotKeys.ibanKeyRaw(normalized);
        String viaValue = SnapshotKeys.ibanKey(canonical);

        // Assert
        assertThat(viaRaw).isEqualTo(viaValue);
    }
}
