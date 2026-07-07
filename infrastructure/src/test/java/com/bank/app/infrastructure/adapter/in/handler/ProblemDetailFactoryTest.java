package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.domain.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("ProblemDetailFactory")
class ProblemDetailFactoryTest {

    @Nested
    @DisplayName("create with ErrorCode")
    class CreateWithErrorCode {

        @Test
        @DisplayName("should create problem detail with valid ErrorCode")
        void shouldCreateWithValidErrorCode() {
            ResponseEntity<ProblemDetail> response = ProblemDetailFactory.create(
                    ErrorCode.RESOURCE_NOT_FOUND, "Resource not found", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getTitle()).isEqualTo("Not Found");
            assertThat(response.getBody().getProperties())
                    .containsEntry("code", "RESOURCE_NOT_FOUND")
                    .containsEntry("message", "Resource not found")
                    .containsKey("timestamp");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should use 500 when ErrorCode has unresolvable http status")
        void shouldUse500WhenErrorCodeHasUnresolvableStatus() {
            ErrorCode unknownCode = mock(ErrorCode.class);
            when(unknownCode.getHttpStatus()).thenReturn(999);
            when(unknownCode.code()).thenReturn("UNKNOWN");
            ResponseEntity<ProblemDetail> response = ProblemDetailFactory.create(
                    unknownCode, "Something went wrong", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should set instance from WebRequest when provided")
        void shouldSetInstanceFromWebRequest() {
            WebRequest request = mock(WebRequest.class);
            when(request.getDescription(false)).thenReturn("uri=/api/test");

            ResponseEntity<ProblemDetail> response = ProblemDetailFactory.create(
                    ErrorCode.RESOURCE_NOT_FOUND, "Not found", request);

            assertThat(response.getBody().getInstance()).isEqualTo(URI.create("/api/test"));
        }

        @Test
        @DisplayName("should handle null request gracefully")
        void shouldHandleNullRequest() {
            ResponseEntity<ProblemDetail> response = ProblemDetailFactory.create(
                    ErrorCode.RESOURCE_NOT_FOUND, "Not found", null);

            assertThat(response.getBody().getInstance()).isNull();
        }
    }

    @Nested
    @DisplayName("create with HttpStatus")
    class CreateWithHttpStatus {

        @Test
        @DisplayName("should create problem detail with explicit HttpStatus")
        void shouldCreateWithExplicitStatus() {
            ResponseEntity<ProblemDetail> response = ProblemDetailFactory.create(
                    HttpStatus.CONFLICT, "CONCURRENT", "Concurrent request", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().getProperties())
                    .containsEntry("code", "CONCURRENT")
                    .containsEntry("message", "Concurrent request");
        }
    }

    @Nested
    @DisplayName("createValidationError")
    class CreateValidationError {

        @Test
        @DisplayName("should create validation error problem detail")
        void shouldCreateValidationError() {
            Map<String, String> errors = Map.of("field1", "must not be null");
            ResponseEntity<ProblemDetail> response = ProblemDetailFactory.createValidationError(errors, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getTitle()).isEqualTo("Validation Failed");
            assertThat(response.getBody().getProperties())
                    .containsEntry("code", "VALIDATION_FAILED")
                    .containsEntry("message", "Validation failed")
                    .containsKey("timestamp");
            assertThat(response.getBody().getProperties().get("errors"))
                    .isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            var resultErrors = (Map<String, String>) response.getBody().getProperties().get("errors");
            assertThat(resultErrors).containsEntry("field1", "must not be null");
        }

        @Test
        @DisplayName("should set instance URI when WebRequest is provided")
        void shouldSetInstanceFromWebRequest() {
            WebRequest request = mock(WebRequest.class);
            when(request.getDescription(false)).thenReturn("uri=/api/test/validation");

            Map<String, String> errors = Map.of("name", "required");
            ResponseEntity<ProblemDetail> response = ProblemDetailFactory.createValidationError(errors, request);

            assertThat(response.getBody().getInstance()).isEqualTo(URI.create("/api/test/validation"));
        }
    }
}
