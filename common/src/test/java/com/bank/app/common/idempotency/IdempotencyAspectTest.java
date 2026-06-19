package com.bank.app.common.idempotency;

import com.bank.app.common.exception.ConcurrentRequestException;
import com.bank.app.common.security.port.out.SecurityContextPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.lang.reflect.Type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyAspectTest {

        @Mock
        private IdempotencyGuard idempotencyGuard;

        @Mock
        private SecurityContextPort securityContextPort;

        @Mock
        private ObjectMapper objectMapper;

        @Mock
        private ProceedingJoinPoint joinPoint;

        @Mock
        private MethodSignature methodSignature;

        @Mock
        private HttpServletRequest request;

        private IdempotencyAspect aspect;

        @Mock
        private ParameterizedType parameterizedType;

        @BeforeEach
        void setUp() {
                aspect = new IdempotencyAspect(
                                idempotencyGuard,
                                securityContextPort,
                                objectMapper);
        }

        @AfterEach
        void tearDown() {
                RequestContextHolder.resetRequestAttributes();
        }

        @Idempotent
        public ResponseEntity<TestResponse> parameterizedMethod() {
                return ResponseEntity.ok(new TestResponse("ok"));
        }

        @SuppressWarnings("rawtypes")
        public ResponseEntity rawMethod() {
                return ResponseEntity.ok().build();
        }

        private void mockRequest(String header) {
                RequestContextHolder.setRequestAttributes(
                                new ServletRequestAttributes(request));

                when(request.getHeader("Idempotency-Key"))
                                .thenReturn(header);
        }

        private void mockMethod(Method method) {
                when(joinPoint.getSignature()).thenReturn(methodSignature);
                when(methodSignature.getMethod()).thenReturn(method);
        }

        private Idempotent annotation() {
                return new Idempotent() {
                        @Override
                        public String headerName() {
                                return "Idempotency-Key";
                        }

                        @Override
                        public Class<? extends Annotation> annotationType() {
                                return Idempotent.class;
                        }
                };
        }

        @Test
        void shouldProceedWhenRequestContextMissing() throws Throwable {

                when(joinPoint.proceed()).thenReturn("OK");

                Object result = aspect.handleIdempotency(joinPoint, annotation());

                assertEquals("OK", result);
                verify(joinPoint).proceed();
        }

        @Test
        void shouldProceedWhenHeaderNull() throws Throwable {

                mockRequest(null);

                when(joinPoint.proceed()).thenReturn("OK");

                Object result = aspect.handleIdempotency(joinPoint, annotation());

                assertEquals("OK", result);
        }

        @Test
        void shouldProceedWhenHeaderBlank() throws Throwable {

                mockRequest(" ");

                when(joinPoint.proceed()).thenReturn("OK");

                Object result = aspect.handleIdempotency(joinPoint, annotation());

                assertEquals("OK", result);
        }

        @Test
        void shouldThrowAccessDeniedException() {

                mockRequest("abc");

                when(securityContextPort.getCurrentUsername())
                                .thenReturn(Optional.empty());

                assertThrows(
                                AccessDeniedException.class,
                                () -> aspect.handleIdempotency(joinPoint, annotation()));
        }

        @Test
        void shouldReturnCompletedResponseWithCustomStatus() throws Throwable {

                mockRequest("abc");

                when(securityContextPort.getCurrentUsername())
                                .thenReturn(Optional.of("user"));

                mockMethod(
                                getClass().getMethod("parameterizedMethod"));

                when(idempotencyGuard.startRequest("user_abc"))
                                .thenReturn(
                                                IdempotencyGuard.IdempotencyResult.completed(
                                                                "{\"message\":\"cached\"}",
                                                                201));

                TestResponse body = new TestResponse("cached");

                when(objectMapper.readValue(
                                anyString(),
                                eq(TestResponse.class)))
                                .thenReturn(body);

                ResponseEntity<?> result = (ResponseEntity<?>) aspect.handleIdempotency(
                                joinPoint,
                                annotation());

                assertEquals(201, result.getStatusCode().value());
                assertEquals(body, result.getBody());
        }

        @Test
        void shouldReturnCompletedResponseWithDefaultStatus() throws Throwable {

                mockRequest("abc");

                when(securityContextPort.getCurrentUsername())
                                .thenReturn(Optional.of("user"));

                mockMethod(
                                getClass().getMethod("parameterizedMethod"));

                when(idempotencyGuard.startRequest("user_abc"))
                                .thenReturn(
                                                IdempotencyGuard.IdempotencyResult.completed(
                                                                "{}",
                                                                null));

                when(objectMapper.readValue(
                                anyString(),
                                eq(TestResponse.class)))
                                .thenReturn(new TestResponse("cached"));

                ResponseEntity<?> result = (ResponseEntity<?>) aspect.handleIdempotency(
                                joinPoint,
                                annotation());

                assertEquals(200, result.getStatusCode().value());
        }

        @Test
        void shouldThrowConcurrentRequestException() throws Exception {

                mockRequest("abc");

                when(securityContextPort.getCurrentUsername())
                                .thenReturn(Optional.of("user"));

                mockMethod(
                                getClass().getMethod("parameterizedMethod"));

                when(idempotencyGuard.startRequest("user_abc"))
                                .thenReturn(
                                                IdempotencyGuard.IdempotencyResult.pending());

                assertThrows(
                                ConcurrentRequestException.class,
                                () -> aspect.handleIdempotency(joinPoint, annotation()));
        }

        @Test
        void shouldCompleteRequestWhenResponseSuccessful() throws Throwable {

                mockRequest("abc");

                when(securityContextPort.getCurrentUsername())
                                .thenReturn(Optional.of("user"));

                mockMethod(
                                getClass().getMethod("parameterizedMethod"));

                when(idempotencyGuard.startRequest("user_abc"))
                                .thenReturn(
                                                IdempotencyGuard.IdempotencyResult.newRequest());

                TestResponse body = new TestResponse("success");

                when(joinPoint.proceed())
                                .thenReturn(ResponseEntity.ok(body));

                when(objectMapper.writeValueAsString(body))
                                .thenReturn("{\"message\":\"success\"}");

                Object result = aspect.handleIdempotency(joinPoint, annotation());

                verify(idempotencyGuard)
                                .completeRequest(
                                                eq("user_abc"),
                                                anyString(),
                                                eq(200));
                assertNotNull(result);
                assertInstanceOf(ResponseEntity.class, result);
                assertEquals(200, ((ResponseEntity<?>) result).getStatusCode().value());
        }

        @Test
        void shouldFailWhenBodyNull() throws Throwable {

                mockRequest("abc");

                when(securityContextPort.getCurrentUsername())
                                .thenReturn(Optional.of("user"));

                mockMethod(
                                getClass().getMethod("parameterizedMethod"));

                when(idempotencyGuard.startRequest("user_abc"))
                                .thenReturn(
                                                IdempotencyGuard.IdempotencyResult.newRequest());

                when(joinPoint.proceed())
                                .thenReturn(ResponseEntity.ok().build());

                Object result = aspect.handleIdempotency(joinPoint, annotation());

                verify(idempotencyGuard)
                                .failRequest("user_abc");
                assertNotNull(result);
                assertEquals(200, ((ResponseEntity<?>) result).getStatusCode().value());
        }

        @Test
        void shouldFailWhenStatusNotSuccessful() throws Throwable {

                mockRequest("abc");

                when(securityContextPort.getCurrentUsername())
                                .thenReturn(Optional.of("user"));

                mockMethod(
                                getClass().getMethod("parameterizedMethod"));

                when(idempotencyGuard.startRequest("user_abc"))
                                .thenReturn(
                                                IdempotencyGuard.IdempotencyResult.newRequest());

                when(joinPoint.proceed())
                                .thenReturn(ResponseEntity.badRequest().build());

                Object result = aspect.handleIdempotency(joinPoint, annotation());

                verify(idempotencyGuard)
                                .failRequest("user_abc");
                assertNotNull(result);
                assertEquals(400, ((ResponseEntity<?>) result).getStatusCode().value());
        }

        @Test
        void shouldFailWhenReturnTypeNotResponseEntity() throws Throwable {

                mockRequest("abc");

                when(securityContextPort.getCurrentUsername())
                                .thenReturn(Optional.of("user"));

                mockMethod(
                                getClass().getMethod("parameterizedMethod"));

                when(idempotencyGuard.startRequest("user_abc"))
                                .thenReturn(
                                                IdempotencyGuard.IdempotencyResult.newRequest());

                when(joinPoint.proceed())
                                .thenReturn("OK");

                Object result = aspect.handleIdempotency(joinPoint, annotation());

                verify(idempotencyGuard)
                                .failRequest("user_abc");
                assertEquals("OK", result);
        }

        @Test
        void shouldFailAndRethrowException() throws Throwable {

                mockRequest("abc");

                when(securityContextPort.getCurrentUsername())
                                .thenReturn(Optional.of("user"));

                mockMethod(
                                getClass().getMethod("parameterizedMethod"));

                when(idempotencyGuard.startRequest("user_abc"))
                                .thenReturn(
                                                IdempotencyGuard.IdempotencyResult.newRequest());

                when(joinPoint.proceed())
                                .thenThrow(new RuntimeException("boom"));

                assertThrows(
                                RuntimeException.class,
                                () -> aspect.handleIdempotency(joinPoint, annotation()));

                verify(idempotencyGuard)
                                .failRequest("user_abc");
        }

        @Test
        void shouldUseObjectClassForRawResponseEntity() throws Throwable {

                mockRequest("abc");

                when(securityContextPort.getCurrentUsername())
                                .thenReturn(Optional.of("user"));

                mockMethod(
                                getClass().getMethod("rawMethod"));

                when(idempotencyGuard.startRequest("user_abc"))
                                .thenReturn(
                                                IdempotencyGuard.IdempotencyResult.completed(
                                                                "{}",
                                                                200));

                when(objectMapper.readValue(
                                anyString(),
                                eq(Object.class)))
                                .thenReturn(new Object());

                aspect.handleIdempotency(joinPoint, annotation());

                verify(objectMapper)
                                .readValue(anyString(), eq(Object.class));
        }

        static class TestResponse {

                private String message;

                public TestResponse() {
                }

                public TestResponse(String message) {
                        this.message = message;
                }

                public String getMessage() {
                        return message;
                }

                public void setMessage(String message) {
                        this.message = message;
                }

                @Override
                public boolean equals(Object obj) {
                        if (this == obj) {
                                return true;
                        }

                        if (!(obj instanceof TestResponse other)) {
                                return false;
                        }

                        return Objects.equals(message, other.message);
                }

                @Override
                public int hashCode() {
                        return Objects.hash(message);
                }
        }

        @Test
        void shouldHandleParameterizedTypeThatIsNotClass() throws Throwable {

                mockRequest("abc");

                when(securityContextPort.getCurrentUsername())
                                .thenReturn(Optional.of("user"));

                mockMethod(
                                getClass().getMethod("nestedGenericMethod"));

                when(idempotencyGuard.startRequest("user_abc"))
                                .thenReturn(
                                                IdempotencyGuard.IdempotencyResult.completed(
                                                                "{}",
                                                                200));

                Object body = new Object();

                when(objectMapper.readValue(
                                anyString(),
                                eq(Object.class)))
                                .thenReturn(body);

                aspect.handleIdempotency(joinPoint, annotation());

                verify(objectMapper)
                                .readValue(anyString(), eq(Object.class));
        }

        private static class Wrapper<T> {
        }

        public ResponseEntity<Wrapper<String>> nestedGenericMethod() {
                return ResponseEntity.ok(new Wrapper<>());
        }

        @Test
        void shouldHandleEmptyTypeArguments() throws Throwable {

                mockRequest("abc");

                when(securityContextPort.getCurrentUsername())
                                .thenReturn(Optional.of("user"));

                mockMethod(
                                getClass().getMethod("parameterizedMethod"));

                when(idempotencyGuard.startRequest("user_abc"))
                                .thenReturn(
                                                IdempotencyGuard.IdempotencyResult.completed(
                                                                "{}",
                                                                200));

                ParameterizedType mockedType = mock(ParameterizedType.class);

                when(mockedType.getActualTypeArguments())
                                .thenReturn(new Type[0]);

                when(methodSignature.getMethod())
                                .thenReturn(mock(Method.class));

                Method mockedMethod = methodSignature.getMethod();

                when(mockedMethod.getGenericReturnType())
                                .thenReturn(mockedType);

                when(objectMapper.readValue(anyString(), eq(Object.class)))
                                .thenReturn(new Object());

                aspect.handleIdempotency(joinPoint, annotation());

                verify(objectMapper)
                                .readValue(anyString(), eq(Object.class));
        }

}
