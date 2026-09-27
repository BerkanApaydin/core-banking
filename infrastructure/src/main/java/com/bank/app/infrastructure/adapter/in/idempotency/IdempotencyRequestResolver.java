package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.adapter.in.idempotency.Idempotent;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

/** Resolves and validates the client key, security scope and request fingerprint. */
final class IdempotencyRequestResolver {
    private static final int MAX_KEY_LENGTH = 128;
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9\\-_.:]+");

    private final UserContextService userContextService;
    private final ClientIpResolverPort clientIpResolver;
    private final ObjectMapper objectMapper;

    IdempotencyRequestResolver(UserContextService userContextService,
            ClientIpResolverPort clientIpResolver, ObjectMapper objectMapper) {
        this.userContextService = userContextService;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    /** A null result means an optional header was absent. */
    RequestIdentity resolve(HttpServletRequest request, Idempotent idempotent, Object[] arguments)
            throws JsonProcessingException {
        String header = request.getHeader(idempotent.headerName());
        if (header == null || header.isBlank()) {
            if (idempotent.required()) {
                throw new ConcurrentRequestException("error.idempotency_key_required", null,
                        "Idempotency-Key header is required for this operation.");
            }
            return null;
        }

        String clientKey = header.trim();
        if (clientKey.length() > MAX_KEY_LENGTH || !SAFE_KEY.matcher(clientKey).matches()) {
            throw new IllegalArgumentException("Invalid Idempotency-Key header format.");
        }

        String scope;
        String subject;
        if (idempotent.publicEndpoint()) {
            subject = clientIpResolver.resolveClientIp(
                    request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
            scope = "public";
        } else {
            userContextService.getCurrentUsername()
                    .orElseThrow(() -> new AuthorizationException("You must be logged in."));
            subject = userContextService.getCurrentUserId()
                    .map(String::valueOf)
                    .orElseThrow(() -> new AuthorizationException("Authenticated user ID is required."));
            scope = "user";
        }

        String key = IdempotencyFingerprint.operationKey(scope, subject,
                request.getMethod(), request.getRequestURI(), clientKey);
        byte[] requestBytes = objectMapper.writeValueAsBytes(arguments == null ? new Object[0] : arguments);
        String hash = IdempotencyFingerprint.requestHash(
                request.getMethod(), request.getRequestURI(), request.getQueryString(), requestBytes);
        return new RequestIdentity(key, hash);
    }

    record RequestIdentity(String key, String requestHash) {}
}
