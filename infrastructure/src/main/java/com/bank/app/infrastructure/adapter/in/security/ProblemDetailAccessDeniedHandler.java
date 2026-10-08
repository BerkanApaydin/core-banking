package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.common.domain.exception.ErrorCode;
import com.bank.app.infrastructure.adapter.in.handler.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renders filter-chain authorization failures (403, e.g. a non-admin hitting
 * {@code /api/v1/admin/**}) as RFC 7807 problem details with the same
 * {@code ACCESS_DENIED} shape as {@code SecurityProblemHandler}, so clients
 * keep a single error parser whether the denial happens at the URL boundary
 * (G-1 outer layer) or in the use case (inner layer).
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        String message = messageSource.getMessage("error.access_denied", null,
                "Access denied.", LocaleContextHolder.getLocale());
        ProblemDetailFactory.writeProblem(response, objectMapper, HttpStatus.FORBIDDEN,
                ErrorCode.ACCESS_DENIED.code(), message, request.getRequestURI());
    }
}
