package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.infrastructure.adapter.in.security.JwtProperties;
import io.jsonwebtoken.io.Decoders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HmacCsrfBindingAdapter")
class HmacCsrfBindingAdapterTest {

    // Zero-entropy placeholder: valid shape, never flags secret scanners.
    // The real dev default lives only in JwtTokenProvider + compose.
    private static final String SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String SECRET_OLD = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=";

    @Test
    @DisplayName("should round-trip via JwtProperties-derived key (K7/D8)")
    void shouldRoundTrip() {
        HmacCsrfBindingAdapter adapter =
                new HmacCsrfBindingAdapter(properties());

        String token = adapter.issueCsrfToken("42");

        assertThat(adapter.verifyCsrfToken(token, token, "42")).isTrue();
        assertThat(adapter.verifyCsrfToken(token, token, "777")).isFalse();
    }

    @Test
    @DisplayName("should use fresh randomness for every issued token")
    void shouldUseFreshRandomness() {
        HmacCsrfBindingAdapter adapter = new HmacCsrfBindingAdapter(properties());

        String first = adapter.issueCsrfToken("42");
        String second = adapter.issueCsrfToken("42");

        assertThat(first).isNotEqualTo(second);
        assertThat(adapter.verifyCsrfToken(first, first, "42")).isTrue();
        assertThat(adapter.verifyCsrfToken(second, second, "42")).isTrue();
    }

    @Test
    @DisplayName("should fail closed on null inputs (K7/D8)")
    void shouldFailClosed() {
        HmacCsrfBindingAdapter adapter =
                new HmacCsrfBindingAdapter(properties());
        String token = adapter.issueCsrfToken("42");

        assertThat(adapter.verifyCsrfToken(null, token, "42")).isFalse();
        assertThat(adapter.verifyCsrfToken(token, null, "42")).isFalse();
        assertThat(adapter.verifyCsrfToken(token, token, null)).isFalse();
    }

    @Test
    @DisplayName("should reject a null or empty MAC key at construction")
    void shouldRejectEmptyMacKey() {
        assertThatThrownBy(
                () -> new HmacCsrfBindingAdapter((byte[]) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("macKey must not be empty");

        assertThatThrownBy(
                () -> new HmacCsrfBindingAdapter(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("macKey must not be empty");
    }

    @Test
    @DisplayName("should fail closed on malformed or mistyped bound tokens")
    void shouldRejectMalformedBoundTokens() {
        HmacCsrfBindingAdapter adapter =
                new HmacCsrfBindingAdapter(properties());
        String valid = adapter.issueCsrfToken("42");

        assertThat(adapter.verifyCsrfToken("", "", "42")).isFalse();
        assertThat(adapter.verifyCsrfToken("short", "short", "42")).isFalse();
        assertThat(adapter.verifyCsrfToken(valid, valid, null)).isFalse();
        assertThat(adapter.verifyCsrfToken(valid, valid, " ")).isFalse();
        // Correct length but no separator at the mandated offset.
        String noSeparator = "x".repeat(43) + "y".repeat(43);
        assertThat(adapter.verifyCsrfToken(noSeparator, noSeparator, "42")).isFalse();
    }

    @Test
    @DisplayName("should reject a matching pair whose MAC was tampered with")
    void shouldRejectTamperedMacEvenWhenHeaderMatchesCookie() {
        HmacCsrfBindingAdapter adapter =
                new HmacCsrfBindingAdapter(properties());
        String valid = adapter.issueCsrfToken("42");
        // Flip the last base64url character: header == cookie still holds, so
        // only the HMAC comparison can reject the pair.
        char last = valid.charAt(valid.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        String tampered = valid.substring(0, valid.length() - 1) + replacement;

        assertThat(adapter.verifyCsrfToken(tampered, tampered, "42")).isFalse();
        assertThat(adapter.verifyCsrfToken(valid, valid, "42")).isTrue();
    }

    @Test
    @DisplayName("tokens from different secrets must not verify (key separation)")
    void shouldIsolateKeys() {
        HmacCsrfBindingAdapter first =
                new HmacCsrfBindingAdapter(properties());
        HmacCsrfBindingAdapter second =
                new HmacCsrfBindingAdapter("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=".getBytes(
                        StandardCharsets.UTF_8));

        String token = first.issueCsrfToken("42");

        assertThat(second.verifyCsrfToken(token, token, "42")).isFalse();
    }

    @Test
    @DisplayName("the production key must not be the raw JWT secret (key-purpose separation)")
    void shouldNotUseTheRawJwtSecretAsMacKey() {
        // If the MAC key were the decoded JWT secret itself, a leaked JWT
        // signing key would immediately mint valid CSRF tokens (and the
        // reverse). The derived key must be a separate secret.
        HmacCsrfBindingAdapter production = new HmacCsrfBindingAdapter(properties());
        HmacCsrfBindingAdapter rawSecretKey =
                new HmacCsrfBindingAdapter(Decoders.BASE64.decode(SECRET));

        String productionToken = production.issueCsrfToken("42");
        String rawToken = rawSecretKey.issueCsrfToken("42");

        assertThat(production.verifyCsrfToken(rawToken, rawToken, "42")).isFalse();
        assertThat(rawSecretKey.verifyCsrfToken(productionToken, productionToken, "42")).isFalse();
    }

    @Test
    @DisplayName("every instance built from the same secret derives the same MAC key")
    void shouldDeriveTheSameKeyForEveryInstanceWithTheSameSecret() {
        // The derivation must be deterministic: a restart must not invalidate
        // every browser CSRF cookie in flight (that would 403 every session).
        HmacCsrfBindingAdapter beforeRestart = new HmacCsrfBindingAdapter(properties());
        HmacCsrfBindingAdapter afterRestart = new HmacCsrfBindingAdapter(properties());

        String token = beforeRestart.issueCsrfToken("42");

        assertThat(afterRestart.verifyCsrfToken(token, token, "42")).isTrue();
    }

    @Test
    @DisplayName("a token bound to another subject must not verify under the production key")
    void shouldRejectForeignSubjectUnderTheProductionKey() {
        HmacCsrfBindingAdapter production = new HmacCsrfBindingAdapter(properties());

        String token = production.issueCsrfToken("42");

        assertThat(production.verifyCsrfToken(token, token, "43")).isFalse();
    }

    @Test
    @DisplayName("a tampered token must not verify under the production key")
    void shouldRejectTamperedTokenUnderTheProductionKey() {
        HmacCsrfBindingAdapter production = new HmacCsrfBindingAdapter(properties());

        String token = production.issueCsrfToken("42");
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThat(production.verifyCsrfToken(tampered, tampered, "42")).isFalse();
        assertThat(production.verifyCsrfToken(token, token, "42")).isTrue();
    }

    @Test
    @DisplayName("JWT rotation keeps in-flight CSRF tokens valid via retired keys")
    void shouldVerifyCsrfTokenSignedWithRetiredJwtSecret() {
        JwtProperties oldProperties = new JwtProperties(SECRET_OLD, 900000L, 604800000L, true,
                "test-issuer", "test-audience", "old-key", 30L, Map.of());
        HmacCsrfBindingAdapter oldAdapter = new HmacCsrfBindingAdapter(oldProperties);
        String oldToken = oldAdapter.issueCsrfToken("42");

        JwtProperties rotatedProperties = new JwtProperties(SECRET, 900000L, 604800000L, true,
                "test-issuer", "test-audience", "new-key", 30L,
                Map.of("old-key", SECRET_OLD));
        HmacCsrfBindingAdapter rotatedAdapter = new HmacCsrfBindingAdapter(rotatedProperties);

        assertThat(rotatedAdapter.verifyCsrfToken(oldToken, oldToken, "42")).isTrue();
        String newToken = rotatedAdapter.issueCsrfToken("42");
        assertThat(rotatedAdapter.verifyCsrfToken(newToken, newToken, "42")).isTrue();
        assertThat(oldAdapter.verifyCsrfToken(newToken, newToken, "42")).isFalse();
    }

    private static JwtProperties properties() {
        return new JwtProperties(SECRET, 900000L, 604800000L, true,
                "test-issuer", "test-audience", "test-key", 30L, Map.of());
    }
}
