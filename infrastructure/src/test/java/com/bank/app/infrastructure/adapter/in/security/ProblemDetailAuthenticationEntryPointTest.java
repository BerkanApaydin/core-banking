package com.bank.app.infrastructure.adapter.in.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemDetailAuthenticationEntryPointTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private HttpServletRequest request;

    @Test
    void shouldWriteProblemJsonWithUnauthorizedStatus() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/accounts");
        when(messageSource.getMessage(eq("error.unauthorized"), isNull(), anyString(), any(Locale.class)))
                .thenReturn("Unauthorized");
        ProblemDetailAuthenticationEntryPoint entryPoint =
                new ProblemDetailAuthenticationEntryPoint(new ObjectMapper(), messageSource);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad credentials"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().contains("application/problem+json"));
        String body = response.getContentAsString();
        assertTrue(body.contains("AUTHENTICATION_FAILED"));
        assertTrue(body.contains("Unauthorized"));
        assertTrue(body.contains("/api/v1/accounts"));
    }
}
