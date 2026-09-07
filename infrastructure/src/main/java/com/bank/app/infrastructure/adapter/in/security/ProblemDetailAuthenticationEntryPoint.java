package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.common.domain.exception.ErrorCode;
import com.bank.app.infrastructure.adapter.in.handler.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renders unauthenticated (401) responses as RFC 7807 problem details so the security
 * layer uses the same error contract as {@code GlobalExceptionHandler}.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        String message = messageSource.getMessage("error.unauthorized", null,
                "Unauthorized", LocaleContextHolder.getLocale());
        ProblemDetailFactory.writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTHENTICATION_FAILED.code(), message, request.getRequestURI());
    }
}
