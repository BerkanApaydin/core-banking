package com.bank.app.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock private FilterChain chain;

    @Test
    void shouldGenerateCorrelationIdWhenMissing() throws IOException, ServletException {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        String correlationId = response.getHeader("X-Correlation-ID");
        assertNotNull(correlationId);
        assertFalse(correlationId.isBlank());
    }

    @Test
    void shouldPropagateCorrelationIdWhenPresent() throws IOException, ServletException {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "custom-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        String correlationId = response.getHeader("X-Correlation-ID");
        assertEquals("custom-id-123", correlationId);
    }

    @Test
    void shouldGenerateCorrelationIdWhenBlank() throws IOException, ServletException {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        String correlationId = response.getHeader("X-Correlation-ID");
        assertNotNull(correlationId);
        assertFalse(correlationId.isBlank());
    }
}
