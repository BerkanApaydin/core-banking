package com.bank.app.transfer.application.usecase;

import com.bank.app.audit.application.AuditService;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.common.domain.Money;
import com.bank.app.account.exception.AccountNotActiveException;
import com.bank.app.account.exception.InsufficientBalanceException;
import com.bank.app.transfer.exception.SameAccountTransferException;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.AccountOperationsPort;
import com.bank.app.transfer.application.port.AccountOperationsPort.AccountInfo;
import com.bank.app.transfer.application.port.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.bank.app.transfer.domain.TransferDomainService;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceTransferUseCaseEdgeCaseTest {

        @Mock
        private AccountOperationsPort accountOperationsPort;
        @Mock
        private SaveTransferPort saveTransferPort;
        @Mock
        private ApplicationEventPublisher eventPublisher;
        @Mock
        private AuditService auditService;

        private PlaceTransferUseCase placeTransferUseCase;

        private static final String SENDER_IBAN = "TR290006200000000000000111";
        private static final String RECEIVER_IBAN = "TR290006200000000000000222";

        @BeforeEach
        void setUp() {
                placeTransferUseCase = new PlaceTransferUseCase(
                                accountOperationsPort, saveTransferPort, auditService,
                                eventPublisher, new TransferDomainService());
        }

        private TransferRequest validRequest() {
                return new TransferRequest(SENDER_IBAN, RECEIVER_IBAN,
                                new BigDecimal("200.00"), Money.Currency.TRY);
        }

        private void mockActiveAccounts() {
                when(accountOperationsPort.getAccountInfoForTransfer(SENDER_IBAN))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
                when(accountOperationsPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                                .thenReturn(new AccountInfo(2L, 200L, "TRY", true));
        }

        private void mockSaveReturnsId() {
                when(saveTransferPort.save(any(Transfer.class))).thenAnswer(invocation -> {
                        Transfer t = invocation.getArgument(0);
                        return new Transfer(10L, t.getSenderAccountId(), t.getReceiverAccountId(),
                                        t.getAmount(), t.getStatus(), t.getCreatedAt());
                });
        }

        @Test
        void shouldThrowWhenSenderIbanAndReceiverIbanAreSame() {
                TransferRequest request = new TransferRequest(SENDER_IBAN, SENDER_IBAN,
                                new BigDecimal("200.00"), Money.Currency.TRY);

                assertThrows(SameAccountTransferException.class,
                                () -> placeTransferUseCase.execute(request));
                verifyNoInteractions(accountOperationsPort);
        }

        @Test
        void shouldThrowWhenSenderIbanAndReceiverIbanAreSameIgnoreCase() {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "tr290006200000000000000111",
                                new BigDecimal("200.00"), Money.Currency.TRY);

                assertThrows(SameAccountTransferException.class,
                                () -> placeTransferUseCase.execute(request));
                verifyNoInteractions(accountOperationsPort);
        }

        @Test
        void shouldThrowWhenSenderAccountNotActive() {
                TransferRequest request = validRequest();
                when(accountOperationsPort.getAccountInfoForTransfer(SENDER_IBAN))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", false));
                when(accountOperationsPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                                .thenReturn(new AccountInfo(2L, 200L, "TRY", true));

                assertThrows(AccountNotActiveException.class,
                                () -> placeTransferUseCase.execute(request));
                verify(accountOperationsPort, never()).debitAndCredit(anyLong(), anyLong(), any());
        }

        @Test
        void shouldThrowWhenReceiverAccountNotActive() {
                TransferRequest request = validRequest();
                when(accountOperationsPort.getAccountInfoForTransfer(SENDER_IBAN))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
                when(accountOperationsPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                                .thenReturn(new AccountInfo(2L, 200L, "TRY", false));

                assertThrows(AccountNotActiveException.class,
                                () -> placeTransferUseCase.execute(request));
                verify(accountOperationsPort, never()).debitAndCredit(anyLong(), anyLong(), any());
        }

        @Test
        void shouldPropagateInsufficientBalanceFromAccountOperations() {
                TransferRequest request = validRequest();
                mockActiveAccounts();

                doThrow(new InsufficientBalanceException("Yetersiz bakiye"))
                                .when(accountOperationsPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

                assertThrows(InsufficientBalanceException.class,
                                () -> placeTransferUseCase.execute(request));
                verify(saveTransferPort, never()).save(any());
        }

        @Test
        void shouldPropagateConcurrencyFailureFromDebitAndCredit() {
                TransferRequest request = validRequest();
                mockActiveAccounts();

                doThrow(new ConcurrencyFailureException("Optimistic lock"))
                                .when(accountOperationsPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

                assertThrows(ConcurrencyFailureException.class,
                                () -> placeTransferUseCase.execute(request));
                verify(saveTransferPort, never()).save(any());
        }

        @Test
        void shouldRollbackWhenEventPublishFails() {
                TransferRequest request = validRequest();
                mockActiveAccounts();
                mockSaveReturnsId();

                doThrow(new RuntimeException("Event publish failed"))
                                .when(eventPublisher).publishEvent(any(TransferCompletedEvent.class));

                assertThrows(RuntimeException.class, () -> placeTransferUseCase.execute(request));
        }

        @Test
        void shouldStillPublishEventWhenAuditServiceFails() {
                TransferRequest request = validRequest();
                mockActiveAccounts();
                mockSaveReturnsId();

                doThrow(new RuntimeException("Audit failed"))
                                .when(auditService).log(any(AuditAction.class), anyString());

                assertDoesNotThrow(() -> placeTransferUseCase.execute(request));
                verify(eventPublisher, times(1)).publishEvent(any(TransferCompletedEvent.class));
        }

        @Test
        void shouldCreateTransferWithCorrectStatus() {
                TransferRequest request = validRequest();
                mockActiveAccounts();
                mockSaveReturnsId();

                placeTransferUseCase.execute(request);

                ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
                verify(saveTransferPort, times(1)).save(transferCaptor.capture());
                assertEquals(TransferStatus.COMPLETED, transferCaptor.getValue().getStatus());
        }

        @Test
        void shouldReturnTransferResponseWithCorrectFields() {
                TransferRequest request = validRequest();
                mockActiveAccounts();
                mockSaveReturnsId();

                TransferResponse response = placeTransferUseCase.execute(request);

                assertNotNull(response);
                assertEquals(10L, response.id());
                assertEquals("COMPLETED", response.status());
                assertEquals(new BigDecimal("200.00"), response.amount());
                assertEquals("TRY", response.currency());
                assertEquals(SENDER_IBAN, response.senderIban());
                assertEquals(RECEIVER_IBAN, response.receiverIban());
                assertNotNull(response.createdAt());
        }

        @Test
        void shouldPublishTransferCompletedEvent() {
                TransferRequest request = validRequest();
                mockActiveAccounts();
                mockSaveReturnsId();

                placeTransferUseCase.execute(request);

                ArgumentCaptor<TransferCompletedEvent> eventCaptor = ArgumentCaptor
                                .forClass(TransferCompletedEvent.class);
                verify(eventPublisher).publishEvent(eventCaptor.capture());
                assertNotNull(eventCaptor.getValue().getTransfer());
                assertEquals(10L, eventCaptor.getValue().getTransfer().getId());
        }
}
