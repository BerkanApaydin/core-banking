package com.bank.app.infrastructure.adapter.in.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilterRequestDecisionsTest {

    @Test
    void requestPathShouldDecodeEncodedAdminPrefix() {
        // SEC-02: "/api/v1/%61dmin/..." must classify as admin, otherwise the
        // token-version re-check is skipped while SecurityConfig's decoded
        // "/api/v1/admin/**" matcher still grants the role.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/%61dmin/audit-logs");

        String path = FilterRequestDecisions.requestPath(request);

        assertEquals("/api/v1/admin/audit-logs", path);
        assertTrue(FilterRequestDecisions.isAdminRequest(path));
    }

    @Test
    void requestPathShouldDecodeAndStripContextPathTogether() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/bank");
        request.setRequestURI("/bank/api/v1/%61dmin/accounts");

        String path = FilterRequestDecisions.requestPath(request);

        assertEquals("/api/v1/admin/accounts", path);
        assertTrue(FilterRequestDecisions.isAdminRequest(path));
    }

    @Test
    void requestPathShouldKeepPlainPathsUnchanged() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/transfers");

        assertEquals("/api/v1/transfers", FilterRequestDecisions.requestPath(request));
        assertFalse(FilterRequestDecisions.isAdminRequest("/api/v1/transfers"));
    }

    @Test
    void requestPathShouldFallBackToServletPathWhenUriIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(null);
        request.setServletPath("/fallback");

        assertEquals("/fallback", FilterRequestDecisions.requestPath(request));
    }

    @Test
    void requestPathShouldSurviveMalformedPercentEncoding() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/%ZZ");

        assertEquals("/api/v1/%ZZ", FilterRequestDecisions.requestPath(request));
    }

    @Test
    void requestPathShouldFallBackWhenThereIsNoContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public String getContextPath() {
                return null;
            }
        };
        request.setRequestURI("/");
        request.setServletPath("/");

        assertEquals("/", FilterRequestDecisions.requestPath(request));
    }

    @Test
    void requestPathShouldFallBackWhenTheUriDoesNotStartWithTheContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/bank");
        request.setRequestURI("/");
        request.setServletPath("/");

        assertEquals("/", FilterRequestDecisions.requestPath(request));
    }

    @Test
    void requestPathShouldStripTheWholeContextPathWhenNothingFollowsIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/bank");
        request.setRequestURI("/bank");

        assertEquals("", FilterRequestDecisions.requestPath(request));
    }

    @Test
    void decodePathShouldHandleNullEmptyPlainAndLeadingPercentInputs() throws Exception {
        Method decode = FilterRequestDecisions.class.getDeclaredMethod("decodePath", String.class);
        decode.setAccessible(true);

        assertEquals("", decode.invoke(null, (String) null));
        assertEquals("", decode.invoke(null, ""));
        assertEquals("/api/v1/transfers", decode.invoke(null, "/api/v1/transfers"));
        assertEquals("admin", decode.invoke(null, "%61dmin"));
    }

    @Test
    void isAdminRequestShouldMatchExactPrefixesAndSubpathsOnly() {
        assertTrue(FilterRequestDecisions.isAdminRequest("/api/v1/admin"));
        assertTrue(FilterRequestDecisions.isAdminRequest("/api/v1/admin/audit-logs"));
        assertTrue(FilterRequestDecisions.isAdminRequest("/actuator/loggers"));
        assertTrue(FilterRequestDecisions.isAdminRequest("/actuator/loggers/ROOT"));
        assertFalse(FilterRequestDecisions.isAdminRequest("/api/v1/administrator"));
        assertFalse(FilterRequestDecisions.isAdminRequest("/actuator/loggersx"));
        assertFalse(FilterRequestDecisions.isAdminRequest("/api/v1/adminx"));
        assertFalse(FilterRequestDecisions.isAdminRequest("/api/v1/accounts"));
    }

    @Test
    void isLogoutRequestShouldRequirePostAndAnExactPath() {
        assertTrue(FilterRequestDecisions.isLogoutRequest(post("/api/v1/auth/logout"),
                "/api/v1/auth/logout"));
        assertTrue(FilterRequestDecisions.isLogoutRequest(post("/api/v1/auth/browser/logout"),
                "/api/v1/auth/browser/logout"));
        assertFalse(FilterRequestDecisions.isLogoutRequest(get("/api/v1/auth/logout"),
                "/api/v1/auth/logout"));
        assertFalse(FilterRequestDecisions.isLogoutRequest(post("/api/v1/auth/logout/x"),
                "/api/v1/auth/logout/x"));
    }

    @Test
    void isPublicLoginShouldCoverEveryPermitAllAuthPathOnPostOnly() {
        String[] paths = {
                "/api/v1/auth/login",
                "/api/v1/auth/browser/login",
                "/api/v1/auth/register",
                "/api/v1/auth/password/reset/request",
                "/api/v1/auth/password/reset/confirm"
        };
        for (String path : paths) {
            assertTrue(FilterRequestDecisions.isPublicLogin(post(path), path), path);
            assertFalse(FilterRequestDecisions.isPublicLogin(get(path), path), path);
        }
        assertFalse(FilterRequestDecisions.isPublicLogin(post("/api/v1/auth/login/extra"),
                "/api/v1/auth/login/extra"));
        assertFalse(FilterRequestDecisions.isPublicLogin(post("/api/v1/transfers"),
                "/api/v1/transfers"));
    }

    @Test
    void isRefreshRequestShouldRequirePostAndAnExactPath() {
        assertTrue(FilterRequestDecisions.isRefreshRequest(post("/api/v1/auth/refresh"),
                "/api/v1/auth/refresh"));
        assertTrue(FilterRequestDecisions.isRefreshRequest(post("/api/v1/auth/browser/refresh"),
                "/api/v1/auth/browser/refresh"));
        assertFalse(FilterRequestDecisions.isRefreshRequest(get("/api/v1/auth/refresh"),
                "/api/v1/auth/refresh"));
        assertFalse(FilterRequestDecisions.isRefreshRequest(post("/api/v1/auth/refresh/x"),
                "/api/v1/auth/refresh/x"));
    }

    @Test
    void cookieValueShouldReturnOnlyTheNamedCookie() {
        MockHttpServletRequest withoutCookies = new MockHttpServletRequest();
        assertNull(FilterRequestDecisions.cookieValue(withoutCookies, "refresh_token"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"), new Cookie("refresh_token", "token-1"));
        assertEquals("token-1", FilterRequestDecisions.cookieValue(request, "refresh_token"));
        assertNull(FilterRequestDecisions.cookieValue(request, "missing"));
    }

    @Test
    void requestPathShouldFallBackToTheRequestUriWhenPathResolutionFails() {
        // A container that cannot resolve the path must not break
        // authentication — and it must not silently drop the admin
        // classification either, or the token-version re-check is skipped.
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(anyString()))
                .thenThrow(new IllegalStateException("container failure"));
        when(request.getRequestURI()).thenReturn("/api/v1/admin/audit-logs");

        String path = FilterRequestDecisions.requestPath(request);

        assertEquals("/api/v1/admin/audit-logs", path);
        assertTrue(FilterRequestDecisions.isAdminRequest(path));
        verify(request, atLeastOnce()).getAttribute(anyString());
    }

    @Test
    void requestPathShouldBeEmptyWhenNeitherUriNorServletPathIsAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(null);
        request.setServletPath(null);

        assertEquals("", FilterRequestDecisions.requestPath(request));
    }

    @Test
    void prefixGuardsShouldNotMatchAnEmptyPath() {
        // An empty path matches no prefix: every prefix-based guard (admin
        // re-check, logout/refresh exemptions, rate-limit tiers) silently
        // stops matching instead of failing closed.
        assertFalse(FilterRequestDecisions.isAdminRequest(""));
        assertFalse(FilterRequestDecisions.isLogoutRequest(post(""), ""));
        assertFalse(FilterRequestDecisions.isRefreshRequest(post(""), ""));
    }

    private static MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        return request;
    }

    private static MockHttpServletRequest get(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return request;
    }
}
