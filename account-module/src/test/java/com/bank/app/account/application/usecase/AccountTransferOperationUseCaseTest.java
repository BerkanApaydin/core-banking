package com.bank.app.account.application.usecase;

import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.account.application.port.in.AccountTransferOperationPort;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.exception.AuthorizationException;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
class AccountTransferOperationUseCaseTest {

    @Mock
    private LoadAccountPort loadAccountPort;
    @Mock
    private SaveAccountPort saveAccountPort;
    @Mock
    private SecurityContextPort securityContextPort;

    private AccountTransferOperationPort useCase;

    private static final String SENDER_IBAN = "TR290006200000000000000111";
    private static final String RECEIVER_IBAN = "TR290006200000000000000222";

    @BeforeEach
    void setUp() {
        useCase = new AccountTransferOperationUseCase(loadAccountPort, saveAccountPort, securityContextPort);
    }

    private Account account(Long id, Long userId, BigDecimal balance, String iban, AccountStatus status) {
        return new Account(id, userId, new Iban(iban), "Owner" + id, Money.of(balance, Currency.TRY), status);
    }

    @Test
    void shouldExecuteTransferSuccessfully() {
        Account sender = account(1L, 100L, new BigDecimal("1000.00"), SENDER_IBAN, AccountStatus.ACTIVE);
        Account receiver = account(2L, 200L, new BigDecimal("500.00"), RECEIVER_IBAN, AccountStatus.ACTIVE);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        useCase.executeTransfer(1L, 2L, Money.of("200.00", Currency.TRY));

        assertEquals(new BigDecimal("800.00"), sender.getBalance().amount());
        assertEquals(new BigDecimal("700.00"), receiver.getBalance().amount());
        verify(saveAccountPort).save(sender);
        verify(saveAccountPort).save(receiver);
    }

    @Test
    void shouldLockAccountsInOrderWhenSenderIdLessThanReceiverId() {
        Account sender = account(1L, 100L, new BigDecimal("1000.00"), SENDER_IBAN, AccountStatus.ACTIVE);
        Account receiver = account(2L, 200L, new BigDecimal("500.00"), RECEIVER_IBAN, AccountStatus.ACTIVE);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        useCase.executeTransfer(1L, 2L, Money.of("100.00", Currency.TRY));

        InOrder order = inOrder(loadAccountPort);
        order.verify(loadAccountPort).findByIdWithLock(1L);
        order.verify(loadAccountPort).findByIdWithLock(2L);
    }

    @Test
    void shouldLockAccountsInReverseOrderWhenReceiverIdLessThanSenderId() {
        Account sender = account(2L, 100L, new BigDecimal("1000.00"), SENDER_IBAN, AccountStatus.ACTIVE);
        Account receiver = account(1L, 200L, new BigDecimal("500.00"), RECEIVER_IBAN, AccountStatus.ACTIVE);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(receiver));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(sender));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        useCase.executeTransfer(2L, 1L, Money.of("100.00", Currency.TRY));

        InOrder order = inOrder(loadAccountPort);
        order.verify(loadAccountPort).findByIdWithLock(1L);
        order.verify(loadAccountPort).findByIdWithLock(2L);
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenSenderNotFound() {
        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> useCase.executeTransfer(1L, 2L, Money.of("100.00", Currency.TRY)));
        verifyNoInteractions(saveAccountPort);
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenReceiverNotFound() {
        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(
                Optional.of(account(1L, 100L, new BigDecimal("1000.00"), SENDER_IBAN, AccountStatus.ACTIVE)));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> useCase.executeTransfer(1L, 2L, Money.of("100.00", Currency.TRY)));
        verifyNoInteractions(saveAccountPort);
    }

    @Test
    void shouldThrowAuthorizationExceptionWhenUserNotAuthorized() {
        Account sender = account(1L, 100L, new BigDecimal("1000.00"), SENDER_IBAN, AccountStatus.ACTIVE);
        Account receiver = account(2L, 200L, new BigDecimal("500.00"), RECEIVER_IBAN, AccountStatus.ACTIVE);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        doThrow(new AuthorizationException("Bu hesaptan transfer yapmaya yetkiniz yok."))
                .when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        assertThrows(AuthorizationException.class,
                () -> useCase.executeTransfer(1L, 2L, Money.of("100.00", Currency.TRY)));
        verify(saveAccountPort, never()).save(any());
    }

    @Test
    void shouldReverseTransferSuccessfully() {
        Account sender = account(1L, 100L, new BigDecimal("800.00"), SENDER_IBAN, AccountStatus.ACTIVE);
        Account receiver = account(2L, 200L, new BigDecimal("700.00"), RECEIVER_IBAN, AccountStatus.ACTIVE);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        useCase.reverseTransfer(1L, 2L, Money.of("200.00", Currency.TRY));

        assertEquals(new BigDecimal("1000.00"), sender.getBalance().amount());
        assertEquals(new BigDecimal("500.00"), receiver.getBalance().amount());
        verify(saveAccountPort).save(sender);
        verify(saveAccountPort).save(receiver);
    }

    @Test
    void shouldThrowAccountNotFoundExceptionForReverseWhenSenderNotFound() {
        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> useCase.reverseTransfer(1L, 2L, Money.of("100.00", Currency.TRY)));
        verifyNoInteractions(saveAccountPort);
    }

    @Test
    void shouldThrowAuthorizationExceptionForReverseWhenUserNotAuthorized() {
        Account sender = account(1L, 100L, new BigDecimal("800.00"), SENDER_IBAN, AccountStatus.ACTIVE);
        Account receiver = account(2L, 200L, new BigDecimal("700.00"), RECEIVER_IBAN, AccountStatus.ACTIVE);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        doThrow(new AuthorizationException("Bu transferi iptal etmeye yetkiniz yok."))
                .when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        assertThrows(AuthorizationException.class,
                () -> useCase.reverseTransfer(1L, 2L, Money.of("200.00", Currency.TRY)));
        verify(saveAccountPort, never()).save(any());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenSenderIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> useCase.executeTransfer(null, 2L, Money.of("100.00", Currency.TRY)));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenReceiverIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> useCase.executeTransfer(1L, null, Money.of("100.00", Currency.TRY)));
    }
}
