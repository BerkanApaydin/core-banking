package com.bank.app.infrastructure.adapter.in.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.hibernate.exception.ConstraintViolationException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "unchecked"})
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(new ProblemMessageResolver(messageSource));
    }

    @Test
    void shouldHandleDataIntegrityViolationException() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Violation");
        when(messageSource.getMessage(eq("error.db_integrity_violation"), isNull(), any(Locale.class)))
                .thenReturn("DB integrity violation");

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrityViolationException(ex, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DB_INTEGRITY_VIOLATION", response.getBody().getProperties().get("code"));
        assertEquals("DB integrity violation", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleDataIntegrityViolationExceptionWithConstraintCause() {
        ConstraintViolationException cause = new ConstraintViolationException(
                "Constraint fail", new SQLException("duplicate", "23505"), "uk_name");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Violation", cause);
        when(messageSource.getMessage(eq("error.unique_constraint_violation"), isNull(), any(Locale.class)))
                .thenReturn("Unique constraint violation");

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrityViolationException(ex, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNIQUE_CONSTRAINT_VIOLATION", response.getBody().getProperties().get("code"));
        assertEquals("Unique constraint violation", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldNotClassifyForeignKeyOrCheckConstraintAsUnique() {
        when(messageSource.getMessage(eq("error.db_integrity_violation"), isNull(), any(Locale.class)))
                .thenReturn("DB integrity violation");
        for (String sqlState : List.of("23503", "23514")) {
            ConstraintViolationException cause = new ConstraintViolationException(
                    "Constraint fail", new SQLException("constraint", sqlState), "constraint_name");
            ResponseEntity<ProblemDetail> response = handler.handleDataIntegrityViolationException(
                    new DataIntegrityViolationException("Violation", cause), null);
            assertNotNull(response.getBody());
            assertEquals("DB_INTEGRITY_VIOLATION", response.getBody().getProperties().get("code"));
        }
    }

    @Test
    void shouldHandleGeneralException() {
        Exception ex = new Exception("General error");

        ResponseEntity<ProblemDetail> response = handler.handleGeneralException(ex, null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("GENERAL_INTERNAL_ERROR", response.getBody().getProperties().get("code"));
    }
}
