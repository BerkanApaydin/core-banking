package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.bank.app.common.domain.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Domain/business failures: use-case rejections, concurrent-request guards,
 * optimistic-lock conflicts and authorization denials. Owns the
 * {@code bank.optimistic.lock.conflicts} counter so real contention is visible
 * in Prometheus instead of being masked by the use-case retry aspect.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BusinessProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(BusinessProblemHandler.class);

    private final ProblemMessageResolver messages;
    private final MeterRegistry meterRegistry;

    public BusinessProblemHandler(ProblemMessageResolver messages,
            @Autowired(required = false) @Nullable MeterRegistry meterRegistry) {
        this.messages = messages;
        // Nullable like OutboxProcessor's registry: unit tests and non-metered
        // slices construct this handler without metrics.
        this.meterRegistry = meterRegistry;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex, WebRequest request) {
        HttpStatus status = BusinessErrorHttpMapper.toStatus(ex);
        return ProblemDetailFactory.create(status, ex.getErrorCode(), messages.resolveBusinessMessage(ex), request);
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ProblemDetail> handleAuthorizationException(AuthorizationException ex, WebRequest request) {
        return ProblemDetailFactory.create(ErrorCode.ACCESS_DENIED, messages.resolveBusinessMessage(ex), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailureException(OptimisticLockingFailureException ex, WebRequest request) {
        if (meterRegistry != null) {
            meterRegistry.counter("bank.optimistic.lock.conflicts",
                    "exception", ex.getClass().getSimpleName()).increment();
        } else {
            log.trace("Optimistic lock conflict (unmetered): {}", ex.getClass().getSimpleName());
        }
        String message = messages.resolveMessage("error.optimistic_lock_conflict");
        return ProblemDetailFactory.create(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, message, request);
    }

    @ExceptionHandler(ConcurrentRequestException.class)
    public ResponseEntity<ProblemDetail> handleConcurrentRequestException(ConcurrentRequestException ex, WebRequest request) {
        return ProblemDetailFactory.create(HttpStatus.CONFLICT, ex.getErrorCode(), messages.resolveBusinessMessage(ex), request);
    }
}
