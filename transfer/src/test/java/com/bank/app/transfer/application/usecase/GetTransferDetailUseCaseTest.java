package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.transfer.application.exception.TransferNotFoundException;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.bank.app.common.domain.exception.AuthorizationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class GetTransferDetailUseCaseTest {

    @Mock
    private LoadTransferPort loadTransferPort;
    @Mock
    private AccountAclPort accountAclPort;
    @Mock
    private TransferAuthorizationService transferAuthorizationService;
    private GetTransferDetailQuery getTransferDetailUseCase;

    @BeforeEach
    void setUp() {
        getTransferDetailUseCase = new GetTransferDetailUseCaseImpl(loadTransferPort, accountAclPort,
                transferAuthorizationService);
    }

    @Test
    void shouldGetTransferDetailSuccessfullyWhenUserIsSenderOwner() {
        Long transferId = 1L;
        Long senderAccountId = 10L;
        Long receiverAccountId = 20L;

        Transfer transfer = new Transfer(
                transferId,
                senderAccountId,
                receiverAccountId,
                new Money(new BigDecimal("150.00"), Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());

        AccountInfo sender = new AccountInfo(senderAccountId, 100L, "TRY", "ACTIVE");
        AccountInfo receiver = new AccountInfo(receiverAccountId, 200L, "TRY", "ACTIVE");

        when(loadTransferPort.findById(transferId)).thenReturn(Optional.of(transfer));
        when(accountAclPort.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountAclPort.getAccountInfo(receiverAccountId)).thenReturn(receiver);

        TransferDetailResponse response = getTransferDetailUseCase.execute(transferId);

        assertNotNull(response);
        assertEquals(transferId, response.id());
        assertEquals(senderAccountId, response.senderAccountId());
        assertEquals(receiverAccountId, response.receiverAccountId());
        assertEquals(new BigDecimal("150.00"), response.amount());
        assertEquals("TRY", response.currency());
        assertEquals("COMPLETED", response.status());
    }

    @Test
    void shouldGetTransferDetailSuccessfullyWhenUserIsReceiverOwner() {
        Long transferId = 1L;
        Long senderAccountId = 10L;
        Long receiverAccountId = 20L;

        Transfer transfer = new Transfer(
                transferId,
                senderAccountId,
                receiverAccountId,
                new Money(new BigDecimal("150.00"), Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());

        AccountInfo sender = new AccountInfo(senderAccountId, 100L, "TRY", "ACTIVE");
        AccountInfo receiver = new AccountInfo(receiverAccountId, 200L, "TRY", "ACTIVE");

        when(loadTransferPort.findById(transferId)).thenReturn(Optional.of(transfer));
        when(accountAclPort.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountAclPort.getAccountInfo(receiverAccountId)).thenReturn(receiver);

        TransferDetailResponse response = getTransferDetailUseCase.execute(transferId);

        assertNotNull(response);
        assertEquals(transferId, response.id());
        assertEquals(senderAccountId, response.senderAccountId());
        assertEquals(receiverAccountId, response.receiverAccountId());
        assertEquals(new BigDecimal("150.00"), response.amount());
        assertEquals("TRY", response.currency());
        assertEquals("COMPLETED", response.status());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenUserIsNeitherSenderNorReceiver() {
        Long transferId = 1L;
        Long senderAccountId = 10L;
        Long receiverAccountId = 20L;

        Transfer transfer = new Transfer(
                transferId,
                senderAccountId,
                receiverAccountId,
                new Money(new BigDecimal("150.00"), Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());

        AccountInfo sender = new AccountInfo(senderAccountId, 100L, "TRY", "ACTIVE");
        AccountInfo receiver = new AccountInfo(receiverAccountId, 200L, "TRY", "ACTIVE");

        when(loadTransferPort.findById(transferId)).thenReturn(Optional.of(transfer));
        when(accountAclPort.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountAclPort.getAccountInfo(receiverAccountId)).thenReturn(receiver);

        doThrow(new AuthorizationException("You are not authorized to view this transfer's details."))
                .when(transferAuthorizationService).authorizeTransferAccess(eq(100L), eq(200L), anyString());

        AuthorizationException exception = assertThrows(AuthorizationException.class,
                () -> getTransferDetailUseCase.execute(transferId));
        assertEquals("You are not authorized to view this transfer's details.", exception.getMessage());
    }

    @Test
    void shouldThrowTransferNotFoundExceptionWhenTransferDoesNotExist() {
        Long transferId = 1L;
        when(loadTransferPort.findById(transferId)).thenReturn(Optional.empty());

        TransferNotFoundException exception = assertThrows(TransferNotFoundException.class,
                () -> getTransferDetailUseCase.execute(transferId));
        assertEquals("Transfer not found. ID: " + transferId, exception.getMessage());
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenSenderAccountDoesNotExist() {
        Long transferId = 1L;
        Long senderAccountId = 10L;
        Long receiverAccountId = 20L;

        Transfer transfer = new Transfer(
                transferId,
                senderAccountId,
                receiverAccountId,
                new Money(new BigDecimal("150.00"), Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());

        when(loadTransferPort.findById(transferId)).thenReturn(Optional.of(transfer));
        when(accountAclPort.getAccountInfo(senderAccountId))
                .thenThrow(new AccountNotFoundException(senderAccountId));

        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class,
                () -> getTransferDetailUseCase.execute(transferId));
        assertEquals("Account not found. ID: " + senderAccountId, exception.getMessage());
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenReceiverAccountDoesNotExist() {
        Long transferId = 1L;
        Long senderAccountId = 10L;
        Long receiverAccountId = 20L;

        Transfer transfer = new Transfer(
                transferId,
                senderAccountId,
                receiverAccountId,
                new Money(new BigDecimal("150.00"), Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());

        AccountInfo sender = new AccountInfo(senderAccountId, 100L, "TRY", "ACTIVE");

        when(loadTransferPort.findById(transferId)).thenReturn(Optional.of(transfer));
        when(accountAclPort.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountAclPort.getAccountInfo(receiverAccountId))
                .thenThrow(new AccountNotFoundException(receiverAccountId));

        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class,
                () -> getTransferDetailUseCase.execute(transferId));
        assertEquals("Account not found. ID: " + receiverAccountId, exception.getMessage());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenNoUserLoggedIn() {
        Long transferId = 1L;
        Long senderAccountId = 10L;
        Long receiverAccountId = 20L;

        Transfer transfer = new Transfer(
                transferId,
                senderAccountId,
                receiverAccountId,
                new Money(new BigDecimal("150.00"), Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now());

        AccountInfo sender = new AccountInfo(senderAccountId, 100L, "TRY", "ACTIVE");
        AccountInfo receiver = new AccountInfo(receiverAccountId, 200L, "TRY", "ACTIVE");

        when(loadTransferPort.findById(transferId)).thenReturn(Optional.of(transfer));
        when(accountAclPort.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountAclPort.getAccountInfo(receiverAccountId)).thenReturn(receiver);

        doThrow(new AuthorizationException("Session not found."))
                .when(transferAuthorizationService).authorizeTransferAccess(eq(100L), eq(200L), anyString());

        AuthorizationException exception = assertThrows(AuthorizationException.class,
                () -> getTransferDetailUseCase.execute(transferId));
        assertEquals("Session not found.", exception.getMessage());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> getTransferDetailUseCase.execute(null));
        assertEquals("Transfer ID must not be null", exception.getMessage());
    }
}
