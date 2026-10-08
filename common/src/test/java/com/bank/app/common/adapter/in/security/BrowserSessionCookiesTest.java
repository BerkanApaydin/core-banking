package com.bank.app.common.adapter.in.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BrowserSessionCookies")
class BrowserSessionCookiesTest {

    private static final byte[] MAC_KEY = "test-only-csrf-mac-key-32bytes!!".getBytes(StandardCharsets.UTF_8);

    private static byte[] random32() {
        byte[] rand = new byte[32];
        new SecureRandom().nextBytes(rand);
        return rand;
    }

    /**
     * Replaces the character at {@code index} with a different base64url
     * character, so the result is guaranteed to differ from the input.
     */
    private static String replaceCharAt(String value, int index) {
        char original = value.charAt(index);
        char replacement = original == 'A' ? 'B' : 'A';
        return value.substring(0, index) + replacement + value.substring(index + 1);
    }

    @Nested
    @DisplayName("bound tokens (K7/D8)")
    class BoundTokens {

        @Test
        @DisplayName("should round-trip a bound token for the same subject")
        void shouldRoundTrip() {
            String token = BrowserSessionCookies.boundToken(random32(), MAC_KEY, "42");

            assertThat(token).hasSize(BrowserSessionCookies.BOUND_TOKEN_LENGTH);
            assertThat(BrowserSessionCookies.validBoundPair(token, token, "42", MAC_KEY)).isTrue();
        }

        @Test
        @DisplayName("should reject a token transplanted from another subject")
        void shouldRejectTransplant() {
            String foreign = BrowserSessionCookies.boundToken(random32(), MAC_KEY, "777");

            assertThat(BrowserSessionCookies.validBoundPair(foreign, foreign, "42", MAC_KEY)).isFalse();
        }

        @Test
        @DisplayName("should reject tampered random and MAC parts")
        void shouldRejectTampering() {
            String token = BrowserSessionCookies.boundToken(random32(), MAC_KEY, "42");
            // Index 0 is the first char of the random part, index 44 the first
            // char of the MAC part (43 rand + '.' + 43 mac).
            String tamperedRand = replaceCharAt(token, 0);
            String tamperedMac = replaceCharAt(token, 44);

            // Without these guards the "tampered" tokens could be byte-identical
            // to the original (the base64url alphabet contains 'A', so a fixed
            // replacement character hits ~1/64 of runs) and the test would fail
            // for the wrong reason about 1.6% of the time.
            assertThat(tamperedRand).as("tampered random part must actually differ").isNotEqualTo(token);
            assertThat(tamperedMac).as("tampered MAC part must actually differ").isNotEqualTo(token);

            assertThat(BrowserSessionCookies.validBoundPair(tamperedRand, tamperedRand, "42", MAC_KEY)).isFalse();
            assertThat(BrowserSessionCookies.validBoundPair(tamperedMac, tamperedMac, "42", MAC_KEY)).isFalse();
        }

        @Test
        @DisplayName("should reject mismatched header and cookie")
        void shouldRejectMismatch() {
            String token = BrowserSessionCookies.boundToken(random32(), MAC_KEY, "42");
            String other = BrowserSessionCookies.boundToken(random32(), MAC_KEY, "42");

            assertThat(BrowserSessionCookies.validBoundPair(token, other, "42", MAC_KEY)).isFalse();
        }

        @Test
        @DisplayName("should reject a forged equal pair without a valid MAC")
        void shouldRejectForgedEqualPair() {
            String forged = "c".repeat(87);

            assertThat(BrowserSessionCookies.validBoundPair(forged, forged, "42", MAC_KEY)).isFalse();
        }

        @Test
        @DisplayName("should fail closed on null or blank inputs")
        void shouldFailClosed() {
            String token = BrowserSessionCookies.boundToken(random32(), MAC_KEY, "42");

            assertThat(BrowserSessionCookies.validBoundPair(null, token, "42", MAC_KEY)).isFalse();
            assertThat(BrowserSessionCookies.validBoundPair(token, null, "42", MAC_KEY)).isFalse();
            assertThat(BrowserSessionCookies.validBoundPair(token, token, null, MAC_KEY)).isFalse();
            assertThat(BrowserSessionCookies.validBoundPair(token, token, "  ", MAC_KEY)).isFalse();
            assertThat(BrowserSessionCookies.validBoundPair(token, token, "42", null)).isFalse();
            assertThat(BrowserSessionCookies.validBoundPair(token, token, "42", new byte[0])).isFalse();
        }

        @Test
        @DisplayName("should reject tokens minted under a different key")
        void shouldRejectDifferentKey() {
            String token = BrowserSessionCookies.boundToken(random32(), MAC_KEY, "42");
            byte[] otherKey = "another-test-only-mac-key-32byte!".getBytes(StandardCharsets.UTF_8);

            assertThat(BrowserSessionCookies.validBoundPair(token, token, "42", otherKey)).isFalse();
        }

        @Test
        @DisplayName("should reject invalid issuance inputs")
        void shouldRejectInvalidIssuance() {
            assertThatThrownBy(() -> BrowserSessionCookies.boundToken(new byte[16], MAC_KEY, "42"))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> BrowserSessionCookies.boundToken(random32(), new byte[0], "42"))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> BrowserSessionCookies.boundToken(random32(), MAC_KEY, " "))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject bound pairs with wrong length")
        void shouldRejectWrongLengthBoundPair() {
            String token = BrowserSessionCookies.boundToken(random32(), MAC_KEY, "42");
            String shortToken = token.substring(0, 86);
            String longToken = token + "A";

            assertThat(BrowserSessionCookies.validBoundPair(shortToken, shortToken, "42", MAC_KEY)).isFalse();
            assertThat(BrowserSessionCookies.validBoundPair(longToken, longToken, "42", MAC_KEY)).isFalse();
            assertThat(BrowserSessionCookies.validBoundPair(token, shortToken, "42", MAC_KEY)).isFalse();
        }

        @Test
        @DisplayName("should reject bound pair whose dot is misplaced")
        void shouldRejectMisplacedDot() {
            // 87 chars with equal header/cookie but no dot at index 43:
            // the length guard passes, the dot guard must still fail closed.
            String noDot = "d".repeat(87);
            assertThat(BrowserSessionCookies.validBoundPair(noDot, noDot, "42", MAC_KEY)).isFalse();
        }
    }

    @Nested
    @DisplayName("cookie names")
    class CookieNames {

        @Test
        @DisplayName("should use __Host- prefix when secure")
        void shouldUseHostPrefixWhenSecure() {
            BrowserSessionCookies cookies = new BrowserSessionCookies(true);
            assertThat(cookies.sessionCookieName()).isEqualTo("__Host-BANK_SESSION");
            assertThat(cookies.refreshCookieName()).isEqualTo("__Host-BANK_REFRESH");
            assertThat(cookies.csrfCookieName()).isEqualTo("__Host-BANK_CSRF");
        }

        @Test
        @DisplayName("should use plain names when not secure")
        void shouldUsePlainNamesWhenNotSecure() {
            BrowserSessionCookies cookies = new BrowserSessionCookies(false);
            assertThat(cookies.sessionCookieName()).isEqualTo("BANK_SESSION");
            assertThat(cookies.refreshCookieName()).isEqualTo("BANK_REFRESH");
            assertThat(cookies.csrfCookieName()).isEqualTo("BANK_CSRF");
        }
    }

    @Nested
    @DisplayName("CSRF requirement")
    class RequiresCsrf {

        @Test
        @DisplayName("should not require CSRF for safe methods")
        void shouldNotRequireCsrfForSafeMethods() {
            assertThat(BrowserSessionCookies.requiresCsrf("GET")).isFalse();
            assertThat(BrowserSessionCookies.requiresCsrf("HEAD")).isFalse();
            assertThat(BrowserSessionCookies.requiresCsrf("OPTIONS")).isFalse();
            assertThat(BrowserSessionCookies.requiresCsrf("TRACE")).isFalse();
        }

        @Test
        @DisplayName("should require CSRF for mutating methods")
        void shouldRequireCsrfForMutatingMethods() {
            assertThat(BrowserSessionCookies.requiresCsrf("POST")).isTrue();
            assertThat(BrowserSessionCookies.requiresCsrf("PUT")).isTrue();
            assertThat(BrowserSessionCookies.requiresCsrf("DELETE")).isTrue();
            assertThat(BrowserSessionCookies.requiresCsrf("PATCH")).isTrue();
        }
    }

    @Nested
    @DisplayName("double-submit CSRF pairs")
    class CsrfPairs {

        @Test
        @DisplayName("should accept equal 43-char pairs")
        void shouldAcceptEqualPairs() {
            String token = "A".repeat(43);
            assertThat(BrowserSessionCookies.validCsrfPair(token, token)).isTrue();
        }

        @Test
        @DisplayName("should reject null or wrong-length inputs")
        void shouldRejectNullOrWrongLength() {
            String token = "A".repeat(43);
            assertThat(BrowserSessionCookies.validCsrfPair(null, token)).isFalse();
            assertThat(BrowserSessionCookies.validCsrfPair(token, null)).isFalse();
            assertThat(BrowserSessionCookies.validCsrfPair("A".repeat(42), "A".repeat(42))).isFalse();
            assertThat(BrowserSessionCookies.validCsrfPair("A".repeat(44), "A".repeat(44))).isFalse();
            assertThat(BrowserSessionCookies.validCsrfPair("", "")).isFalse();
        }

        @Test
        @DisplayName("should reject mismatched pairs")
        void shouldRejectMismatchedPairs() {
            String header = "A".repeat(43);
            String cookie = "B".repeat(43);
            assertThat(BrowserSessionCookies.validCsrfPair(header, cookie)).isFalse();
        }

        @Test
        @DisplayName("should reject tampered single-char pairs in constant time")
        void shouldRejectSingleCharDifference() {
            String header = "A".repeat(43);
            String cookie = "A".repeat(42) + "B";
            assertThat(BrowserSessionCookies.validCsrfPair(header, cookie)).isFalse();
        }
    }
}
