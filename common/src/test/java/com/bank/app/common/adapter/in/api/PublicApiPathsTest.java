package com.bank.app.common.adapter.in.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The path literals below are matched in three independent places (security
 * whitelist, rate-limit filter, JWT login bypass). A rename applied in only
 * one of them silently breaks login or opens a bypass, so the literals are
 * pinned here and the tiering invariants are asserted together.
 */
@DisplayName("PublicApiPaths")
class PublicApiPathsTest {

    private static final List<String> AUTH_PATHS = List.of(
            PublicApiPaths.LOGIN,
            PublicApiPaths.BROWSER_LOGIN,
            PublicApiPaths.REGISTER,
            PublicApiPaths.REFRESH,
            PublicApiPaths.BROWSER_REFRESH,
            PublicApiPaths.LOGOUT,
            PublicApiPaths.BROWSER_LOGOUT,
            PublicApiPaths.BROWSER_SESSION,
            PublicApiPaths.PASSWORD_RESET_REQUEST,
            PublicApiPaths.PASSWORD_RESET_CONFIRM,
            PublicApiPaths.MFA);

    private static final List<String> RESOURCE_PATHS = List.of(
            PublicApiPaths.ACCOUNTS,
            PublicApiPaths.TRANSFERS,
            PublicApiPaths.ADMIN);

    private static final List<String> ALL_PATHS = List.of(
            PublicApiPaths.AUTH_PREFIX,
            PublicApiPaths.LOGIN,
            PublicApiPaths.BROWSER_LOGIN,
            PublicApiPaths.REGISTER,
            PublicApiPaths.REFRESH,
            PublicApiPaths.BROWSER_REFRESH,
            PublicApiPaths.LOGOUT,
            PublicApiPaths.BROWSER_LOGOUT,
            PublicApiPaths.BROWSER_SESSION,
            PublicApiPaths.PASSWORD_RESET_REQUEST,
            PublicApiPaths.PASSWORD_RESET_CONFIRM,
            PublicApiPaths.MFA,
            PublicApiPaths.ACCOUNTS,
            PublicApiPaths.TRANSFERS,
            PublicApiPaths.ADMIN);

    @Test
    @DisplayName("should expose the exact path literals the three call sites match on")
    void shouldExposeExactPathLiterals() {
        assertThat(PublicApiPaths.AUTH_PREFIX).isEqualTo("/api/v1/auth");
        assertThat(PublicApiPaths.LOGIN).isEqualTo("/api/v1/auth/login");
        assertThat(PublicApiPaths.BROWSER_LOGIN).isEqualTo("/api/v1/auth/browser/login");
        assertThat(PublicApiPaths.REGISTER).isEqualTo("/api/v1/auth/register");
        assertThat(PublicApiPaths.REFRESH).isEqualTo("/api/v1/auth/refresh");
        assertThat(PublicApiPaths.BROWSER_REFRESH).isEqualTo("/api/v1/auth/browser/refresh");
        assertThat(PublicApiPaths.LOGOUT).isEqualTo("/api/v1/auth/logout");
        assertThat(PublicApiPaths.BROWSER_LOGOUT).isEqualTo("/api/v1/auth/browser/logout");
        assertThat(PublicApiPaths.BROWSER_SESSION).isEqualTo("/api/v1/auth/browser/session");
        assertThat(PublicApiPaths.PASSWORD_RESET_REQUEST)
                .isEqualTo("/api/v1/auth/password/reset/request");
        assertThat(PublicApiPaths.PASSWORD_RESET_CONFIRM)
                .isEqualTo("/api/v1/auth/password/reset/confirm");
        assertThat(PublicApiPaths.MFA).isEqualTo("/api/v1/auth/mfa");
        assertThat(PublicApiPaths.ACCOUNTS).isEqualTo("/api/v1/accounts");
        assertThat(PublicApiPaths.TRANSFERS).isEqualTo("/api/v1/transfers");
        assertThat(PublicApiPaths.ADMIN).isEqualTo("/api/v1/admin");
    }

    @Test
    @DisplayName("should start every path with a slash")
    void shouldStartEveryPathWithSlash() {
        assertThat(ALL_PATHS).allSatisfy(path -> assertThat(path).startsWith("/"));
    }

    @Test
    @DisplayName("should keep every path on the versioned API prefix")
    void shouldKeepEveryPathOnTheVersionedApiPrefix() {
        assertThat(ALL_PATHS).allSatisfy(path -> assertThat(path).startsWith("/api/v1/"));
    }

    @Test
    @DisplayName("should keep every auth path under the auth prefix")
    void shouldKeepEveryAuthPathUnderAuthPrefix() {
        assertThat(AUTH_PATHS).allSatisfy(path ->
                assertThat(path).startsWith(PublicApiPaths.AUTH_PREFIX));
        // Equal to the prefix would make the prefix itself a public endpoint.
        assertThat(AUTH_PATHS).allSatisfy(path ->
                assertThat(path.length()).isGreaterThan(PublicApiPaths.AUTH_PREFIX.length()));
    }

    @Test
    @DisplayName("should keep MFA, password-reset, refresh and logout on the auth tier")
    void shouldKeepBruteForceSensitivePathsOnTheAuthTier() {
        // MFA is a code-guessing target, password reset is unauthenticated by
        // necessity, refresh/logout authenticate with the credential they
        // present: all of them must inherit the tight auth-tier rate budget.
        List<String> bruteForceSensitive = List.of(
                PublicApiPaths.MFA,
                PublicApiPaths.PASSWORD_RESET_REQUEST,
                PublicApiPaths.PASSWORD_RESET_CONFIRM,
                PublicApiPaths.REFRESH,
                PublicApiPaths.BROWSER_REFRESH,
                PublicApiPaths.LOGOUT,
                PublicApiPaths.BROWSER_LOGOUT);

        assertThat(bruteForceSensitive).allSatisfy(path ->
                assertThat(path).startsWith(PublicApiPaths.AUTH_PREFIX));
    }

    @Test
    @DisplayName("should keep dashboard paths out of the tight auth tier")
    void shouldKeepResourcePathsOutOfTheAuthTier() {
        // The rate limiter picks the tier with startsWith(AUTH_PREFIX); a
        // resource path under the auth prefix would be throttled with the
        // brute-force budget and lock out legitimate dashboard traffic.
        assertThat(RESOURCE_PATHS).noneSatisfy(path ->
                assertThat(path).startsWith(PublicApiPaths.AUTH_PREFIX));
    }

    @Test
    @DisplayName("should not end any path with a trailing slash")
    void shouldNotEndAnyPathWithTrailingSlash() {
        assertThat(ALL_PATHS).noneSatisfy(path -> assertThat(path).endsWith("/"));
    }

    @Test
    @DisplayName("should not reuse one literal for two endpoints")
    void shouldNotDuplicateAnyPath() {
        assertThat(ALL_PATHS).doesNotHaveDuplicates();
    }
}
