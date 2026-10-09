package com.bank.app.infrastructure.adapter.in.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "unchecked"})
class RequestProblemHandlerTest {

    private RequestProblemHandler handler;

    @Mock
    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        handler = new RequestProblemHandler(new ProblemMessageResolver(messageSource));
    }

    @Test
    void shouldHandleValidationExceptions() {
        BindingResult bindingResult = new MapBindingResult(new HashMap<>(), "request");
        bindingResult.addError(new FieldError("request", "amount", "Amount must be positive"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ProblemDetail> response = handler.handleValidationExceptions(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_FAILED", response.getBody().getProperties().get("code"));
        Map<String, String> errors = (Map<String, String>) response.getBody().getProperties().get("errors");
        assertNotNull(errors);
        assertEquals("Amount must be positive", errors.get("amount"));
    }

    @Test
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");
        when(messageSource.getMessage(eq("error.invalid_argument"), any(), any(Locale.class)))
                .thenReturn("Invalid request argument.");

        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgumentException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_ARGUMENT", response.getBody().getProperties().get("code"));
        // Raw exception detail must not leak to clients.
        assertEquals("Invalid request argument.", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldMapNumberFormatExceptionToBadRequest() {
        // Money.of(String) parses with new BigDecimal(String): malformed
        // amounts surface as NumberFormatException (an IllegalArgumentException),
        // which must stay a 400 validation failure — never the 500 fallback.
        NumberFormatException ex = new NumberFormatException("Character a is neither a decimal digit number");
        when(messageSource.getMessage(eq("error.invalid_argument"), any(), any(Locale.class)))
                .thenReturn("Invalid request argument.");

        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgumentException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_ARGUMENT", response.getBody().getProperties().get("code"));
    }

    @Test
    void shouldMapArithmeticExceptionToBadRequest() {
        // Safety net for numeric paths that bypass the Money factories
        // (BigDecimal UNNECESSARY rounding, divide): a decimal shape failure
        // is client input error (400), never the 500 fallback.
        ArithmeticException ex = new ArithmeticException("Rounding necessary");
        when(messageSource.getMessage(eq("error.invalid_argument"), any(), any(Locale.class)))
                .thenReturn("Invalid request argument.");

        ResponseEntity<ProblemDetail> response = handler.handleArithmeticException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_ARGUMENT", response.getBody().getProperties().get("code"));
    }

    @SuppressWarnings("unused")
    static void dummyEndpoint(int size) {
    }

    private static HandlerMethodValidationException methodValidationFailure() throws Exception {
        MethodParameter param = new MethodParameter(
                RequestProblemHandlerTest.class.getDeclaredMethod("dummyEndpoint", int.class), 0);
        param.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        FieldError fieldError = new FieldError("size", "size", 200, false, null, null,
                "must be less than or equal to 100");
        ParameterValidationResult result = new ParameterValidationResult(param, 200, List.of(fieldError),
                null, null, null, (resolvable, type) -> null);
        MethodValidationResult validationResult = mock(MethodValidationResult.class);
        when(validationResult.getParameterValidationResults()).thenReturn(List.of(result));
        return new HandlerMethodValidationException(validationResult);
    }

    @Test
    void shouldReportParameterFieldOnMethodValidation() throws Exception {
        ResponseEntity<ProblemDetail> response = handler.handleMethodValidation(methodValidationFailure(), null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_FAILED", response.getBody().getProperties().get("code"));
        @SuppressWarnings("unchecked")
        Map<String, String> errors =
                (Map<String, String>) response.getBody().getProperties().get("errors");
        assertNotNull(errors);
        assertEquals("must be less than or equal to 100", errors.get("size.size"));
    }

    @Test
    void shouldFallBackToRequestKeyWhenMethodValidationHasNoResults() {
        MethodValidationResult empty = mock(MethodValidationResult.class);
        when(empty.getParameterValidationResults()).thenReturn(List.of());

        ResponseEntity<ProblemDetail> response =
                handler.handleMethodValidation(new HandlerMethodValidationException(empty), null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> errors =
                (Map<String, String>) response.getBody().getProperties().get("errors");
        assertNotNull(errors);
        assertEquals("Invalid request parameters", errors.get("request"));
    }

    @Test
    void shouldFallBackToGenericMessageWhenBundleKeyIsMissing() {
        IllegalArgumentException ex = new IllegalArgumentException("SELECT * FROM users");
        when(messageSource.getMessage(eq("error.invalid_argument"), any(), any(Locale.class)))
                .thenThrow(new NoSuchMessageException("error.invalid_argument"));

        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgumentException(ex, null);

        assertNotNull(response.getBody());
        assertEquals("Invalid request argument.", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldFallBackToGenericMessageWhenBundleReturnsKeyItself() {
        IllegalArgumentException ex = new IllegalArgumentException("internal detail");
        when(messageSource.getMessage(eq("error.invalid_argument"), any(), any(Locale.class)))
                .thenReturn("error.invalid_argument");

        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgumentException(ex, null);

        assertNotNull(response.getBody());
        assertEquals("Invalid request argument.", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldFallBackToGenericMessageWhenBundleReturnsEmpty() {
        IllegalArgumentException ex = new IllegalArgumentException("internal detail");
        when(messageSource.getMessage(eq("error.invalid_argument"), any(), any(Locale.class)))
                .thenReturn("");

        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgumentException(ex, null);

        assertNotNull(response.getBody());
        assertEquals("Invalid request argument.", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldReturn401ForMissingAuthHeader() {
        MissingRequestHeaderException ex =
                new MissingRequestHeaderException("Authorization", mock(MethodParameter.class));
        when(messageSource.getMessage(eq("error.authentication_failed"), any(), any(Locale.class)))
                .thenReturn("Authentication failed.");

        ResponseEntity<ProblemDetail> response = handler.handleMissingRequestHeader(ex, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AUTHENTICATION_FAILED", response.getBody().getProperties().get("code"));
        assertEquals("Authentication failed.", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldReturn400ForMissingRequestParameter() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("page", "int");

        ResponseEntity<ProblemDetail> response = handler.handleMissingRequestParameter(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_FAILED", response.getBody().getProperties().get("code"));
    }

    @Test
    void shouldReturn400ForTypeMismatch() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", mock(MethodParameter.class), null);

        ResponseEntity<ProblemDetail> response = handler.handleArgumentTypeMismatch(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        Map<String, String> errors = (Map<String, String>) response.getBody().getProperties().get("errors");
        assertNotNull(errors);
        assertEquals("Invalid parameter value", errors.get("id"));
    }

    @Test
    void shouldHandleHttpMessageNotReadableException() {
        HttpInputMessage httpInputMessage = mock(HttpInputMessage.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", null, httpInputMessage);
        when(messageSource.getMessage(eq("error.invalid_format"), isNull(), any(Locale.class)))
                .thenReturn("Invalid format");

        ResponseEntity<ProblemDetail> response = handler.handleHttpMessageNotReadableException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_FORMAT", response.getBody().getProperties().get("code"));
        assertEquals("Invalid format", response.getBody().getProperties().get("message"));
    }

    enum DummyEnum { VALUE1, VALUE2 }

    @Test
    void shouldHandleHttpMessageNotReadableExceptionWithEnumCause() {
        InvalidFormatException cause = new InvalidFormatException(null, "Invalid value", "VALUE3", DummyEnum.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", cause, null);
        when(messageSource.getMessage(eq("error.invalid_enum_value"), any(), any(Locale.class)))
                .thenReturn("Invalid value for this field. Accepted values: [VALUE1, VALUE2]");
        when(messageSource.getMessage(eq("error.invalid_format"), isNull(), any(Locale.class)))
                .thenReturn("Invalid format");

        ResponseEntity<ProblemDetail> response = handler.handleHttpMessageNotReadableException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_ENUM_VALUE", response.getBody().getProperties().get("code"));
        String message = (String) response.getBody().getProperties().get("message");
        assertTrue(message.contains("[VALUE1, VALUE2]"));
        assertFalse(message.contains("VALUE3"));
    }

    @Test
    void shouldHandleHttpMessageNotReadableExceptionWithNonEnumCause() {
        InvalidFormatException cause = new InvalidFormatException(null, "Invalid value", "123", Integer.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", cause, null);
        when(messageSource.getMessage(eq("error.invalid_format"), isNull(), any(Locale.class)))
                .thenReturn("Invalid format");

        ResponseEntity<ProblemDetail> response = handler.handleHttpMessageNotReadableException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_FORMAT", response.getBody().getProperties().get("code"));
        assertEquals("Invalid format", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleHttpMessageNotReadableExceptionWithNullTargetTypeCause() {
        InvalidFormatException cause = new InvalidFormatException(null, "Invalid value", "123", null);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Not readable", cause, null);
        when(messageSource.getMessage(eq("error.invalid_format"), isNull(), any(Locale.class)))
                .thenReturn("Invalid format");

        ResponseEntity<ProblemDetail> response = handler.handleHttpMessageNotReadableException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_FORMAT", response.getBody().getProperties().get("code"));
        assertEquals("Invalid format", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldReturn405WhenHttpRequestMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "POST"));

        ResponseEntity<ProblemDetail> response = handler.handleMethodNotSupportedException(ex, null);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().getProperties().get("code"));
        // D12/K13: fixed catalog message — the request method must not be echoed.
        String message = (String) response.getBody().getProperties().get("message");
        assertEquals("Request method is not supported for this endpoint.", message);
        assertFalse(message.contains("PATCH"));
    }

    @Test
    void shouldResolveMethodNotAllowedFromCatalogWhenPresent() {
        when(messageSource.getMessage(eq("error.method_not_allowed"), isNull(), any(Locale.class)))
                .thenReturn("Catalog message.");
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "POST"));

        ResponseEntity<ProblemDetail> response = handler.handleMethodNotSupportedException(ex, null);

        assertEquals("Catalog message.", response.getBody().getProperties().get("message"));
    }

    @Test
    void shouldHandleHttpRequestMethodNotSupportedWithNullSupportedMethods() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE", Collections.emptyList());

        ResponseEntity<ProblemDetail> response = handler.handleMethodNotSupportedException(ex, null);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().getProperties().get("code"));
    }

    @Test
    void shouldHandleNoResourceFoundException() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/resource");

        ResponseEntity<ProblemDetail> response = handler.handleNoResourceFoundException(ex, null);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().getProperties().get("code"));
        // D12/K13: fixed catalog message — the request path must not be reflected.
        String message = (String) response.getBody().getProperties().get("message");
        assertEquals("The requested resource was not found.", message);
        assertFalse(message.contains("/api/resource"));
    }

    @Test
    void shouldHandleHttpMediaTypeNotSupportedException() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON));
        when(messageSource.getMessage(eq("error.unsupported_media_type"), any(), any(Locale.class)))
                .thenReturn("Unsupported media type: application/xml");

        ResponseEntity<ProblemDetail> response = handler.handleMediaTypeNotSupportedException(ex, null);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().getProperties().get("code"));
        assertTrue(((String) response.getBody().getProperties().get("message")).contains("Unsupported media type"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldHandleJakartaConstraintViolationAsBadRequest() {
        ConstraintViolation violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("getHistory.size");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be less than or equal to 100");
        Set violations = new HashSet(List.of(violation));
        ConstraintViolationException ex =
                new ConstraintViolationException("validation failed", violations);

        ResponseEntity<ProblemDetail> response = handler.handleConstraintViolationException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_FAILED", response.getBody().getProperties().get("code"));
    }
}
