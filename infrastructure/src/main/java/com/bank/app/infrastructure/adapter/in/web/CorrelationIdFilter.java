package com.bank.app.infrastructure.adapter.in.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class CorrelationIdFilter implements Filter {
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String MDC_CORRELATION_KEY = "correlationId";
    private static final String MDC_TRACE_KEY = "traceId";
    private static final String MDC_SPAN_KEY = "spanId";

    // Inbound IDs are attacker-controlled: allow only a tight alphabet and cap
    // the length so CRLF (log forging / header injection) and oversized values
    // can never reach the MDC (every log line) or the reflected headers.
    // Anything else falls back to a generated UUID.
    private static final int MAX_ID_LENGTH = 64;
    private static final java.util.regex.Pattern SAFE_ID = java.util.regex.Pattern.compile("[A-Za-z0-9_-]+");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String correlationId = sanitize(httpRequest.getHeader(CORRELATION_ID_HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        // Distributed-tracing headers are optional; without an OTel SDK the correlation
        // ID doubles as trace ID so prod JSON logs (logback-spring.xml) never emit blanks.
        String traceId = sanitize(httpRequest.getHeader(TRACE_ID_HEADER));
        if (traceId == null) {
            traceId = correlationId;
        }
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        MDC.put(MDC_CORRELATION_KEY, correlationId);
        MDC.put(MDC_TRACE_KEY, traceId);
        MDC.put(MDC_SPAN_KEY, spanId);
        httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
        httpResponse.setHeader(TRACE_ID_HEADER, traceId);

        try { chain.doFilter(request, response); }
        finally {
            MDC.remove(MDC_CORRELATION_KEY);
            MDC.remove(MDC_TRACE_KEY);
            MDC.remove(MDC_SPAN_KEY);
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_ID_LENGTH) {
            return null;
        }
        String trimmed = value.trim();
        return SAFE_ID.matcher(trimmed).matches() ? trimmed : null;
    }
}
