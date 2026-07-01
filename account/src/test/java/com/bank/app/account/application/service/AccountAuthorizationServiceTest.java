package com.bank.app.account.application.service;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import com.bank.app.common.domain.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountAuthorizationService")
class AccountAuthorizationServiceTest {

    @Mock
    private UserContextService userContextService;

    private AccountAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new AccountAuthorizationService(userContextService);
    }

    @Nested
    @DisplayName("authorizeAccountOwner")
    class AuthorizeAccountOwner {

        @Test
        @DisplayName("should authorize when user owns the account")
        void shouldAuthorizeWhenOwner() {
            Account account = new Account(1L, new UserId(100L), new Iban("TR111111111111111111111111"),
                    "Owner", Money.of("1000", Currency.TRY), AccountStatus.ACTIVE);

            authorizationService.authorizeAccountOwner(account, "Not authorized");

            verify(userContextService).checkUserAuthorization(100L, "Not authorized");
        }
    }

    @Nested
    @DisplayName("authorizeUserAction")
    class AuthorizeUserAction {

        @Test
        @DisplayName("should authorize when user matches expected userId")
        void shouldAuthorizeWhenUserMatches() {
            authorizationService.authorizeUserAction(42L, "Access denied");

            verify(userContextService).checkUserAuthorization(42L, "Access denied");
        }
    }

    @Nested
    @DisplayName("getCurrentUserId")
    class GetCurrentUserId {

        @Test
        @DisplayName("should return current user id when present")
        void shouldReturnUserIdWhenPresent() {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.of(42L));

            Long result = authorizationService.getCurrentUserId();

            assertThat(result).isEqualTo(42L);
        }

        @Test
        @DisplayName("should throw AuthorizationException when no user is logged in")
        void shouldThrowWhenNoUser() {
            when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authorizationService.getCurrentUserId())
                    .isExactlyInstanceOf(AuthorizationException.class)
                    .hasMessage("You must be logged in to perform this action.");
        }
    }
}
