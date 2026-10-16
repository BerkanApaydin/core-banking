package com.bank.app.infrastructure.adapter.in.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

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
}
