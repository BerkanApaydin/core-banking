package com.bank.app.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class RequestPathResolverTest {

    @Test
    void shouldReturnPlainPathUnchanged() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/transfers/123");

        assertEquals("/api/v1/transfers/123", RequestPathResolver.resolve(request));
    }

    @Test
    void shouldDecodePercentEncodedPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/%61ccounts");

        assertEquals("/api/v1/accounts", RequestPathResolver.resolve(request));
    }

    @Test
    void shouldStripContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/bank");
        request.setRequestURI("/bank/api/v1/auth/login");

        assertEquals("/api/v1/auth/login", RequestPathResolver.resolve(request));
    }

    @Test
    void shouldDecodeAndStripTogether() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/bank");
        request.setRequestURI("/bank/api/v1/%74ransfers");

        assertEquals("/api/v1/transfers", RequestPathResolver.resolve(request));
    }

    @Test
    void shouldReturnSlashForEmptyPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals("/", RequestPathResolver.resolve(request));
    }

    @Test
    void matchesPrefixShouldAcceptExactAndChildPaths() {
        assertTrue(RequestPathResolver.matchesPrefix("/api/v1/accounts", "/api/v1/accounts"));
        assertTrue(RequestPathResolver.matchesPrefix("/api/v1/accounts/123", "/api/v1/accounts"));
        assertTrue(RequestPathResolver.matchesPrefix("/api/v1/transfers/report", "/api/v1/transfers"));
    }

    @Test
    void matchesPrefixShouldRejectSiblingPaths() {
        assertFalse(RequestPathResolver.matchesPrefix("/api/v1/accountsextra", "/api/v1/accounts"));
        assertFalse(RequestPathResolver.matchesPrefix("/api/v1/health", "/api/v1/accounts"));
        assertFalse(RequestPathResolver.matchesPrefix("/other/v1/accounts", "/api/v1/accounts"));
    }

    @Test
    void matchesPrefixShouldMatchThePrefixWithATrailingSlash() {
        // "/api/v1/accounts/" is the prefix plus an empty segment: it belongs
        // to the guarded subtree, not to a sibling endpoint.
        assertTrue(RequestPathResolver.matchesPrefix("/api/v1/accounts/", "/api/v1/accounts"));
    }

    @Test
    void matchesPrefixShouldNotMatchASiblingSegmentWithTheSameCharacters() {
        // The admin guard must not leak onto an unrelated endpoint that only
        // shares the prefix characters ("/api/v1/administrator").
        assertFalse(RequestPathResolver.matchesPrefix("/api/v1/administrator", "/api/v1/admin"));
        assertFalse(RequestPathResolver.matchesPrefix("/api/v1/adminx", "/api/v1/admin"));
        assertFalse(RequestPathResolver.matchesPrefix("/api/v1/admin.accounts", "/api/v1/admin"));
    }
}
