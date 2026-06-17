package com.bank.app.common.idempotency;

import com.bank.app.common.exception.ConcurrentRequestException;
import com.bank.app.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencyAspectTest {

    private IdempotencyManager idempotencyManager;
    private SecurityUtils securityUtils;
    private ObjectMapper objectMapper;
    private IdempotencyAspect aspect;

    private ProceedingJoinPoint joinPoint;
    private MethodSignature methodSignature;
    private Idempotent idempotent;
    private HttpServletRequest servletRequest;

    @BeforeEach
    void setUp() {
        idempotencyManager = mock(IdempotencyManager.class);
        securityUtils = mock(SecurityUtils.class);
        objectMapper = mock(ObjectMapper.class);
        aspect = new IdempotencyAspect(idempotencyManager, securityUtils, objectMapper);

        joinPoint = mock(ProceedingJoinPoint.class);
        methodSignature = mock(MethodSignature.class);
        idempotent = mock(Idempotent.class);
        servletRequest = mock(HttpServletRequest.class);

        when(idempotent.headerName()).thenReturn("Idempotency-Key");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldProceedWhenNoRequestAttributes() throws Throwable {
        RequestContextHolder.resetRequestAttributes();
        when(joinPoint.proceed()).thenReturn("proceeded");

        Object result = aspect.handleIdempotency(joinPoint, idempotent);

        assertEquals("proceeded", result);
        verify(joinPoint).proceed();
        verifyNoInteractions(idempotencyManager);
    }

    @Test
    void shouldProceedWhenHeaderIsMissing() throws Throwable {
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(servletRequest);
        RequestContextHolder.setRequestAttributes(attributes);
        when(servletRequest.getHeader("Idempotency-Key")).thenReturn(null);
        when(joinPoint.proceed()).thenReturn("proceeded");

        Object result = aspect.handleIdempotency(joinPoint, idempotent);

        assertEquals("proceeded", result);
        verify(joinPoint).proceed();
        verifyNoInteractions(idempotencyManager);
    }

    @Test
    void shouldThrowAccessDeniedWhenNotLoggedIn() throws Throwable {
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(servletRequest);
        RequestContextHolder.setRequestAttributes(attributes);
        when(servletRequest.getHeader("Idempotency-Key")).thenReturn("key-123");
        when(securityUtils.getCurrentUsername()).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> aspect.handleIdempotency(joinPoint, idempotent));
        verifyNoInteractions(idempotencyManager);
    }

    interface TestController {
        ResponseEntity<String> myMethod();
    }

    @Test
    void shouldReturnCachedValueWhenCompleted() throws Throwable {
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(servletRequest);
        RequestContextHolder.setRequestAttributes(attributes);
        when(servletRequest.getHeader("Idempotency-Key")).thenReturn("key-123");
        when(securityUtils.getCurrentUsername()).thenReturn(Optional.of("user1"));

        IdempotencyManager.IdempotencyResult cachedResult = IdempotencyManager.IdempotencyResult.completed("cached-json");
        when(idempotencyManager.startRequest("user1_key-123")).thenReturn(cachedResult);

        Method method = TestController.class.getMethod("myMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getReturnType()).thenReturn(ResponseEntity.class);
        when(objectMapper.readValue("cached-json", String.class)).thenReturn("cached-response");

        Object result = aspect.handleIdempotency(joinPoint, idempotent);

        assertTrue(result instanceof ResponseEntity);
        ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
        assertEquals("cached-response", responseEntity.getBody());
        assertEquals(200, responseEntity.getStatusCode().value());
    }

    @Test
    void shouldThrowConcurrentRequestWhenPending() throws Throwable {
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(servletRequest);
        RequestContextHolder.setRequestAttributes(attributes);
        when(servletRequest.getHeader("Idempotency-Key")).thenReturn("key-123");
        when(securityUtils.getCurrentUsername()).thenReturn(Optional.of("user1"));

        IdempotencyManager.IdempotencyResult cachedResult = IdempotencyManager.IdempotencyResult.pending();
        when(idempotencyManager.startRequest("user1_key-123")).thenReturn(cachedResult);

        Method method = TestController.class.getMethod("myMethod");
        when(methodSignature.getMethod()).thenReturn(method);

        assertThrows(ConcurrentRequestException.class, () -> aspect.handleIdempotency(joinPoint, idempotent));
    }

    @Test
    void shouldExecuteAndCompleteRequestWhenNew() throws Throwable {
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(servletRequest);
        RequestContextHolder.setRequestAttributes(attributes);
        when(servletRequest.getHeader("Idempotency-Key")).thenReturn("key-123");
        when(securityUtils.getCurrentUsername()).thenReturn(Optional.of("user1"));

        IdempotencyManager.IdempotencyResult newResult = IdempotencyManager.IdempotencyResult.newRequest();
        when(idempotencyManager.startRequest("user1_key-123")).thenReturn(newResult);

        Method method = TestController.class.getMethod("myMethod");
        when(methodSignature.getMethod()).thenReturn(method);

        ResponseEntity<String> response = ResponseEntity.ok("my-response");
        when(joinPoint.proceed()).thenReturn(response);
        when(objectMapper.writeValueAsString("my-response")).thenReturn("json-response");

        Object result = aspect.handleIdempotency(joinPoint, idempotent);

        assertEquals(response, result);
        verify(idempotencyManager).completeRequest("user1_key-123", "json-response");
    }

    @Test
    void shouldFailRequestWhenExceptionOccurs() throws Throwable {
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(servletRequest);
        RequestContextHolder.setRequestAttributes(attributes);
        when(servletRequest.getHeader("Idempotency-Key")).thenReturn("key-123");
        when(securityUtils.getCurrentUsername()).thenReturn(Optional.of("user1"));

        IdempotencyManager.IdempotencyResult newResult = IdempotencyManager.IdempotencyResult.newRequest();
        when(idempotencyManager.startRequest("user1_key-123")).thenReturn(newResult);

        Method method = TestController.class.getMethod("myMethod");
        when(methodSignature.getMethod()).thenReturn(method);

        when(joinPoint.proceed()).thenThrow(new RuntimeException("database error"));

        assertThrows(RuntimeException.class, () -> aspect.handleIdempotency(joinPoint, idempotent));
        verify(idempotencyManager).failRequest("user1_key-123");
    }
}
