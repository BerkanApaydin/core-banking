package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAccountsByUserQueryHandler")
@SuppressWarnings("null")
class GetAccountsByUserQueryHandlerTest {

    @Mock
    private LoadAccountPort loadAccountPort;
    @Mock
    private AccountAuthorizationService accountAuthorizationService;

    private GetAccountsByUserQuery query;

    @BeforeEach
    void setUp() {
        query = new GetAccountsByUserQueryHandler(loadAccountPort, accountAuthorizationService);
    }

    private Account account(Long id, Long userId, String iban, BigDecimal balance, AccountStatus status) {
        return new Account(id, new UserId(userId), new Iban(iban), "Owner" + id, Money.of(balance, Currency.TRY), status);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should return accounts for current user")
        void shouldReturnAccountsForCurrentUser() {
            Account account1 = account(1L, 100L, "TR290006200000000000000111", new BigDecimal("500.00"), AccountStatus.ACTIVE);
            Account account2 = account(2L, 100L, "TR290006200000000000000222", new BigDecimal("300.00"), AccountStatus.ACTIVE);

            when(accountAuthorizationService.getCurrentUserId()).thenReturn(100L);
            when(loadAccountPort.findByUserId(100L)).thenReturn(List.of(account1, account2));

            List<AccountResponse> responses = query.execute(0, 20);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).iban()).isEqualTo("TR290006200000000000000111");
            assertThat(responses.get(1).iban()).isEqualTo("TR290006200000000000000222");
        }

        @Test
        @DisplayName("should return empty list when user has no accounts")
        void shouldReturnEmptyListWhenNoAccounts() {
            when(accountAuthorizationService.getCurrentUserId()).thenReturn(100L);
            when(loadAccountPort.findByUserId(100L)).thenReturn(List.of());

            List<AccountResponse> responses = query.execute(0, 20);

            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("authorization")
    class Authorization {

        @Test
        @DisplayName("should throw AuthorizationException when user is not logged in")
        void shouldThrowWhenNotLoggedIn() {
            when(accountAuthorizationService.getCurrentUserId()).thenThrow(new AuthorizationException("Bu işlem için giriş yapmalısınız."));

            assertThatThrownBy(() -> query.execute(0, 20))
                    .isExactlyInstanceOf(AuthorizationException.class)
                    .hasMessage("Bu işlem için giriş yapmalısınız.");
            verifyNoInteractions(loadAccountPort);
        }
    }
}
