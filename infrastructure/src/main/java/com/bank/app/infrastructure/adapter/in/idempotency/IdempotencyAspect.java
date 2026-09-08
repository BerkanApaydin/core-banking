package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.adapter.in.idempotency.Idempotent;
import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.bank.app.common.application.service.UserContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.bank.app.user.application.port.out.ClientIpResolverPort;

@Aspect
@Component
@Order(1)
public class IdempotencyAspect {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final java.util.regex.Pattern SAFE_KEY =
            java.util.regex.Pattern.compile("[A-Za-z0-9\\-_.:]+");

    private final IdempotencyGuard idempotencyGuard;
    private final UserContextService userContextService;
    private final ObjectMapper objectMapper;
    private final ClientIpResolverPort clientIpResolver;

    public IdempotencyAspect(IdempotencyGuard idempotencyGuard,
            UserContextService userContextService,
            ObjectMapper objectMapper,
            ClientIpResolverPort clientIpResolver) {
        this.idempotencyGuard = idempotencyGuard;
        this.userContextService = userContextService;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
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
            if (idempotent.required()) {
                throw new ConcurrentRequestException("error.idempotency_key_required", null,
                        "Idempotency-Key header is required for this operation.");
            }
            return joinPoint.proceed();
        }

        String trimmedKey = idempotencyKeyHeader.trim();
        // Unbounded header values reach the idempotency_keys table verbatim:
        // cap length + allowlist charset so oversized/garbage keys cannot bloat
        // the table or smuggle control characters into keys/logs.
        if (trimmedKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH
                || !SAFE_KEY.matcher(trimmedKey).matches()) {
            throw new IllegalArgumentException(
                    "Invalid Idempotency-Key header format.");
        }
        String idempotencyKeyHeaderValidated = trimmedKey;

        String key;
        if (idempotent.publicEndpoint()) {
            String clientIp = clientIpResolver.resolveClientIp(
                    request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
            key = clientIp + "_" + idempotencyKeyHeaderValidated;
        } else {
            String username = userContextService.getCurrentUsername()
                    .orElseThrow(() -> new AuthorizationException("You must be logged in."));
            key = username + "_" + idempotencyKeyHeaderValidated;
        }

        IdempotencyGuard.IdempotencyResult result = idempotencyGuard.startRequest(key);

        if (result.isCompleted()) {
            return buildCachedResponse(result);
        } else if (result.isPending()) {
            throw new ConcurrentRequestException("error.concurrent_request", null,
                    "This operation is currently being processed. Please wait.");
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
        HttpStatusCode status = result.responseStatus() != null
                ? HttpStatusCode.valueOf(result.responseStatus())
                : HttpStatusCode.valueOf(200);

        String body = result.responseBody();
        if (body == null || body.isBlank() || "null".equals(body)) {
            return ResponseEntity.status(status).build();
        }

        // Return the stored payload byte-identical to the original response instead of
        // re-deserializing it into a generic tree model, so replayed responses keep the
        // exact JSON shape and content type of the first response.
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
