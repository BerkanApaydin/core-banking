package com.bank.app.infrastructure.adapter.in.web;

import com.bank.app.common.messages.MessageKeys;
import com.bank.app.infrastructure.adapter.in.handler.ProblemDetailFactory;
import com.bank.app.infrastructure.adapter.in.handler.RequestBodyTooLargeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * SE-4: rejects oversized request bodies with 413 before Jackson parses them.
 *
 * <p>Runs first ({@code HIGHEST_PRECEDENCE}), ahead of authentication and rate
 * limiting: a 100MB JSON body must not burn a BCrypt comparison or a Lua
 * round trip before being rejected. Known lengths fail fast on the
 * {@code Content-Length} header; unknown (chunked) lengths are capped by a
 * counting wrapper so an unbounded stream cannot OOM the reader either.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodySizeLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestBodySizeLimitFilter.class);

    private final RequestBodyLimitProperties properties;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    public RequestBodySizeLimitFilter(RequestBodyLimitProperties properties,
                                      MessageSource messageSource,
                                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!hasBody(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        long maxBytes = properties.maxBytes();
        long declared = httpRequest.getContentLengthLong();
        String path = RequestPathResolver.resolve(httpRequest);
        if (declared > maxBytes) {
            reject(httpResponse, path, maxBytes);
            return;
        }
        try {
            chain.doFilter(new BoundedRequestWrapper(httpRequest, maxBytes), response);
        } catch (RequestBodyTooLargeException tooLarge) {
            // Direct propagation (e.g. a filter/controller reading the stream
            // outside an MVC message converter). Converters instead wrap this
            // exception in HttpMessageNotReadableException; RequestProblemHandler
            // inspects the cause chain and also answers 413.
            if (httpResponse.isCommitted()) {
                log.warn("Oversized request body detected after the response was committed: path={}", path);
                return;
            }
            reject(httpResponse, path, maxBytes);
        }
    }

    private static boolean hasBody(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private void reject(HttpServletResponse httpResponse, String path, long maxBytes) throws IOException {
        log.warn("Oversized request body rejected: path={}, limitBytes={}", path, maxBytes);
        String message = messageSource.getMessage(MessageKeys.REQUEST_BODY_TOO_LARGE, null,
                "Request body is too large.", LocaleContextHolder.getLocale());
        ProblemDetailFactory.writeProblem(httpResponse, objectMapper, HttpStatus.PAYLOAD_TOO_LARGE,
                "REQUEST_BODY_TOO_LARGE", message, path);
    }

    /**
     * Counts every byte read from the body (stream and reader paths) and
     * fails once the cap is exceeded — chunked requests with no
     * Content-Length are covered too.
     */
    static final class BoundedRequestWrapper extends HttpServletRequestWrapper {
        private final long maxBytes;

        BoundedRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new BoundedInputStream(super.getInputStream(), maxBytes);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding()));
        }
    }

    static final class BoundedInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maxBytes;
        private long seen;

        BoundedInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws RequestBodyTooLargeException {
            seen += n;
            if (seen > maxBytes) {
                throw new RequestBodyTooLargeException(
                        "Request body exceeded " + maxBytes + " bytes");
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
