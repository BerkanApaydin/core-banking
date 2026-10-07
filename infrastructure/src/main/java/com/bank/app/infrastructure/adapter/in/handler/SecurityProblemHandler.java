package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.domain.exception.ErrorCode;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Authentication, authorization and security-backend failures. Every branch
 * returns a fixed catalog message: backend internals (Redis details, SQL
 * fragments, missing roles/paths) are logged, never echoed, so error bodies
 * cannot become an oracle or a leak.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SecurityProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityProblemHandler.class);

    private final ProblemMessageResolver messages;

    public SecurityProblemHandler(ProblemMessageResolver messages) {
        this.messages = messages;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.warn("Authentication failed: {}", ex.getClass().getSimpleName());
        String message = messages.resolveOrDefault("error.authentication_failed", "Authentication failed.");
        return ProblemDetailFactory.create(ErrorCode.AUTHENTICATION_FAILED, message, request);
    }

    @ExceptionHandler(LoginAttemptStoreUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleLoginAttemptStoreUnavailable(
            LoginAttemptStoreUnavailableException ex, WebRequest request) {
        log.warn("Failed-login security backend unavailable: {}",
                ex.getCause() == null ? ex.getClass().getSimpleName() : ex.getCause().getClass().getSimpleName());
        String message = messages.resolveOrDefault("error.security_backend_unavailable",
                "Security service temporarily unavailable. Please try again later.");
        return ProblemDetailFactory.create(ErrorCode.SECURITY_BACKEND_UNAVAILABLE, message, request);
    }

    @ExceptionHandler(AuthenticationBackendUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationBackendUnavailable(
            AuthenticationBackendUnavailableException ex, WebRequest request) {
        log.warn("Authentication backend unavailable: {}",
                ex.getCause() == null ? ex.getClass().getSimpleName() : ex.getCause().getClass().getSimpleName());
        String message = messages.resolveOrDefault("error.security_backend_unavailable",
                "Security service temporarily unavailable. Please try again later.");
        return ProblemDetailFactory.create(ErrorCode.SECURITY_BACKEND_UNAVAILABLE, message, request);
    }

    @ExceptionHandler(RevocationStoreUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleRevocationStoreUnavailable(
            RevocationStoreUnavailableException ex, WebRequest request) {
        log.warn("Token revocation backend unavailable: {}",
                ex.getCause() == null ? ex.getClass().getSimpleName() : ex.getCause().getClass().getSimpleName());
        String message = messages.resolveOrDefault("error.security_backend_unavailable",
                "Security service temporarily unavailable. Please try again later.");
        return ProblemDetailFactory.create(ErrorCode.SECURITY_BACKEND_UNAVAILABLE, message, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", ex.getClass().getSimpleName());
        String message = messages.resolveOrDefault("error.access_denied", "Access denied.");
        return ProblemDetailFactory.create(ErrorCode.ACCESS_DENIED, message, request);
    }
}
