package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.AccountOperationsPort;
import com.bank.app.transfer.application.port.AccountOperationsPort.AccountInfo;
import com.bank.app.common.exception.AccountNotFoundException;
import com.bank.app.common.exception.TransferNotFoundException;
import com.bank.app.common.security.port.SecurityContextPort;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetTransferDetailUseCaseTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private AccountOperationsPort accountOperationsPort;
    @Mock private SecurityContextPort securityContextPort;
    private GetTransferDetailUseCase getTransferDetailUseCase;

    @BeforeEach
    void setUp() {
        getTransferDetailUseCase = new GetTransferDetailUseCase(loadTransferPort, accountOperationsPort, securityContextPort);
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
                new Money(new BigDecimal("150.00"), Money.Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now()
        );

        AccountInfo sender = new AccountInfo(senderAccountId, 100L, "TRY", true);
        AccountInfo receiver = new AccountInfo(receiverAccountId, 200L, "TRY", true);

        when(loadTransferPort.findById(transferId)).thenReturn(Optional.of(transfer));
        when(accountOperationsPort.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountOperationsPort.getAccountInfo(receiverAccountId)).thenReturn(receiver);

        when(securityContextPort.getCurrentUserId()).thenReturn(Optional.of(100L));

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
                new Money(new BigDecimal("150.00"), Money.Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now()
        );

        AccountInfo sender = new AccountInfo(senderAccountId, 100L, "TRY", true);
        AccountInfo receiver = new AccountInfo(receiverAccountId, 200L, "TRY", true);

        when(loadTransferPort.findById(transferId)).thenReturn(Optional.of(transfer));
        when(accountOperationsPort.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountOperationsPort.getAccountInfo(receiverAccountId)).thenReturn(receiver);

        when(securityContextPort.getCurrentUserId()).thenReturn(Optional.of(200L));

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
                new Money(new BigDecimal("150.00"), Money.Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now()
        );

        AccountInfo sender = new AccountInfo(senderAccountId, 100L, "TRY", true);
        AccountInfo receiver = new AccountInfo(receiverAccountId, 200L, "TRY", true);

        when(loadTransferPort.findById(transferId)).thenReturn(Optional.of(transfer));
        when(accountOperationsPort.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountOperationsPort.getAccountInfo(receiverAccountId)).thenReturn(receiver);

        when(securityContextPort.getCurrentUserId()).thenReturn(Optional.of(300L));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> getTransferDetailUseCase.execute(transferId));
        assertEquals("Bu transferin detaylarını görme yetkiniz yok.", exception.getMessage());
    }

    @Test
    void shouldThrowTransferNotFoundExceptionWhenTransferDoesNotExist() {
        Long transferId = 1L;
        when(loadTransferPort.findById(transferId)).thenReturn(Optional.empty());

        TransferNotFoundException exception = assertThrows(TransferNotFoundException.class, () -> getTransferDetailUseCase.execute(transferId));
        assertEquals("Transfer bulunamadı. ID: " + transferId, exception.getMessage());
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
                new Money(new BigDecimal("150.00"), Money.Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now()
        );

        when(loadTransferPort.findById(transferId)).thenReturn(Optional.of(transfer));
        when(accountOperationsPort.getAccountInfo(senderAccountId)).thenThrow(new AccountNotFoundException(senderAccountId));

        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class, () -> getTransferDetailUseCase.execute(transferId));
        assertEquals("Hesap bulunamadı. ID: " + senderAccountId, exception.getMessage());
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
                new Money(new BigDecimal("150.00"), Money.Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now()
        );

        AccountInfo sender = new AccountInfo(senderAccountId, 100L, "TRY", true);

        when(loadTransferPort.findById(transferId)).thenReturn(Optional.of(transfer));
        when(accountOperationsPort.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountOperationsPort.getAccountInfo(receiverAccountId)).thenThrow(new AccountNotFoundException(receiverAccountId));

        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class, () -> getTransferDetailUseCase.execute(transferId));
        assertEquals("Hesap bulunamadı. ID: " + receiverAccountId, exception.getMessage());
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
                new Money(new BigDecimal("150.00"), Money.Currency.TRY),
                TransferStatus.COMPLETED,
                LocalDateTime.now()
        );

        AccountInfo sender = new AccountInfo(senderAccountId, 100L, "TRY", true);
        AccountInfo receiver = new AccountInfo(receiverAccountId, 200L, "TRY", true);

        when(loadTransferPort.findById(transferId)).thenReturn(Optional.of(transfer));
        when(accountOperationsPort.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountOperationsPort.getAccountInfo(receiverAccountId)).thenReturn(receiver);

        when(securityContextPort.getCurrentUserId()).thenReturn(Optional.empty());

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> getTransferDetailUseCase.execute(transferId));
        assertEquals("Oturum bulunamadı.", exception.getMessage());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> getTransferDetailUseCase.execute(null));
        assertEquals("Transfer ID null olamaz", exception.getMessage());
    }
}
