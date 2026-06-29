package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.account.application.port.in.GetAccountByIdQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAccountByIdQueryHandler")
@SuppressWarnings("null")
class GetAccountByIdQueryHandlerTest {

    @Mock
    private LoadAccountPort loadAccountPort;
    @Mock
    private AccountAuthorizationService accountAuthorizationService;

    private GetAccountByIdQuery query;

    private static final Long ACCOUNT_ID = 1L;
    private static final String VALID_IBAN = "TR290006200000000000000123";

    @BeforeEach
    void setUp() {
        query = new GetAccountByIdQueryHandler(loadAccountPort, accountAuthorizationService);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should get account by ID successfully")
        void shouldGetAccountByIdSuccessfully() {
            Account account = new Account(ACCOUNT_ID, new UserId(100L), new Iban(VALID_IBAN), "Ali Veli",
                    Money.of(new BigDecimal("500.00"), Currency.TRY), AccountStatus.ACTIVE);
            when(loadAccountPort.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            doNothing().when(accountAuthorizationService).authorizeAccountOwner(any(), anyString());

            AccountResponse response = query.execute(ACCOUNT_ID);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.ownerName()).isEqualTo("Ali Veli");
            assertThat(response.iban()).isEqualTo(VALID_IBAN);
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw AccountNotFoundException when account not found")
        void shouldThrowAccountNotFoundExceptionWhenNotFound() {
            when(loadAccountPort.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> query.execute(99L))
                    .isExactlyInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("should throw AuthorizationException when not authorized")
        void shouldThrowAuthorizationExceptionWhenNotAuthorized() {
            Account account = new Account(ACCOUNT_ID, new UserId(100L), new Iban(VALID_IBAN), "Ali Veli",
                    Money.of(new BigDecimal("500.00"), Currency.TRY), AccountStatus.ACTIVE);
            when(loadAccountPort.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            doThrow(new AuthorizationException("Yetki yok")).when(accountAuthorizationService)
                    .authorizeAccountOwner(any(), anyString());

            assertThatThrownBy(() -> query.execute(ACCOUNT_ID))
                    .isExactlyInstanceOf(AuthorizationException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when ID is null")
        void shouldThrowNullPointerExceptionWhenIdIsNull() {
            assertThatThrownBy(() -> query.execute(null))
                    .isExactlyInstanceOf(NullPointerException.class);
        }
    }
}
