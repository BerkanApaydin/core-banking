package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.adapter.in.idempotency.Idempotent;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyRequestResolverPathTest {

    @Mock
    private UserContextService userContextService;

    @Mock
    private ClientIpResolverPort clientIpResolver;

    private IdempotencyRequestResolver resolver;
    private Idempotent idempotent;

    @Idempotent(required = true)
    void annotatedEndpoint() {
    }

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        resolver = new IdempotencyRequestResolver(userContextService, clientIpResolver, new ObjectMapper());
        Method method = getClass().getDeclaredMethod("annotatedEndpoint");
        idempotent = method.getAnnotation(Idempotent.class);
        when(userContextService.getCurrentUsername()).thenReturn(Optional.of("alice"));
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
    }

    private MockHttpServletRequest post(String uri, String contextPath) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContextPath(contextPath);
        request.setRequestURI(uri);
        request.addHeader("Idempotency-Key", "key-123");
        return request;
    }

    @Test
    void shouldDeriveSameKeyForPercentEncodedAlias() throws Exception {
        // %74 == 't': Spring MVC routes both to the transfer endpoint, so both
        // must de-duplicate against the same idempotency record. Before path
        // normalization each encoding derived its own key and the same logical
        // operation could execute twice.
        IdempotencyRequestResolver.RequestIdentity plain =
                resolver.resolve(post("/api/v1/transfers", ""), idempotent, new Object[]{"body"});
        IdempotencyRequestResolver.RequestIdentity encoded =
                resolver.resolve(post("/api/v1/%74ransfers", ""), idempotent, new Object[]{"body"});

        assertEquals(plain.key(), encoded.key());
        assertEquals(plain.requestHash(), encoded.requestHash());
    }

    @Test
    void shouldDeriveSameKeyUnderContextPath() throws Exception {
        IdempotencyRequestResolver.RequestIdentity plain =
                resolver.resolve(post("/api/v1/transfers", ""), idempotent, new Object[]{"body"});
        IdempotencyRequestResolver.RequestIdentity deployed =
                resolver.resolve(post("/bank/api/v1/transfers", "/bank"), idempotent, new Object[]{"body"});

        assertEquals(plain.key(), deployed.key());
        assertEquals(plain.requestHash(), deployed.requestHash());
    }

    @Test
    void shouldDeriveDifferentKeysForDifferentEndpoints() throws Exception {
        IdempotencyRequestResolver.RequestIdentity transfers =
                resolver.resolve(post("/api/v1/transfers", ""), idempotent, new Object[]{"body"});
        IdempotencyRequestResolver.RequestIdentity accounts =
                resolver.resolve(post("/api/v1/accounts", ""), idempotent, new Object[]{"body"});

        assertNotEquals(transfers.key(), accounts.key());
    }
}
