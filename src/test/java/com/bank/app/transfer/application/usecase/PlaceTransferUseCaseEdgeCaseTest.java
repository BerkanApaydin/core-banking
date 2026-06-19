package com.bank.app.transfer.application.usecase;

import com.bank.app.audit.application.AuditLogger;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.common.domain.Money;
import com.bank.app.account.exception.AccountNotActiveException;
import com.bank.app.account.exception.InsufficientBalanceException;
import com.bank.app.transfer.exception.SameAccountTransferException;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort.AccountInfo;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
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
import com.bank.app.transfer.application.port.out.DomainEventPublisherPort;
import org.springframework.dao.ConcurrencyFailureException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceTransferUseCaseEdgeCaseTest {

        @Mock
        private AccountOperationPort accountOperationPort;
        @Mock
        private SaveTransferPort saveTransferPort;
        @Mock
        private DomainEventPublisherPort eventPublisherPort;
        @Mock
        private AuditLogger auditLogger;

        private PlaceTransferUseCase placeTransferUseCase;

        private static final String SENDER_IBAN = "TR290006200000000000000111";
        private static final String RECEIVER_IBAN = "TR290006200000000000000222";

        @BeforeEach
        void setUp() {
                placeTransferUseCase = new PlaceTransferUseCase(accountOperationPort, saveTransferPort, auditLogger,
                                eventPublisherPort, new TransferDomainService());
        }

        private TransferRequest validRequest() {
                return new TransferRequest(SENDER_IBAN, RECEIVER_IBAN,
                                new BigDecimal("200.00"), Money.Currency.TRY);
        }

        private void mockActiveAccounts() {
                when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
                when(accountOperationPort.getAccountInfoForTransfer(RECEIVER_IBAN))
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
                verifyNoInteractions(accountOperationPort);
        }

        @Test
        void shouldThrowWhenSenderIbanAndReceiverIbanAreSameIgnoreCase() {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "tr290006200000000000000111",
                                new BigDecimal("200.00"), Money.Currency.TRY);

                assertThrows(SameAccountTransferException.class,
                                () -> placeTransferUseCase.execute(request));
                verifyNoInteractions(accountOperationPort);
        }

        @Test
        void shouldThrowWhenSenderAccountNotActive() {
                TransferRequest request = validRequest();
                when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", false));
                when(accountOperationPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                                .thenReturn(new AccountInfo(2L, 200L, "TRY", true));

                assertThrows(AccountNotActiveException.class,
                                () -> placeTransferUseCase.execute(request));
                verify(accountOperationPort, never()).debitAndCredit(anyLong(), anyLong(), any());
        }

        @Test
        void shouldThrowWhenReceiverAccountNotActive() {
                TransferRequest request = validRequest();
                when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
                when(accountOperationPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                                .thenReturn(new AccountInfo(2L, 200L, "TRY", false));

                assertThrows(AccountNotActiveException.class,
                                () -> placeTransferUseCase.execute(request));
                verify(accountOperationPort, never()).debitAndCredit(anyLong(), anyLong(), any());
        }

        @Test
        void shouldPropagateInsufficientBalanceFromAccountOperations() {
                TransferRequest request = validRequest();
                mockActiveAccounts();

                doThrow(new InsufficientBalanceException("Yetersiz bakiye"))
                                .when(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

                assertThrows(InsufficientBalanceException.class,
                                () -> placeTransferUseCase.execute(request));
                verify(saveTransferPort, never()).save(any());
        }

        @Test
        void shouldPropagateConcurrencyFailureFromDebitAndCredit() {
                TransferRequest request = validRequest();
                mockActiveAccounts();

                doThrow(new ConcurrencyFailureException("Optimistic lock"))
                                .when(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

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
                                .when(eventPublisherPort).publish(any(TransferCompletedEvent.class));

                assertThrows(RuntimeException.class, () -> placeTransferUseCase.execute(request));
        }

        @Test
        void shouldStillPublishEventWhenAuditServiceFails() {
                TransferRequest request = validRequest();
                mockActiveAccounts();
                mockSaveReturnsId();

                doThrow(new RuntimeException("Audit failed"))
                                .when(auditLogger).log(any(AuditAction.class), anyString());

                assertDoesNotThrow(() -> placeTransferUseCase.execute(request));
                verify(eventPublisherPort, times(1)).publish(any(TransferCompletedEvent.class));
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
                verify(eventPublisherPort).publish(eventCaptor.capture());
                assertNotNull(eventCaptor.getValue().getTransfer());
                assertEquals(10L, eventCaptor.getValue().getTransfer().getId());
        }
}
