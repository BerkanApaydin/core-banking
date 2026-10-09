package com.bank.app.common.application.service;

import com.bank.app.common.domain.exception.AuthorizationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceOwnershipPolicyTest {

    @Mock
    private UserContextService userContextService;

    private ResourceOwnershipPolicy policy() {
        return new ResourceOwnershipPolicy(userContextService);
    }

    @Test
    void delegatesOwnershipCheck() {
        policy().requireOwner(7L, "nope");

        verify(userContextService).checkUserAuthorization(7L, "nope");
    }

    @Test
    void returnsCurrentUserWhenPresent() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(9L));

        assertEquals(9L, policy().currentUserIdOrThrow(
                new AuthorizationException("error.login_required", null, "login")));
    }

    @Test
    void throwsCallerSuppliedFailureWhenAnonymous() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());
        AuthorizationException failure =
                new AuthorizationException("error.custom", null, "custom");

        assertEquals(failure, assertThrows(AuthorizationException.class,
                () -> policy().currentUserIdOrThrow(failure)));
    }

    @Test
    void acceptsEitherParticipant() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(2L));
        AuthorizationException unauthenticated =
                new AuthorizationException("error.session_not_found", null, "login");
        AuthorizationException notParticipant =
                new AuthorizationException("error.not_resource_owner", null, "denied");

        policy().requireParticipant(1L, 2L, unauthenticated, notParticipant);
    }

    @Test
    void rejectsOutsider() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(3L));
        AuthorizationException notParticipant =
                new AuthorizationException("error.not_resource_owner", null, "denied");

        assertEquals(notParticipant, assertThrows(AuthorizationException.class, () ->
                policy().requireParticipant(1L, 2L,
                        new AuthorizationException("error.session_not_found", null, "login"),
                        notParticipant)));
    }

    @Test
    void delegatesUsernameFallback() {
        when(userContextService.getCurrentUsernameOrSystem()).thenReturn("system");

        assertEquals("system", policy().currentUsernameOrSystem());
    }
}
