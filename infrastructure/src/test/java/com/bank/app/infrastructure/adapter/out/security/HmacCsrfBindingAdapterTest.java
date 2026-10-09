package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.infrastructure.adapter.in.security.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HmacCsrfBindingAdapter")
class HmacCsrfBindingAdapterTest {

    private static final String SECRET = "i83oGVJffFn/qzcqrahuJ6oxZyKp6bvxmDukRE/X3+s=";

    @Test
    @DisplayName("should round-trip via JwtProperties-derived key (K7/D8)")
    void shouldRoundTrip() {
        HmacCsrfBindingAdapter adapter =
                new HmacCsrfBindingAdapter(new JwtProperties(SECRET, 900000L, 604800000L, true));

        String token = adapter.issueCsrfToken("42");

        assertThat(adapter.verifyCsrfToken(token, token, "42")).isTrue();
        assertThat(adapter.verifyCsrfToken(token, token, "777")).isFalse();
    }

    @Test
    @DisplayName("should fail closed on null inputs (K7/D8)")
    void shouldFailClosed() {
        HmacCsrfBindingAdapter adapter =
                new HmacCsrfBindingAdapter(new JwtProperties(SECRET, 900000L, 604800000L, true));
        String token = adapter.issueCsrfToken("42");

        assertThat(adapter.verifyCsrfToken(null, token, "42")).isFalse();
        assertThat(adapter.verifyCsrfToken(token, null, "42")).isFalse();
        assertThat(adapter.verifyCsrfToken(token, token, null)).isFalse();
    }

    @Test
    @DisplayName("tokens from different secrets must not verify (key separation)")
    void shouldIsolateKeys() {
        HmacCsrfBindingAdapter first =
                new HmacCsrfBindingAdapter(new JwtProperties(SECRET, 900000L, 604800000L, true));
        HmacCsrfBindingAdapter second =
                new HmacCsrfBindingAdapter("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=".getBytes(
                        StandardCharsets.UTF_8));

        String token = first.issueCsrfToken("42");

        assertThat(second.verifyCsrfToken(token, token, "42")).isFalse();
    }
}
