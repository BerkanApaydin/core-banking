package com.bank.app.common.adapter.in.idempotency;

import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Order(1)
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

        if (result.isCompleted()) {
            return buildCachedResponse(result);
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

    private Object buildCachedResponse(IdempotencyGuard.IdempotencyResult result) {
        try {
            HttpStatusCode status = result.responseStatus() != null
                    ? HttpStatusCode.valueOf(result.responseStatus())
                    : HttpStatusCode.valueOf(200);

            String body = result.responseBody();
            if (body == null || body.isBlank() || "null".equals(body)) {
                return ResponseEntity.status(status).build();
            }

            JsonNode cachedBody = objectMapper.readValue(body, JsonNode.class);
            return ResponseEntity.status(status).body(cachedBody);
        } catch (Exception e) {
            throw new RuntimeException("Idempotent cache yanıtı çözümlenemedi", e);
        }
    }
}
