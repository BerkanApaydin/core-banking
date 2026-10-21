package com.bank.app.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SE-4: oversized bodies are rejected with 413 before reaching the chain;
 * small bodies and bodyless methods pass through untouched.
 */
@ExtendWith(MockitoExtension.class)
class RequestBodySizeLimitFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    private RequestBodySizeLimitFilter filter;

    @BeforeEach
    void setUp() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("error.request_body_too_large", Locale.ENGLISH, "Request body is too large.");
        filter = new RequestBodySizeLimitFilter(
                new RequestBodyLimitProperties(1024), messages, new ObjectMapper());
    }

    @Test
    @DisplayName("should reject declared Content-Length above the cap with 413")
    void shouldRejectOversizedDeclaredBody() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn(2048L);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(response).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(body.toString()).contains("REQUEST_BODY_TOO_LARGE");
    }

    @Test
    @DisplayName("should reject unknown-length (chunked) bodies once the stream crosses the cap")
    void shouldRejectChunkedBodyWhenStreamCrossesCap() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        when(request.getInputStream()).thenReturn(new FixedServletInputStream(new byte[2048]));
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        doAnswer(invocation -> {
            HttpServletRequest wrapped = invocation.getArgument(0);
            wrapped.getInputStream().readAllBytes();
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(body.toString()).contains("REQUEST_BODY_TOO_LARGE");
    }

    private static final class FixedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        private FixedServletInputStream(byte[] content) {
            this.delegate = new ByteArrayInputStream(content);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return delegate.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Synchronous test stream");
        }
    }

    @Test
    @DisplayName("should pass a small body through")
    void shouldPassSmallBody() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn(100L);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verify(response, never()).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
    }

    @Test
    @DisplayName("should ignore bodyless methods")
    void shouldIgnoreBodylessMethods() throws Exception {
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
    }
}
