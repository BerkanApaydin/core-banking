package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.account.application.port.in.GetAccountByIbanQuery;
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
@DisplayName("GetAccountByIbanQueryHandler")
@SuppressWarnings("null")
class GetAccountByIbanQueryHandlerTest {

    @Mock
    private LoadAccountPort loadAccountPort;
    @Mock
    private AccountAuthorizationService accountAuthorizationService;

    private GetAccountByIbanQuery query;

    private static final String VALID_IBAN = "TR290006200000000000000123";

    @BeforeEach
    void setUp() {
        query = new GetAccountByIbanQueryHandler(loadAccountPort, accountAuthorizationService);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should get account by IBAN successfully")
        void shouldGetAccountByIbanSuccessfully() {
            Account account = new Account(1L, new UserId(100L), new Iban(VALID_IBAN), "Ali Veli",
                    Money.of(new BigDecimal("500.00"), Currency.TRY), AccountStatus.ACTIVE);
            Iban iban = new Iban(VALID_IBAN);
            when(loadAccountPort.findByIban(iban)).thenReturn(Optional.of(account));
            doNothing().when(accountAuthorizationService).authorizeAccountOwner(any(), anyString());

            AccountResponse response = query.execute(VALID_IBAN);

            assertThat(response.iban()).isEqualTo(VALID_IBAN);
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw AccountNotFoundException when IBAN not found")
        void shouldThrowAccountNotFoundExceptionWhenIbanNotFound() {
            when(loadAccountPort.findByIban(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> query.execute("TR290006200000000000000999"))
                    .isExactlyInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("should throw AuthorizationException when not authorized")
        void shouldThrowAuthorizationExceptionWhenNotAuthorized() {
            Account account = new Account(1L, new UserId(100L), new Iban(VALID_IBAN), "Ali Veli",
                    Money.of(new BigDecimal("500.00"), Currency.TRY), AccountStatus.ACTIVE);
            when(loadAccountPort.findByIban(any())).thenReturn(Optional.of(account));
            doThrow(new AuthorizationException("Yetki yok")).when(accountAuthorizationService)
                    .authorizeAccountOwner(any(), anyString());

            assertThatThrownBy(() -> query.execute(VALID_IBAN))
                    .isExactlyInstanceOf(AuthorizationException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when IBAN is null")
        void shouldThrowNullPointerExceptionWhenIbanIsNull() {
            assertThatThrownBy(() -> query.execute((String) null))
                    .isExactlyInstanceOf(NullPointerException.class);
        }
    }
}
