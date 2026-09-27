package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.adapter.in.idempotency.Idempotent;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.bank.app.infrastructure.adapter.in.config.TransactionProperties;
import com.bank.app.common.domain.exception.AuthorizationException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class IdempotencyAspectTest {

    @Mock
    private IdempotencyGuard idempotencyGuard;

    @Mock
    private UserContextService userContextService;

    @Mock
    private ClientIpResolverPort clientIpResolver;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private HttpServletRequest request;

    @Mock
    private PlatformTransactionManager transactionManager;

    private IdempotencyAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new IdempotencyAspect(
                idempotencyGuard,
                userContextService,
                objectMapper,
                clientIpResolver,
                transactionManager,
                new TransactionProperties(30),
                3,
                0);
        lenient().when(transactionManager.getTransaction(any())).thenAnswer(
                ignored -> new SimpleTransactionStatus());
        lenient().when(userContextService.getCurrentUserId()).thenReturn(Optional.of(42L));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void mockRequest(String header) {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

        when(request.getHeader("Idempotency-Key"))
                .thenReturn(header);
        lenient().when(request.getMethod()).thenReturn("POST");
        lenient().when(request.getRequestURI()).thenReturn("/api/v1/transfers");
    }

    private String userKey() {
        return IdempotencyFingerprint.operationKey("user", "42", "POST", "/api/v1/transfers", "abc");
    }

    private String publicKey() {
        return IdempotencyFingerprint.operationKey("public", "10.0.0.1", "POST", "/api/v1/transfers", "abc");
    }

    private Idempotent annotation() {
        return new Idempotent() {
            @Override
            public String headerName() {
                return "Idempotency-Key";
            }

            @Override
            public boolean publicEndpoint() {
                return false;
            }

            @Override
            public boolean required() {
                return false;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Idempotent.class;
            }
        };
    }

    private Idempotent publicAnnotation() {
        return new Idempotent() {
            @Override
            public String headerName() {
                return "Idempotency-Key";
            }

            @Override
            public boolean publicEndpoint() {
                return true;
            }

            @Override
            public boolean required() {
                return false;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Idempotent.class;
            }
        };
    }

    @Test
    void shouldProceedWhenRequestContextMissing() throws Throwable {

        when(joinPoint.proceed()).thenReturn("OK");

        Object result = aspect.handleIdempotency(joinPoint, annotation());

        assertEquals("OK", result);
        verify(joinPoint).proceed();
    }

    @Test
    void shouldProceedWhenHeaderNull() throws Throwable {

        mockRequest(null);

        when(joinPoint.proceed()).thenReturn("OK");

        Object result = aspect.handleIdempotency(joinPoint, annotation());

        assertEquals("OK", result);
    }

    @Test
    void shouldProceedWhenHeaderBlank() throws Throwable {

        mockRequest(" ");

        when(joinPoint.proceed()).thenReturn("OK");

        Object result = aspect.handleIdempotency(joinPoint, annotation());

        assertEquals("OK", result);
    }

    @Test
    void shouldRejectMissingHeaderWhenRequired() {
        mockRequest(null);

        Idempotent required = new Idempotent() {
            @Override
            public String headerName() {
                return "Idempotency-Key";
            }

            @Override
            public boolean publicEndpoint() {
                return false;
            }

            @Override
            public boolean required() {
                return true;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Idempotent.class;
            }
        };

        ConcurrentRequestException ex = assertThrows(
                ConcurrentRequestException.class,
                () -> aspect.handleIdempotency(joinPoint, required));
        assertEquals("error.idempotency_key_required", ex.getMessageKey());
    }

    @Test
    void shouldThrowAccessDeniedException() {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.empty());

        assertThrows(
                AuthorizationException.class,
                () -> aspect.handleIdempotency(joinPoint, annotation()));
    }

    @Test
    void shouldReturnCompletedResponseWithCustomStatus() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest(eq(userKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.completed(
                                "{\"message\":\"cached\"}",
                                201));

        ResponseEntity<?> result = (ResponseEntity<?>) aspect.handleIdempotency(
                joinPoint,
                annotation());

        assertEquals(201, result.getStatusCode().value());
        assertEquals("{\"message\":\"cached\"}", result.getBody());
        assertEquals(MediaType.APPLICATION_JSON, result.getHeaders().getContentType());
    }

    @Test
    void shouldReturnCompletedResponseWithDefaultStatus() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest(eq(userKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.completed(
                                "{}",
                                null));

        ResponseEntity<?> result = (ResponseEntity<?>) aspect.handleIdempotency(
                joinPoint,
                annotation());

        assertEquals(200, result.getStatusCode().value());
        assertEquals("{}", result.getBody());
        assertEquals(MediaType.APPLICATION_JSON, result.getHeaders().getContentType());
    }

    @Test
    void shouldThrowConcurrentRequestException() throws Exception {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest(eq(userKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.pending());

        assertThrows(
                ConcurrentRequestException.class,
                () -> aspect.handleIdempotency(joinPoint, annotation()));
    }

    @Test
    void shouldCompleteRequestWhenResponseSuccessful() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest(eq(userKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.newRequest());

        String body = "success";

        when(joinPoint.proceed())
                .thenReturn(ResponseEntity.ok(body));

        when(objectMapper.writeValueAsString(body))
                .thenReturn("\"success\"");

        Object result = aspect.handleIdempotency(joinPoint, annotation());

        verify(idempotencyGuard)
                .completeRequest(
                        eq(userKey()),
                        anyString(),
                        eq(200));
        assertNotNull(result);
        assertInstanceOf(ResponseEntity.class, result);
        assertEquals(200, ((ResponseEntity<?>) result).getStatusCode().value());
    }

    @Test
    void shouldCompleteWhenBodyNull() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest(eq(userKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.newRequest());

        when(joinPoint.proceed())
                .thenReturn(ResponseEntity.ok().build());

        Object result = aspect.handleIdempotency(joinPoint, annotation());

        verify(idempotencyGuard)
                .completeRequest(userKey(), "", 200);
        assertNotNull(result);
        assertEquals(200, ((ResponseEntity<?>) result).getStatusCode().value());
    }

    @Test
    void shouldFailWhenStatusNotSuccessful() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest(eq(userKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.newRequest());

        when(joinPoint.proceed())
                .thenReturn(ResponseEntity.badRequest().build());

        Object result = aspect.handleIdempotency(joinPoint, annotation());

        verify(idempotencyGuard)
                .failRequest(userKey());
        assertNotNull(result);
        assertEquals(400, ((ResponseEntity<?>) result).getStatusCode().value());
    }

    @Test
    void shouldCompleteWhenReturnTypeNotResponseEntity() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest(eq(userKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.newRequest());

        when(joinPoint.proceed())
                .thenReturn("OK");

        when(objectMapper.writeValueAsString("OK"))
                .thenReturn("\"OK\"");

        Object result = aspect.handleIdempotency(joinPoint, annotation());

        verify(idempotencyGuard)
                .completeRequest(userKey(), "\"OK\"", 200);
        assertEquals("OK", result);
    }

    @Test
    void shouldFailAndRethrowException() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest(eq(userKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.newRequest());

        when(joinPoint.proceed())
                .thenThrow(new RuntimeException("boom"));

        assertThrows(
                RuntimeException.class,
                () -> aspect.handleIdempotency(joinPoint, annotation()));

        verify(idempotencyGuard)
                .failRequest(userKey());
    }

    @Test
    void shouldReturnEmptyBodyWhenCachedResponseIsNull() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest(eq(userKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.completed(
                                null,
                                200));

        ResponseEntity<?> result = (ResponseEntity<?>) aspect.handleIdempotency(
                joinPoint,
                annotation());

        assertEquals(200, result.getStatusCode().value());
        assertNull(result.getBody());
    }

    @Test
    void shouldReturnEmptyBodyWhenCachedResponseIsBlank() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest(eq(userKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.completed(
                                "",
                                200));

        ResponseEntity<?> result = (ResponseEntity<?>) aspect.handleIdempotency(
                joinPoint,
                annotation());

        assertEquals(200, result.getStatusCode().value());
        assertNull(result.getBody());
    }

    @Test
    void shouldReturnEmptyBodyWhenCachedResponseIsNullLiteral() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest(eq(userKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.completed(
                                "null",
                                200));

        ResponseEntity<?> result = (ResponseEntity<?>) aspect.handleIdempotency(
                joinPoint,
                annotation());

        assertEquals(200, result.getStatusCode().value());
        assertNull(result.getBody());
    }

    @Test
    void shouldUseClientIpForPublicEndpoint() throws Throwable {

        mockRequest("abc");

        when(clientIpResolver.resolveClientIp(any(), any()))
                .thenReturn("10.0.0.1");

        when(idempotencyGuard.startRequest(eq(publicKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.newRequest());

        when(joinPoint.proceed())
                .thenReturn(ResponseEntity.ok().build());

        Object result = aspect.handleIdempotency(joinPoint, publicAnnotation());

        verify(idempotencyGuard)
                .completeRequest(publicKey(), "", 200);
        assertNotNull(result);
        assertEquals(200, ((ResponseEntity<?>) result).getStatusCode().value());
    }

    @Test
    void shouldReturnCompletedForPublicEndpoint() throws Throwable {

        mockRequest("abc");

        when(clientIpResolver.resolveClientIp(any(), any()))
                .thenReturn("10.0.0.1");

        when(idempotencyGuard.startRequest(eq(publicKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.completed(
                                "{\"message\":\"cached\"}",
                                201));

        ResponseEntity<?> result = (ResponseEntity<?>) aspect.handleIdempotency(
                joinPoint,
                publicAnnotation());

        assertEquals(201, result.getStatusCode().value());
        assertEquals("{\"message\":\"cached\"}", result.getBody());
        assertEquals(MediaType.APPLICATION_JSON, result.getHeaders().getContentType());
    }

    @Test
    void shouldThrowConcurrentRequestForPublicEndpoint() throws Exception {

        mockRequest("abc");

        when(clientIpResolver.resolveClientIp(any(), any()))
                .thenReturn("10.0.0.1");

        when(idempotencyGuard.startRequest(eq(publicKey()), anyString()))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.pending());

        assertThrows(
                ConcurrentRequestException.class,
                () -> aspect.handleIdempotency(joinPoint, publicAnnotation()));
    }

    @Test
    void shouldProceedWhenPublicEndpointHeaderNull() throws Throwable {

        mockRequest(null);

        when(joinPoint.proceed()).thenReturn("OK");

        Object result = aspect.handleIdempotency(joinPoint, publicAnnotation());

        assertEquals("OK", result);
        verify(clientIpResolver, never()).resolveClientIp(any(), any());
    }

    @Test
    void shouldRejectOverlongIdempotencyKey() {
        mockRequest("a".repeat(129));

        assertThrows(
                IllegalArgumentException.class,
                () -> aspect.handleIdempotency(joinPoint, annotation()));
    }

    @Test
    void shouldRejectIdempotencyKeyWithIllegalCharacters() {
        mockRequest("abc def;DROP");

        assertThrows(
                IllegalArgumentException.class,
                () -> aspect.handleIdempotency(joinPoint, annotation()));
    }

    private void reserveNewRequest() {
        mockRequest("abc");
        when(userContextService.getCurrentUsername()).thenReturn(Optional.of("user"));
        when(idempotencyGuard.startRequest(eq(userKey()), anyString()))
                .thenReturn(IdempotencyGuard.IdempotencyResult.newRequest());
    }

    @Test
    void interruptedRetryReleasesReservationOnlyAfterRollbackAndRestoresInterrupt() throws Throwable {
        reserveNewRequest();
        when(joinPoint.proceed()).thenThrow(new org.springframework.dao.OptimisticLockingFailureException("stale"));
        try {
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class, () -> aspect.handleIdempotency(joinPoint, annotation()));
            assertTrue(Thread.currentThread().isInterrupted());
            var order = inOrder(transactionManager, idempotencyGuard);
            order.verify(transactionManager).rollback(any());
            order.verify(idempotencyGuard).failRequest(userKey());
            verify(joinPoint).proceed();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void cleanupFailureDoesNotHideOriginalBusinessFailure() throws Throwable {
        reserveNewRequest();
        var original = new IllegalArgumentException("invalid business operation");
        var cleanup = new IllegalStateException("database unavailable");
        when(joinPoint.proceed()).thenThrow(original);
        doThrow(cleanup).when(idempotencyGuard).failRequest(userKey());

        Throwable result = assertThrows(IllegalArgumentException.class,
                () -> aspect.handleIdempotency(joinPoint, annotation()));
        assertSame(original, result);
        assertArrayEquals(new Throwable[]{cleanup}, result.getSuppressed());
        verify(transactionManager).rollback(any());
    }

    @Test
    void interruptionIsPreservedEvenWhenCleanupFails() throws Throwable {
        reserveNewRequest();
        when(joinPoint.proceed()).thenThrow(new org.springframework.dao.PessimisticLockingFailureException("busy"));
        var cleanup = new IllegalStateException("database unavailable");
        doThrow(cleanup).when(idempotencyGuard).failRequest(userKey());
        try {
            Thread.currentThread().interrupt();
            var interrupted = assertThrows(InterruptedException.class,
                    () -> aspect.handleIdempotency(joinPoint, annotation()));
            assertTrue(Thread.currentThread().isInterrupted());
            assertArrayEquals(new Throwable[]{cleanup}, interrupted.getSuppressed());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void ambiguousCommitFailureMustRetainReservation() throws Throwable {
        reserveNewRequest();
        when(joinPoint.proceed()).thenReturn(ResponseEntity.ok().build());
        var commitFailure = new org.springframework.transaction.TransactionSystemException("commit acknowledgement lost");
        doThrow(commitFailure).when(transactionManager).commit(any());

        assertSame(commitFailure, assertThrows(org.springframework.transaction.TransactionSystemException.class,
                () -> aspect.handleIdempotency(joinPoint, annotation())));
        verify(idempotencyGuard, never()).failRequest(anyString());
        verify(joinPoint).proceed();
    }

    @Test
    void uncertainRollbackFailureMustRetainReservation() throws Throwable {
        reserveNewRequest();
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("business failure"));
        var rollbackFailure = new org.springframework.transaction.TransactionSystemException("rollback failed");
        doThrow(rollbackFailure).when(transactionManager).rollback(any());

        assertSame(rollbackFailure, assertThrows(org.springframework.transaction.TransactionSystemException.class,
                () -> aspect.handleIdempotency(joinPoint, annotation())));
        verify(idempotencyGuard, never()).failRequest(anyString());
    }

    @Test
    void retriesOnlyAfterRollbackUsingANewTransaction() throws Throwable {
        reserveNewRequest();
        when(joinPoint.proceed())
                .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("stale"))
                .thenReturn(ResponseEntity.ok().build());

        aspect.handleIdempotency(joinPoint, annotation());

        var order = inOrder(transactionManager, joinPoint, idempotencyGuard);
        order.verify(transactionManager).getTransaction(any());
        order.verify(joinPoint).proceed();
        order.verify(transactionManager).rollback(any());
        order.verify(transactionManager).getTransaction(any());
        order.verify(joinPoint).proceed();
        order.verify(idempotencyGuard).completeRequest(userKey(), "", 200);
        order.verify(transactionManager).commit(any());
        verify(idempotencyGuard, never()).failRequest(anyString());
    }
}
