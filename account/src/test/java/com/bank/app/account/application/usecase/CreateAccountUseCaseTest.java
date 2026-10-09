package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.application.port.out.IbanGeneratorPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Iban;
import com.bank.app.account.domain.exception.DuplicateIbanException;
import com.bank.app.common.domain.exception.InvalidIbanException;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
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
import com.bank.app.common.domain.exception.AuthorizationException;

import java.math.BigDecimal;
import java.time.Clock;
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
    private IbanGeneratorPort ibanGeneratorPort;
    @Mock
    private DomainEventPublisherService domainEventPublisherService;
    @Mock
    private AuditEventPort auditEventPort;
    @Mock
    private AccountAuthorizationService accountAuthorizationService;
    @Mock
    private ClockProviderPort clockProvider;

    @Captor
    private ArgumentCaptor<AccountCreatedEvent> eventCaptor;

    private CreateAccountUseCase createAccountUseCase;

    private static final String VALID_IBAN = "TR440006200000000000000123";
    private static final Long USER_ID = 100L;
    private static final String OWNER = "Ali Veli";

    @BeforeEach
    void setUp() {
        createAccountUseCase = new CreateAccountUseCaseImpl(
                loadAccountPort, saveAccountPort, ibanGeneratorPort, domainEventPublisherService, auditEventPort,
                accountAuthorizationService, clockProvider, true);
        lenient().when(clockProvider.clock()).thenReturn(Clock.systemUTC());
    }

    private CreateAccountRequest validRequest() {
        return new CreateAccountRequest(USER_ID, VALID_IBAN, OWNER, new BigDecimal("500.00"), Currency.TRY);
    }

    @Test
    void generatesIbanWhenClientDoesNotProvideOne() {
        Iban generated = Iban.fromTurkishBban("0000001234567890123456");
        when(ibanGeneratorPort.generate()).thenReturn(generated);
        when(saveAccountPort.save(any(Account.class))).thenAnswer(invocation -> {
            Account opening = invocation.getArgument(0);
            return new Account(1L, opening.getUserId(), opening.getIban(), opening.getOwnerName(),
                    opening.getBalance(), AccountStatus.ACTIVE);
        });

        AccountResponse response = createAccountUseCase.execute(
                new CreateAccountRequest(USER_ID, OWNER, BigDecimal.ZERO, Currency.TRY));

        assertThat(response.iban()).isEqualTo(generated.value());
        assertThat(new Iban(response.iban()).hasValidChecksum()).isTrue();
        verify(loadAccountPort).findByIban(generated);
    }

    @Test
    void retriesWhenGeneratedIbanAlreadyExists() {
        Iban first = Iban.fromTurkishBban("0000001234567890123456");
        Iban second = Iban.fromTurkishBban("0000001234567890123457");
        Account existing = new Account(7L, new UserId(USER_ID), first, OWNER,
                new Money(BigDecimal.ZERO, Currency.TRY), AccountStatus.ACTIVE);
        when(ibanGeneratorPort.generate()).thenReturn(first, second);
        when(loadAccountPort.findByIban(first)).thenReturn(Optional.of(existing));
        when(saveAccountPort.save(any(Account.class))).thenAnswer(invocation -> {
            Account opening = invocation.getArgument(0);
            return new Account(8L, opening.getUserId(), opening.getIban(), opening.getOwnerName(),
                    opening.getBalance(), AccountStatus.ACTIVE);
        });

        AccountResponse response = createAccountUseCase.execute(
                new CreateAccountRequest(USER_ID, OWNER, BigDecimal.ZERO, Currency.TRY));

        assertThat(response.iban()).isEqualTo(second.value());
        verify(ibanGeneratorPort, times(2)).generate();
    }

    @Test
    void shouldRejectChecksumInvalidGeneratedIban() {
        // Kills the VoidMethodCall mutant (removed requireValidChecksum on the
        // generated path): without the guard the colliding/invalid IBAN would
        // be accepted instead of rejected.
        Iban checksumInvalid = new Iban("TR340006100519786457841326");
        when(ibanGeneratorPort.generate()).thenReturn(checksumInvalid);

        assertThatThrownBy(() -> createAccountUseCase.execute(
                new CreateAccountRequest(USER_ID, OWNER, BigDecimal.ZERO, Currency.TRY)))
                .isExactlyInstanceOf(InvalidIbanException.class);
        verify(saveAccountPort, never()).save(any(Account.class));
    }

    @Test
    void shouldFailAfterFiveCollidingGenerations() {
        // Kills the ConditionalsBoundary mutant (attempt < 5 vs <= 5):
        // exactly five generations must be attempted before giving up.
        Iban colliding = Iban.fromTurkishBban("0000001234567890123456");
        Account existing = new Account(7L, new UserId(USER_ID), colliding, OWNER,
                new Money(BigDecimal.ZERO, Currency.TRY), AccountStatus.ACTIVE);
        when(ibanGeneratorPort.generate()).thenReturn(colliding);
        when(loadAccountPort.findByIban(colliding)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> createAccountUseCase.execute(
                new CreateAccountRequest(USER_ID, OWNER, BigDecimal.ZERO, Currency.TRY)))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("five attempts");
        verify(ibanGeneratorPort, times(5)).generate();
    }

    @Test
    void nonDemoOpeningCannotMintBalance() {
        var productionUseCase = new CreateAccountUseCaseImpl(loadAccountPort, saveAccountPort, ibanGeneratorPort,
                domainEventPublisherService, auditEventPort, accountAuthorizationService, clockProvider, false);

        assertThatThrownBy(() -> productionUseCase.execute(validRequest()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessage("Initial funding is available only in demo mode.");
        verifyNoInteractions(saveAccountPort);
    }

    @Test
    void nonDemoOpeningStartsAtZero() {
        var productionUseCase = new CreateAccountUseCaseImpl(loadAccountPort, saveAccountPort, ibanGeneratorPort,
                domainEventPublisherService, auditEventPort, accountAuthorizationService, clockProvider, false);
        var request = new CreateAccountRequest(USER_ID, VALID_IBAN, OWNER, BigDecimal.ZERO, Currency.TRY);
        when(saveAccountPort.save(any(Account.class))).thenAnswer(invocation -> {
            Account opening = invocation.getArgument(0);
            return new Account(1L, opening.getUserId(), opening.getIban(), opening.getOwnerName(),
                    opening.getBalance(), AccountStatus.ACTIVE);
        });

        var response = productionUseCase.execute(request);

        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(saveAccountPort).save(any(Account.class));
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

            verify(domainEventPublisherService, times(1)).publish(eventCaptor.capture());
            verify(auditEventPort, times(1)).publish(any());
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

            doThrow(new AuthorizationException("You cannot create an account on behalf of another user."))
                    .when(accountAuthorizationService).authorizeUserAction(eq(200L), anyString());

            assertThatThrownBy(() -> createAccountUseCase.execute(request))
                    .isExactlyInstanceOf(AuthorizationException.class)
                    .hasMessage("You cannot create an account on behalf of another user.");
            verify(saveAccountPort, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("should throw when user is not logged in")
        void shouldThrowWhenNotLoggedIn() {
            CreateAccountRequest request = validRequest();

            doThrow(new AuthorizationException("Session not found."))
                    .when(accountAuthorizationService).authorizeUserAction(eq(USER_ID), anyString());

            assertThatThrownBy(() -> createAccountUseCase.execute(request))
                    .isExactlyInstanceOf(AuthorizationException.class)
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
                    .hasMessage("An account already exists with this IBAN: TR440006*******0123");
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

        @Test
        void shouldRejectBadCheckDigitsBeforePersistingNewAccount() {
            CreateAccountRequest request = new CreateAccountRequest(
                    USER_ID, "TR340006100519786457841326", OWNER, BigDecimal.ZERO, Currency.TRY);

            assertThatThrownBy(() -> createAccountUseCase.execute(request))
                    .isExactlyInstanceOf(InvalidIbanException.class);
            verifyNoInteractions(saveAccountPort);
        }
    }
}
