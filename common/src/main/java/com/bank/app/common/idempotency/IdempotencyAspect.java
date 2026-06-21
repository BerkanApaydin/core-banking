package com.bank.app.common.idempotency;

import com.bank.app.common.exception.AuthorizationException;
import com.bank.app.common.exception.ConcurrentRequestException;
import com.bank.app.common.security.port.out.SecurityContextPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

@Aspect
@Component
@Order(1)  // Transactional proxy'den (Order=Integer.MAX_VALUE) önce çalış
public class IdempotencyAspect {

    private final IdempotencyGuard idempotencyGuard;
    private final SecurityContextPort securityContextPort;
    private final ObjectMapper objectMapper;

    public IdempotencyAspect(IdempotencyGuard idempotencyGuard,
            SecurityContextPort securityContextPort,
            ObjectMapper objectMapper) {
        this.idempotencyGuard = idempotencyGuard;
        this.securityContextPort = securityContextPort;
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

        String username = securityContextPort.getCurrentUsername()
                .orElseThrow(() -> new AuthorizationException("Giriş yapmalısınız."));
        String key = username + "_" + idempotencyKeyHeader;

        IdempotencyGuard.IdempotencyResult result = idempotencyGuard.startRequest(key);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Type returnType = method.getGenericReturnType();

        boolean isResponseEntity = method.getReturnType() != null && ResponseEntity.class.isAssignableFrom(method.getReturnType());
        Class<?> responseBodyClass = Object.class;
        if (isResponseEntity) {
            if (returnType instanceof ParameterizedType paramType) {
                Type[] actualTypeArguments = paramType.getActualTypeArguments();
                if (actualTypeArguments.length > 0 && actualTypeArguments[0] instanceof Class<?> bodyClass) {
                    responseBodyClass = bodyClass;
                }
            }
        } else {
            responseBodyClass = method.getReturnType() != null ? method.getReturnType() : Object.class;
        }

        if (result.isCompleted()) {
            HttpStatusCode status = result.responseStatus() != null
                    ? HttpStatusCode.valueOf(result.responseStatus())
                    : HttpStatusCode.valueOf(200);
            
            boolean isEmptyBody = result.responseBody() == null || result.responseBody().isBlank() || "null".equals(result.responseBody());
            
            if (isResponseEntity) {
                if (isEmptyBody) {
                    return ResponseEntity.status(status).build();
                }
                Object cachedResponse = objectMapper.readValue(result.responseBody(), responseBodyClass);
                return ResponseEntity.status(status).body(cachedResponse);
            } else {
                if (isEmptyBody) {
                    return null;
                }
                return objectMapper.readValue(result.responseBody(), responseBodyClass);
            }
        } else if (result.isPending()) {
            throw new ConcurrentRequestException("error.concurrent_request", null,
                    "Bu işlem şu anda gerçekleştiriliyor. Lütfen bekleyin.");
        }

        try {
            Object responseObj = joinPoint.proceed();
            if (responseObj instanceof ResponseEntity<?> responseEntity) {
                if (responseEntity.getStatusCode().is2xxSuccessful()) {
                    String jsonResponse = responseEntity.getBody() != null
                            ? objectMapper.writeValueAsString(responseEntity.getBody())
                            : "";
                    idempotencyGuard.completeRequest(key, jsonResponse, responseEntity.getStatusCode().value());
                } else {
                    idempotencyGuard.failRequest(key);
                }
            } else {
                String jsonResponse = responseObj != null
                        ? objectMapper.writeValueAsString(responseObj)
                        : "";
                idempotencyGuard.completeRequest(key, jsonResponse, 200);
            }
            return responseObj;
        } catch (Throwable ex) {
            idempotencyGuard.failRequest(key);
            throw ex;
        }
    }
}
