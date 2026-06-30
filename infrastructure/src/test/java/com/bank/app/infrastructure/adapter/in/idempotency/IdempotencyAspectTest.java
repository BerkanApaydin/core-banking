package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.adapter.in.idempotency.Idempotent;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.bank.app.common.application.service.UserContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
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
    private ObjectMapper objectMapper;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private HttpServletRequest request;

    @Mock
    private JsonNode jsonNode;

    private IdempotencyAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new IdempotencyAspect(
                idempotencyGuard,
                userContextService,
                objectMapper);
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
    }

    private Idempotent annotation() {
        return new Idempotent() {
            @Override
            public String headerName() {
                return "Idempotency-Key";
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

        when(idempotencyGuard.startRequest("user_abc"))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.completed(
                                "{\"message\":\"cached\"}",
                                201));

        when(objectMapper.readValue(
                anyString(),
                eq(JsonNode.class)))
                .thenReturn(jsonNode);

        ResponseEntity<?> result = (ResponseEntity<?>) aspect.handleIdempotency(
                joinPoint,
                annotation());

        assertEquals(201, result.getStatusCode().value());
        assertSame(jsonNode, result.getBody());
    }

    @Test
    void shouldReturnCompletedResponseWithDefaultStatus() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest("user_abc"))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.completed(
                                "{}",
                                null));

        when(objectMapper.readValue(
                anyString(),
                eq(JsonNode.class)))
                .thenReturn(jsonNode);

        ResponseEntity<?> result = (ResponseEntity<?>) aspect.handleIdempotency(
                joinPoint,
                annotation());

        assertEquals(200, result.getStatusCode().value());
        assertSame(jsonNode, result.getBody());
    }

    @Test
    void shouldThrowConcurrentRequestException() throws Exception {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest("user_abc"))
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

        when(idempotencyGuard.startRequest("user_abc"))
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
                        eq("user_abc"),
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

        when(idempotencyGuard.startRequest("user_abc"))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.newRequest());

        when(joinPoint.proceed())
                .thenReturn(ResponseEntity.ok().build());

        Object result = aspect.handleIdempotency(joinPoint, annotation());

        verify(idempotencyGuard)
                .completeRequest("user_abc", "", 200);
        assertNotNull(result);
        assertEquals(200, ((ResponseEntity<?>) result).getStatusCode().value());
    }

    @Test
    void shouldFailWhenStatusNotSuccessful() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest("user_abc"))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.newRequest());

        when(joinPoint.proceed())
                .thenReturn(ResponseEntity.badRequest().build());

        Object result = aspect.handleIdempotency(joinPoint, annotation());

        verify(idempotencyGuard)
                .failRequest("user_abc");
        assertNotNull(result);
        assertEquals(400, ((ResponseEntity<?>) result).getStatusCode().value());
    }

    @Test
    void shouldCompleteWhenReturnTypeNotResponseEntity() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest("user_abc"))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.newRequest());

        when(joinPoint.proceed())
                .thenReturn("OK");

        when(objectMapper.writeValueAsString("OK"))
                .thenReturn("\"OK\"");

        Object result = aspect.handleIdempotency(joinPoint, annotation());

        verify(idempotencyGuard)
                .completeRequest("user_abc", "\"OK\"", 200);
        assertEquals("OK", result);
    }

    @Test
    void shouldFailAndRethrowException() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest("user_abc"))
                .thenReturn(
                        IdempotencyGuard.IdempotencyResult.newRequest());

        when(joinPoint.proceed())
                .thenThrow(new RuntimeException("boom"));

        assertThrows(
                RuntimeException.class,
                () -> aspect.handleIdempotency(joinPoint, annotation()));

        verify(idempotencyGuard)
                .failRequest("user_abc");
    }

    @Test
    void shouldReturnEmptyBodyWhenCachedResponseIsNull() throws Throwable {

        mockRequest("abc");

        when(userContextService.getCurrentUsername())
                .thenReturn(Optional.of("user"));

        when(idempotencyGuard.startRequest("user_abc"))
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

        when(idempotencyGuard.startRequest("user_abc"))
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

        when(idempotencyGuard.startRequest("user_abc"))
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
}
