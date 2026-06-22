package com.bank.app.account.application.usecase;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.account.domain.Iban;
import com.bank.app.account.domain.exception.DuplicateIbanException;
import com.bank.app.account.domain.exception.InvalidIbanException;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateAccountUseCaseTest {

        @Mock
        private LoadAccountPort loadAccountPort;
        @Mock
        private SaveAccountPort saveAccountPort;
        @Mock
        private EventPublisherPort eventPublisherPort;
        @Mock
        private SecurityContextPort securityContextPort;

        @Captor
        private ArgumentCaptor<AccountCreatedEvent> eventCaptor;

        private CreateAccountUseCase createAccountUseCase;

        @BeforeEach
        void setUp() {
                createAccountUseCase = new CreateAccountUseCaseImpl(loadAccountPort, saveAccountPort, eventPublisherPort,
                                securityContextPort);
        }

        @Test
        void shouldCreateAccountSuccessfully() {
                CreateAccountRequest request = new CreateAccountRequest(
                                100L,
                                "TR290006200000000000000123",
                                "Ali Veli",
                                new BigDecimal("500.00"),
                                Currency.TRY);

                Iban iban = new Iban(request.iban());
                Account savedAccount = new Account(
                                1L,
                                100L,
                                iban,
                                "Ali Veli",
                                new Money(new BigDecimal("500.00"), Currency.TRY),
                                AccountStatus.ACTIVE);

                doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());
                when(loadAccountPort.findByIban(iban))
                                .thenReturn(Optional.empty());
                when(saveAccountPort.save(any(Account.class)))
                                .thenReturn(savedAccount);

                AccountResponse response = createAccountUseCase.execute(request);

                assertNotNull(response);
                assertEquals(1L, response.id());
                assertEquals("TR290006200000000000000123", response.iban());
                assertEquals("Ali Veli", response.ownerName());
                assertEquals(new BigDecimal("500.00"), response.balance());
                assertEquals("TRY", response.currency());
                assertEquals(AccountStatus.ACTIVE, response.status());

                ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
                verify(saveAccountPort).save(accountCaptor.capture());
                assertEquals("TR290006200000000000000123", accountCaptor.getValue().getIban().value());

                verify(eventPublisherPort).publish(eventCaptor.capture());
                AccountCreatedEvent publishedEvent = eventCaptor.getValue();
                assertEquals(1L, publishedEvent.getAccountId());
                assertEquals(100L, publishedEvent.getUserId());
        }

        @Test
        void shouldThrowAccessDeniedExceptionWhenCreatingAccountForAnotherUser() {
                CreateAccountRequest request = new CreateAccountRequest(
                                200L,
                                "TR290006200000000000000123",
                                "Ali Veli",
                                new BigDecimal("500.00"),
                                Currency.TRY);

                doThrow(new AccessDeniedException("Başka bir kullanıcı adına hesap oluşturamazsınız."))
                        .when(securityContextPort).checkUserAuthorization(eq(200L), anyString());

                AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                                () -> createAccountUseCase.execute(request));
                assertEquals("Başka bir kullanıcı adına hesap oluşturamazsınız.", ex.getMessage());
                verify(saveAccountPort, never()).save(any(Account.class));
        }

        @Test
        void shouldThrowExceptionWhenIbanAlreadyExists() {
                CreateAccountRequest request = new CreateAccountRequest(
                                100L,
                                "TR290006200000000000000123",
                                "Ali Veli",
                                new BigDecimal("500.00"),
                                Currency.TRY);

                Iban iban = new Iban(request.iban());
                Account existingAccount = new Account(
                                1L,
                                100L,
                                iban,
                                "Eski Sahip",
                                new Money(new BigDecimal("100.00"), Currency.TRY),
                                AccountStatus.ACTIVE);

                doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());
                when(loadAccountPort.findByIban(iban)).thenReturn(Optional.of(existingAccount));

                DuplicateIbanException exception = assertThrows(DuplicateIbanException.class, () -> {
                        createAccountUseCase.execute(request);
                });

                assertEquals("Bu IBAN ile kayıtlı bir hesap zaten mevcut: TR290006200000000000000123",
                                exception.getMessage());
                verify(saveAccountPort, never()).save(any(Account.class));
        }

        @Test
        void shouldThrowNullPointerExceptionWhenCurrencyIsNull() {
                CreateAccountRequest request = new CreateAccountRequest(
                                100L,
                                "TR290006200000000000000123",
                                "Ali Veli",
                                new BigDecimal("500.00"),
                                null);

                Iban iban = new Iban(request.iban());
                doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());
                when(loadAccountPort.findByIban(iban)).thenReturn(Optional.empty());

                NullPointerException ex = assertThrows(NullPointerException.class, () -> {
                        createAccountUseCase.execute(request);
                });
                assertEquals("Para birimi boş olamaz", ex.getMessage());

                verify(saveAccountPort, never()).save(any(Account.class));
        }

        @Test
        void shouldThrowInvalidIbanExceptionWhenIbanFormatIsInvalid() {
                CreateAccountRequest request = new CreateAccountRequest(
                                100L,
                                "INVALID_IBAN",
                                "Ali Veli",
                                new BigDecimal("500.00"),
                                Currency.TRY);

                doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

                assertThrows(InvalidIbanException.class,
                                () -> createAccountUseCase.execute(request));
                verify(saveAccountPort, never()).save(any(Account.class));
        }

        @Test
        void shouldThrowNullPointerExceptionWhenRequestIsNull() {
                NullPointerException ex = assertThrows(NullPointerException.class,
                                () -> createAccountUseCase.execute(null));
                assertEquals("Request null olamaz", ex.getMessage());
        }

        @Test
        void shouldThrowAccessDeniedExceptionWhenUserNotLoggedIn() {
                CreateAccountRequest request = new CreateAccountRequest(
                                100L,
                                "TR290006200000000000000123",
                                "Ali Veli",
                                new BigDecimal("500.00"),
                                Currency.TRY);

                doThrow(new AccessDeniedException("Oturum bulunamadı."))
                        .when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

                AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                                () -> createAccountUseCase.execute(request));
                assertEquals("Oturum bulunamadı.", ex.getMessage());
                verify(saveAccountPort, never()).save(any(Account.class));
        }

}
