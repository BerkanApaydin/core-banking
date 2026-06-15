package com.bank.app.transfer.application.usecase;

import com.bank.app.account.application.usecase.AccountInternalService;
import com.bank.app.account.application.usecase.AccountInternalService.AccountInfo;
import com.bank.app.common.exception.AccountNotFoundException;
import com.bank.app.common.exception.TransferNotFoundException;
import com.bank.app.common.security.SecurityUtils;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetTransferDetailUseCaseTest {

    private LoadTransferPort loadTransferPort;
    private AccountInternalService accountInternalService;
    private SecurityUtils securityUtils;
    private GetTransferDetailUseCase getTransferDetailUseCase;

    @BeforeEach
    void setUp() {
        loadTransferPort = mock(LoadTransferPort.class);
        accountInternalService = mock(AccountInternalService.class);
        securityUtils = mock(SecurityUtils.class);
        getTransferDetailUseCase = new GetTransferDetailUseCase(loadTransferPort, accountInternalService, securityUtils);
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
        when(accountInternalService.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountInternalService.getAccountInfo(receiverAccountId)).thenReturn(receiver);

        when(securityUtils.getCurrentUserId()).thenReturn(Optional.of(100L));

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
        when(accountInternalService.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountInternalService.getAccountInfo(receiverAccountId)).thenReturn(receiver);

        when(securityUtils.getCurrentUserId()).thenReturn(Optional.of(200L));

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
        when(accountInternalService.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountInternalService.getAccountInfo(receiverAccountId)).thenReturn(receiver);

        when(securityUtils.getCurrentUserId()).thenReturn(Optional.of(300L));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> getTransferDetailUseCase.execute(transferId));
        assertEquals("Bu transferin detaylarını görme yetkiniz yok.", exception.getMessage());
    }

    @Test
    void shouldThrowTransferNotFoundExceptionWhenTransferDoesNotExist() {
        Long transferId = 1L;
        when(loadTransferPort.findById(transferId)).thenReturn(Optional.empty());

        assertThrows(TransferNotFoundException.class, () -> getTransferDetailUseCase.execute(transferId));
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
        when(accountInternalService.getAccountInfo(senderAccountId)).thenThrow(new AccountNotFoundException(senderAccountId));

        assertThrows(AccountNotFoundException.class, () -> getTransferDetailUseCase.execute(transferId));
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
        when(accountInternalService.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountInternalService.getAccountInfo(receiverAccountId)).thenThrow(new AccountNotFoundException(receiverAccountId));

        assertThrows(AccountNotFoundException.class, () -> getTransferDetailUseCase.execute(transferId));
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
        when(accountInternalService.getAccountInfo(senderAccountId)).thenReturn(sender);
        when(accountInternalService.getAccountInfo(receiverAccountId)).thenReturn(receiver);

        when(securityUtils.getCurrentUserId()).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> getTransferDetailUseCase.execute(transferId));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        assertThrows(NullPointerException.class, () -> getTransferDetailUseCase.execute(null));
    }
}
