package com.bank.app.common.idempotency;

import com.bank.app.common.exception.ConcurrentRequestException;
import com.bank.app.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

@Aspect
@Component
public class IdempotencyAspect {

    private final IdempotencyManager idempotencyManager;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    public IdempotencyAspect(IdempotencyManager idempotencyManager,
                             SecurityUtils securityUtils,
                             ObjectMapper objectMapper) {
        this.idempotencyManager = idempotencyManager;
        this.securityUtils = securityUtils;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String idempotencyKeyHeader = request.getHeader(idempotent.headerName());

        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            return joinPoint.proceed();
        }

        String username = securityUtils.getCurrentUsername()
                .orElseThrow(() -> new AccessDeniedException("Giriş yapmalısınız."));
        String key = username + "_" + idempotencyKeyHeader;

        IdempotencyManager.IdempotencyResult result = idempotencyManager.startRequest(key);
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Type returnType = method.getGenericReturnType();
        
        Class<?> responseBodyClass = Object.class;
        if (returnType instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) returnType;
            Type[] actualTypeArguments = paramType.getActualTypeArguments();
            if (actualTypeArguments.length > 0 && actualTypeArguments[0] instanceof Class) {
                responseBodyClass = (Class<?>) actualTypeArguments[0];
            }
        }

        if (result.isCompleted()) {
            Object cachedResponse = objectMapper.readValue(result.responseBody(), responseBodyClass);
            return ResponseEntity.ok(cachedResponse);
        } else if (result.isPending()) {
            throw new ConcurrentRequestException("error.concurrent_request", null, "Bu işlem şu anda gerçekleştiriliyor. Lütfen bekleyin.");
        }

        try {
            Object responseObj = joinPoint.proceed();
            if (responseObj instanceof ResponseEntity) {
                ResponseEntity<?> responseEntity = (ResponseEntity<?>) responseObj;
                if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                    String jsonResponse = objectMapper.writeValueAsString(responseEntity.getBody());
                    idempotencyManager.completeRequest(key, jsonResponse);
                } else {
                    idempotencyManager.failRequest(key);
                }
            } else {
                idempotencyManager.failRequest(key);
            }
            return responseObj;
        } catch (Throwable ex) {
            idempotencyManager.failRequest(key);
            throw ex;
        }
    }
}
