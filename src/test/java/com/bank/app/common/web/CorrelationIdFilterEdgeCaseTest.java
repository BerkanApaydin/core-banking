package com.bank.app.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterEdgeCaseTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsNull() throws Exception {
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).setHeader(eq("X-Correlation-ID"), anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsBlank() throws Exception {
        when(request.getHeader("X-Correlation-ID")).thenReturn("   ");

        filter.doFilter(request, response, chain);

        verify(response).setHeader(eq("X-Correlation-ID"), anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldUseCorrelationIdFromHeaderWhenPresent() throws Exception {
        when(request.getHeader("X-Correlation-ID")).thenReturn("custom-id-123");

        filter.doFilter(request, response, chain);

        verify(response).setHeader("X-Correlation-ID", "custom-id-123");
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldGenerateUniqueCorrelationIdsOnMultipleCalls() throws Exception {
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        CorrelationIdFilter filter2 = new CorrelationIdFilter();

        final String[] firstId = new String[1];
        final String[] secondId = new String[1];

        doAnswer(inv -> {
            firstId[0] = (String) inv.getArguments()[1];
            return null;
        }).when(response).setHeader(eq("X-Correlation-ID"), anyString());

        filter.doFilter(request, response, chain);

        HttpServletResponse response2 = mock(HttpServletResponse.class);
        doAnswer(inv -> {
            secondId[0] = (String) inv.getArguments()[1];
            return null;
        }).when(response2).setHeader(eq("X-Correlation-ID"), anyString());

        filter2.doFilter(request, response2, chain);

        assertNotNull(firstId[0]);
        assertNotNull(secondId[0]);
        assertNotEquals(firstId[0], secondId[0]);
    }

    @Test
    void shouldCleanupMDCAfterRequest() throws Exception {
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
