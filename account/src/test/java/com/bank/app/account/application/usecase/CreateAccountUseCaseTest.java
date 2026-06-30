package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Iban;
import com.bank.app.account.domain.exception.DuplicateIbanException;
import com.bank.app.common.domain.exception.InvalidIbanException;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import com.bank.app.account.application.service.AccountAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateAccountUseCase")
class CreateAccountUseCaseTest {

    @Mock
    private LoadAccountPort loadAccountPort;
    @Mock
    private SaveAccountPort saveAccountPort;
    @Mock
    private EventPublisherPort eventPublisherPort;
    @Mock
    private AuditEventPort auditEventPort;
    @Mock
    private AccountAuthorizationService accountAuthorizationService;

    @Captor
    private ArgumentCaptor<AccountCreatedEvent> eventCaptor;

    private CreateAccountUseCase createAccountUseCase;

    private static final String VALID_IBAN = "TR290006200000000000000123";
    private static final Long USER_ID = 100L;
    private static final String OWNER = "Ali Veli";

    @BeforeEach
    void setUp() {
        createAccountUseCase = new CreateAccountUseCaseImpl(
                loadAccountPort, saveAccountPort, eventPublisherPort, auditEventPort, accountAuthorizationService);
    }

    private CreateAccountRequest validRequest() {
        return new CreateAccountRequest(USER_ID, VALID_IBAN, OWNER, new BigDecimal("500.00"), Currency.TRY);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should create account successfully")
        void shouldCreateSuccessfully() {
            CreateAccountRequest request = validRequest();
            Iban iban = new Iban(request.iban());
            Account savedAccount = new Account(1L, new UserId(USER_ID), iban, OWNER,
                    new Money(new BigDecimal("500.00"), Currency.TRY), AccountStatus.ACTIVE);

            doNothing().when(accountAuthorizationService).authorizeUserAction(eq(USER_ID), anyString());
            when(loadAccountPort.findByIban(iban)).thenReturn(Optional.empty());
            when(saveAccountPort.save(any(Account.class))).thenReturn(savedAccount);

            AccountResponse response = createAccountUseCase.execute(request);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.iban()).isEqualTo(VALID_IBAN);
            assertThat(response.ownerName()).isEqualTo(OWNER);
            assertThat(response.balance()).isEqualByComparingTo("500.00");
            assertThat(response.currency()).isEqualTo("TRY");
            assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);

            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            verify(saveAccountPort).save(accountCaptor.capture());
            assertThat(accountCaptor.getValue().getIban().value()).isEqualTo(VALID_IBAN);

            verify(eventPublisherPort).publish(any());
            verify(eventPublisherPort).publish(eventCaptor.capture());
            verify(auditEventPort).publish(any());
            AccountCreatedEvent publishedEvent = eventCaptor.getValue();
            assertThat(publishedEvent.accountId()).isEqualTo(1L);
            assertThat(publishedEvent.userId().value()).isEqualTo(USER_ID);
        }
    }

    @Nested
    @DisplayName("authorization")
    class Authorization {

        @Test
        @DisplayName("should throw when creating for another user")
        void shouldThrowForAnotherUser() {
            CreateAccountRequest request = new CreateAccountRequest(
                    200L, VALID_IBAN, OWNER, new BigDecimal("500.00"), Currency.TRY);

            doThrow(new AccessDeniedException("You cannot create an account on behalf of another user."))
                    .when(accountAuthorizationService).authorizeUserAction(eq(200L), anyString());

            assertThatThrownBy(() -> createAccountUseCase.execute(request))
                    .isExactlyInstanceOf(AccessDeniedException.class)
                    .hasMessage("You cannot create an account on behalf of another user.");
            verify(saveAccountPort, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("should throw when user is not logged in")
        void shouldThrowWhenNotLoggedIn() {
            CreateAccountRequest request = validRequest();

            doThrow(new AccessDeniedException("Session not found."))
                    .when(accountAuthorizationService).authorizeUserAction(eq(USER_ID), anyString());

            assertThatThrownBy(() -> createAccountUseCase.execute(request))
                    .isExactlyInstanceOf(AccessDeniedException.class)
                    .hasMessage("Session not found.");
            verify(saveAccountPort, never()).save(any(Account.class));
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("should throw when request is null")
        void shouldThrowOnNullRequest() {
            assertThatThrownBy(() -> createAccountUseCase.execute(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Request must not be null");
        }

        @Test
        @DisplayName("should throw when IBAN already exists")
        void shouldThrowOnDuplicateIban() {
            CreateAccountRequest request = validRequest();
            Iban iban = new Iban(request.iban());
            Account existingAccount = new Account(1L, new UserId(USER_ID), iban, "Eski Sahip",
                    new Money(new BigDecimal("100.00"), Currency.TRY), AccountStatus.ACTIVE);

            doNothing().when(accountAuthorizationService).authorizeUserAction(eq(USER_ID), anyString());
            when(loadAccountPort.findByIban(iban)).thenReturn(Optional.of(existingAccount));

            assertThatThrownBy(() -> createAccountUseCase.execute(request))
                    .isExactlyInstanceOf(DuplicateIbanException.class)
                    .hasMessage("An account already exists with this IBAN: " + VALID_IBAN);
            verify(saveAccountPort, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("should throw when currency is null")
        void shouldThrowOnNullCurrency() {
            assertThatThrownBy(() -> new CreateAccountRequest(
                    USER_ID, VALID_IBAN, OWNER, new BigDecimal("500.00"), null))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw when IBAN format is invalid")
        void shouldThrowOnInvalidIban() {
            CreateAccountRequest request = new CreateAccountRequest(
                    USER_ID, "INVALID_IBAN", OWNER, new BigDecimal("500.00"), Currency.TRY);

            doNothing().when(accountAuthorizationService).authorizeUserAction(eq(USER_ID), anyString());

            assertThatThrownBy(() -> createAccountUseCase.execute(request))
                    .isExactlyInstanceOf(InvalidIbanException.class);
            verify(saveAccountPort, never()).save(any(Account.class));
        }
    }
}
