package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.adapter.in.idempotency.Idempotent;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyRequestResolverTest {

    @Mock
    private UserContextService userContextService;

    @Mock
    private ClientIpResolverPort clientIpResolver;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpServletRequest request;

    private IdempotencyRequestResolver resolver;
    private Idempotent idempotent;

    @BeforeEach
    void setUp() {
        resolver = new IdempotencyRequestResolver(userContextService, clientIpResolver, objectMapper);
        idempotent = mock(Idempotent.class);
        when(idempotent.headerName()).thenReturn("Idempotency-Key");
        when(idempotent.publicEndpoint()).thenReturn(false);
        when(idempotent.required()).thenReturn(false);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/transfers");
        when(request.getContextPath()).thenReturn("");
        when(userContextService.getCurrentUsername()).thenReturn(Optional.of("user"));
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(42L));
    }

    @Test
    void shouldAcceptKeyAtTheExactMaximumLength() throws Exception {
        String key = "a".repeat(128);
        when(request.getHeader("Idempotency-Key")).thenReturn(key);
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[0]);

        assertThat(resolver.resolve(request, idempotent, null).key()).isNotBlank();
    }

    @Test
    void shouldRejectKeyOneCharacterOverTheMaximum() {
        when(request.getHeader("Idempotency-Key")).thenReturn("a".repeat(129));

        assertThatThrownBy(() -> resolver.resolve(request, idempotent, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHashNullArgumentsAsAnEmptyArray() throws Exception {
        when(request.getHeader("Idempotency-Key")).thenReturn("abc");
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[0]);

        resolver.resolve(request, idempotent, null);

        verify(objectMapper).writeValueAsBytes(argThat(
                value -> value instanceof Object[] array && array.length == 0));
    }

    @Test
    void shouldRejectAuthenticatedRequestWithoutUserId() {
        when(request.getHeader("Idempotency-Key")).thenReturn("abc");
        when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(request, idempotent, null))
                .isInstanceOf(AuthorizationException.class);
    }
}
