package com.bank.app.infrastructure.adapter.in.web;

import com.bank.app.infrastructure.adapter.in.handler.RequestBodyTooLargeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
            wrapped.getInputStream().read(new byte[2048]);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(body.toString()).contains("REQUEST_BODY_TOO_LARGE");
    }

    @Test
    @DisplayName("should keep the byte count when the wrapped stream is requested twice")
    void shouldNotResetCounterWhenInputStreamIsRequestedTwice() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        when(request.getInputStream()).thenReturn(new FixedServletInputStream(new byte[2048]));
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        doAnswer(invocation -> {
            HttpServletRequest wrapped = invocation.getArgument(0);
            ServletInputStream first = wrapped.getInputStream();
            ServletInputStream second = wrapped.getInputStream();
            assertThat(second).isSameAs(first);
            byte[] chunk = new byte[600];
            assertThat(first.read(chunk)).isEqualTo(600);
            // A fresh wrapper per getInputStream() call would reset the
            // counter; the same stream accumulates past the 1024-byte cap.
            second.read(chunk);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(body.toString()).contains("REQUEST_BODY_TOO_LARGE");
    }

    @Test
    @DisplayName("should keep the byte count when the wrapped reader is requested twice")
    void shouldNotResetCounterWhenReaderIsRequestedTwice() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        when(request.getCharacterEncoding()).thenReturn("UTF-8");
        when(request.getInputStream()).thenReturn(new FixedServletInputStream(new byte[2048]));
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        doAnswer(invocation -> {
            HttpServletRequest wrapped = invocation.getArgument(0);
            BufferedReader first = wrapped.getReader();
            BufferedReader second = wrapped.getReader();
            assertThat(first).isNotNull();
            assertThat(second).isSameAs(first);
            // Read through the wrapped stream directly: draining the reader
            // spins forever if the bulk-read mutant returns 0, which PIT
            // records as TIMED_OUT instead of a kill.
            wrapped.getInputStream().read(new byte[2048]);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(body.toString()).contains("REQUEST_BODY_TOO_LARGE");
    }

    @Test
    @DisplayName("should read a body with no declared charset as UTF-8 instead of throwing")
    void shouldFallBackToUtf8WhenNoCharsetIsDeclared() throws Exception {
        // Content-Type may legally omit charset; passing null to
        // InputStreamReader threw an NPE and turned a valid request into 500.
        String json = "{\"name\":\"J\u00f6rg\"}";
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn((long) payload.length);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        when(request.getCharacterEncoding()).thenReturn(null);
        when(request.getInputStream()).thenReturn(new FixedServletInputStream(payload));
        String[] read = new String[1];
        doAnswer(invocation -> {
            HttpServletRequest wrapped = invocation.getArgument(0);
            read[0] = wrapped.getReader().readLine();
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(read[0]).isEqualTo(json);
    }

    @Test
    @DisplayName("should read a body with an invalid charset token instead of throwing")
    void shouldFallBackToUtf8WhenTheCharsetIsInvalid() throws Exception {
        byte[] payload = "{\"name\":\"Jorg\"}".getBytes(StandardCharsets.UTF_8);
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn((long) payload.length);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        when(request.getCharacterEncoding()).thenReturn("not-a-charset");
        when(request.getInputStream()).thenReturn(new FixedServletInputStream(payload));
        String[] read = new String[1];
        doAnswer(invocation -> {
            HttpServletRequest wrapped = invocation.getArgument(0);
            read[0] = wrapped.getReader().readLine();
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(read[0]).isEqualTo("{\"name\":\"Jorg\"}");
    }

    @Test
    @DisplayName("should accept a declared body exactly at the cap")
    void shouldAcceptDeclaredBodyExactlyAtTheCap() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn(1024L);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");

        filter.doFilter(request, response, chain);

        // The contract is "greater than the cap", not "greater or equal":
        // an off-by-one here rejects every request that fills the limit.
        verify(chain).doFilter(any(), any());
        verify(response, never()).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
    }

    @Test
    @DisplayName("should accept a chunked body exactly at the cap")
    void shouldAcceptChunkedBodyExactlyAtTheCap() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        when(request.getInputStream()).thenReturn(new FixedServletInputStream(new byte[1024]));
        doAnswer(invocation -> {
            HttpServletRequest wrapped = invocation.getArgument(0);
            assertThat(wrapped.getInputStream().read(new byte[2048])).isEqualTo(1024);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(response, never()).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
    }

    @Test
    @DisplayName("should enforce the cap even when Content-Length understates the body")
    void shouldEnforceCapWhenContentLengthLies() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        // A malicious/chunked client can send a small or zero Content-Length
        // and still stream a large body; the header alone is not trusted.
        when(request.getContentLengthLong()).thenReturn(0L);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        when(request.getInputStream()).thenReturn(new FixedServletInputStream(new byte[2048]));
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        doAnswer(invocation -> {
            HttpServletRequest wrapped = invocation.getArgument(0);
            wrapped.getInputStream().read(new byte[2048]);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(body.toString()).contains("REQUEST_BODY_TOO_LARGE");
    }

    @Test
    @DisplayName("should reject a stream read one byte at a time past the cap")
    void shouldRejectSingleByteReadsPastTheCap() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        byte[] content = new byte[2048];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 250 + 1);
        }
        when(request.getInputStream()).thenReturn(new FixedServletInputStream(content));
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        doAnswer(invocation -> {
            HttpServletRequest wrapped = invocation.getArgument(0);
            ServletInputStream stream = wrapped.getInputStream();
            int reads = 0;
            int b;
            while ((b = stream.read()) != -1) {
                // A mutant returning a constant (e.g. 0) instead of the byte
                // fails on the very first value, before the 413 path can mask
                // it; the count guard covers a mutant that still progresses.
                assertThat(b).isEqualTo(content[reads] & 0xff);
                if (++reads > content.length) {
                    throw new AssertionError("read() did not make progress");
                }
            }
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
    }

    @Test
    @DisplayName("should not rewrite a response that was already committed")
    void shouldNotWrite413AfterResponseCommitted() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        when(request.getInputStream()).thenReturn(new FixedServletInputStream(new byte[2048]));
        when(response.isCommitted()).thenReturn(true);
        doAnswer(invocation -> {
            HttpServletRequest wrapped = invocation.getArgument(0);
            wrapped.getInputStream().read(new byte[2048]);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        // Writing a 413 into a committed response would corrupt the bytes the
        // client already started receiving; the filter can only log and stop.
        verify(response, never()).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        verify(response, never()).getWriter();
    }

    @ParameterizedTest(name = "{0} bodies are capped")
    @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
    @DisplayName("should apply the cap to every body-bearing method")
    void shouldCheckEveryBodyBearingMethod(String method) throws Exception {
        when(request.getMethod()).thenReturn(method);
        when(request.getContentLengthLong()).thenReturn(2048L);
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(response).setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
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
    @DisplayName("should delegate reads and async metadata to the underlying stream")
    void shouldDelegateInputStreamReadsAndMetadata() throws Exception {
        ServletInputStream delegate = mock(ServletInputStream.class);
        when(delegate.read()).thenReturn(65, -1);
        when(delegate.read(any(byte[].class), anyInt(), anyInt())).thenReturn(2, -1);
        when(delegate.isFinished()).thenReturn(false, true);
        when(delegate.isReady()).thenReturn(true, false);
        ReadListener listener = mock(ReadListener.class);
        RequestBodySizeLimitFilter.BoundedInputStream stream =
                new RequestBodySizeLimitFilter.BoundedInputStream(delegate, 1024);

        assertThat(stream.read()).isEqualTo(65);
        assertThat(stream.read(new byte[3], 0, 3)).isEqualTo(2);
        assertThat(stream.read(new byte[3], 0, 3)).isEqualTo(-1);
        assertThat(stream.isFinished()).isFalse();
        assertThat(stream.isFinished()).isTrue();
        assertThat(stream.isReady()).isTrue();
        assertThat(stream.isReady()).isFalse();
        stream.setReadListener(listener);

        verify(delegate).setReadListener(listener);
    }

    @Test
    @DisplayName("should pass end-of-stream through without counting it")
    void shouldPassEndOfStreamWithoutCounting() throws Exception {
        ServletInputStream delegate = mock(ServletInputStream.class);
        when(delegate.read()).thenReturn(-1);
        RequestBodySizeLimitFilter.BoundedInputStream stream =
                new RequestBodySizeLimitFilter.BoundedInputStream(delegate, 0);

        assertThat(stream.read()).isEqualTo(-1);
    }

    @Test
    @DisplayName("should count every positive chunk against the cap")
    void shouldCountPositiveChunksAgainstTheCap() throws Exception {
        ServletInputStream delegate = mock(ServletInputStream.class);
        when(delegate.read(any(byte[].class), anyInt(), anyInt())).thenReturn(1);
        RequestBodySizeLimitFilter.BoundedInputStream stream =
                new RequestBodySizeLimitFilter.BoundedInputStream(delegate, 0);

        assertThrows(RequestBodyTooLargeException.class,
                () -> stream.read(new byte[1], 0, 1));
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
