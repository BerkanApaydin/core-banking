package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.domain.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.sql.SQLException;

/**
 * Last-resort problems: persistence integrity violations and the uncaught
 * {@code Exception} fallback. Domain, security and malformed-request failures
 * live in {@link BusinessProblemHandler}, {@link SecurityProblemHandler} and
 * {@link RequestProblemHandler} — this class stays small on purpose so the
 * 500 path is trivially auditable. Ordered last: the {@code Exception}
 * fallback must never shadow the specific handlers above, and advice
 * resolution consults matching advices in order.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemMessageResolver messages;

    public GlobalExceptionHandler(ProblemMessageResolver messages) {
        this.messages = messages;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolationException(DataIntegrityViolationException ex, WebRequest request) {
        String message;
        ErrorCode code;
        if (isUniqueViolation(ex)) {
            message = messages.resolveMessage("error.unique_constraint_violation");
            code = ErrorCode.UNIQUE_CONSTRAINT_VIOLATION;
        } else {
            message = messages.resolveMessage("error.db_integrity_violation");
            code = ErrorCode.DB_INTEGRITY_VIOLATION;
        }
        return ProblemDetailFactory.create(code, message, request);
    }

    private static boolean isUniqueViolation(Throwable failure) {
        for (Throwable current = failure; current != null && current != current.getCause(); current = current.getCause()) {
            if (current instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred: ", ex);
        String message = messages.resolveMessage("error.general_internal_error");
        return ProblemDetailFactory.create(ErrorCode.GENERAL_INTERNAL_ERROR, message, request);
    }
}
