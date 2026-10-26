package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.api.AccountNotFoundException;
import com.bank.app.account.application.port.in.GetAccountByIbanQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.UserId;
import com.bank.app.common.domain.exception.InvalidIbanException;
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
@DisplayName("GetAccountByIbanQueryImpl")
@SuppressWarnings("null")
class GetAccountByIbanQueryImplTest {

    @Mock
    private LoadAccountPort loadAccountPort;
    @Mock
    private AccountAuthorizationService accountAuthorizationService;

    private GetAccountByIbanQuery query;

    private static final String VALID_IBAN = "TR440006200000000000000123";

    @BeforeEach
    void setUp() {
        query = new GetAccountByIbanQueryImpl(loadAccountPort, accountAuthorizationService);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should get account by IBAN successfully")
        void shouldGetAccountByIbanSuccessfully() {
            Account account = Account.rehydrate(1L, new UserId(100L), new Iban(VALID_IBAN), "Ali Veli",
                    Money.of(new BigDecimal("500.00"), Currency.TRY), AccountStatus.ACTIVE);
            Iban iban = new Iban(VALID_IBAN);
            when(loadAccountPort.findByIban(iban)).thenReturn(Optional.of(account));
            when(accountAuthorizationService.isCurrentUserOwner(account)).thenReturn(true);

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

            assertThatThrownBy(() -> query.execute("TR600006200000000000000999"))
                    .isExactlyInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("should hide another user's IBAN with not found")
        void shouldHideAnotherUsersIban() {
            Account account = Account.rehydrate(1L, new UserId(100L), new Iban(VALID_IBAN), "Ali Veli",
                    Money.of(new BigDecimal("500.00"), Currency.TRY), AccountStatus.ACTIVE);
            when(loadAccountPort.findByIban(any())).thenReturn(Optional.of(account));
            when(accountAuthorizationService.isCurrentUserOwner(account)).thenReturn(false);

            assertThatThrownBy(() -> query.execute(VALID_IBAN))
                    .isExactlyInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when IBAN is null")
        void shouldThrowNullPointerExceptionWhenIbanIsNull() {
            assertThatThrownBy(() -> query.execute((String) null))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject a malformed IBAN as a client error, not as not-found")
        void shouldRejectMalformedIbanAsInvalidIban() {
            // A malformed IBAN is a client mistake (400-class), while a
            // well-formed but unknown IBAN is a not-found. Collapsing the two
            // hides the client's typo behind a "no such account" answer.
            assertThatThrownBy(() -> query.execute("INVALID_IBAN"))
                    .isExactlyInstanceOf(InvalidIbanException.class)
                    .isNotInstanceOf(AccountNotFoundException.class)
                    .satisfies(ex -> assertThat(((InvalidIbanException) ex).getErrorCode())
                            .isEqualTo("INVALID_IBAN"));
            verifyNoInteractions(loadAccountPort);
        }

        @Test
        @DisplayName("should reject a truncated IBAN as a client error")
        void shouldRejectTruncatedIbanAsInvalidIban() {
            assertThatThrownBy(() -> query.execute("TR12"))
                    .isExactlyInstanceOf(InvalidIbanException.class)
                    .isNotInstanceOf(AccountNotFoundException.class);
            verifyNoInteractions(loadAccountPort);
        }
    }
}
