package com.bank.app.transfer.application.usecase;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.account.domain.exception.InsufficientBalanceException;
import com.bank.app.transfer.domain.exception.SameAccountTransferException;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
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
import com.bank.app.common.application.port.out.EventPublisherPort;
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
        private EventPublisherPort eventPublisherPort;

        private PlaceTransferUseCase placeTransferUseCase;

        private static final String SENDER_IBAN = "TR290006200000000000000111";
        private static final String RECEIVER_IBAN = "TR290006200000000000000222";

        @BeforeEach
        void setUp() {
                placeTransferUseCase = new PlaceTransferUseCaseImpl(accountOperationPort, saveTransferPort,
                                eventPublisherPort, new TransferDomainService());
        }

        private TransferRequest validRequest() {
                return new TransferRequest(SENDER_IBAN, RECEIVER_IBAN,
                                new BigDecimal("200.00"), Currency.TRY);
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
                                new BigDecimal("200.00"), Currency.TRY);

                when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));

                assertThrows(SameAccountTransferException.class,
                                () -> placeTransferUseCase.execute(request));
                verify(accountOperationPort, never()).debitAndCredit(anyLong(), anyLong(), any());
        }

        @Test
        void shouldThrowWhenSenderIbanAndReceiverIbanAreSameIgnoreCase() {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "tr290006200000000000000111",
                                new BigDecimal("200.00"), Currency.TRY);

                when(accountOperationPort.getAccountInfoForTransfer("TR290006200000000000000111"))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));

                assertThrows(SameAccountTransferException.class,
                                () -> placeTransferUseCase.execute(request));
                verify(accountOperationPort, never()).debitAndCredit(anyLong(), anyLong(), any());
        }

        @Test
        void shouldThrowWhenSenderAccountNotActive() {
                TransferRequest request = validRequest();
                when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", false));
                when(accountOperationPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                                .thenReturn(new AccountInfo(2L, 200L, "TRY", true));

                doThrow(new com.bank.app.account.domain.exception.AccountNotActiveException(SENDER_IBAN))
                                .when(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

                assertThrows(com.bank.app.account.domain.exception.AccountNotActiveException.class,
                                () -> placeTransferUseCase.execute(request));
                verify(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));
        }

        @Test
        void shouldThrowWhenReceiverAccountNotActive() {
                TransferRequest request = validRequest();
                when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
                when(accountOperationPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                                .thenReturn(new AccountInfo(2L, 200L, "TRY", false));

                doThrow(new com.bank.app.account.domain.exception.AccountNotActiveException(RECEIVER_IBAN))
                                .when(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

                assertThrows(com.bank.app.account.domain.exception.AccountNotActiveException.class,
                                () -> placeTransferUseCase.execute(request));
                verify(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));
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
                assertNotNull(eventCaptor.getValue().getTransferId());
                assertEquals(10L, eventCaptor.getValue().getTransferId());
        }

        @Test
        void shouldHandleIbanWithSpacesByNormalizing() {
                TransferRequest request = new TransferRequest(
                                "TR29 0006 2000 0000 0000 0001 11",
                                "TR29 0006 2000 0000 0000 0002 22",
                                new BigDecimal("200.00"), Currency.TRY);

                when(accountOperationPort.getAccountInfoForTransfer("TR290006200000000000000111"))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
                when(accountOperationPort.getAccountInfoForTransfer("TR290006200000000000000222"))
                                .thenReturn(new AccountInfo(2L, 200L, "TRY", true));
                mockSaveReturnsId();

                TransferResponse response = placeTransferUseCase.execute(request);

                assertNotNull(response);
                assertEquals(10L, response.id());
                assertEquals("COMPLETED", response.status());
                verify(accountOperationPort).getAccountInfoForTransfer("TR290006200000000000000111");
                verify(accountOperationPort).getAccountInfoForTransfer("TR290006200000000000000222");
        }

        @Test
        void shouldHandleIbanWithMixedCaseAndSpacesByNormalizing() {
                TransferRequest request = new TransferRequest(
                                "tr29 0006 2000 0000 0000 0001 11",
                                "TR29 0006 2000 0000 0000 0002 22",
                                new BigDecimal("200.00"), Currency.TRY);

                when(accountOperationPort.getAccountInfoForTransfer("TR290006200000000000000111"))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
                when(accountOperationPort.getAccountInfoForTransfer("TR290006200000000000000222"))
                                .thenReturn(new AccountInfo(2L, 200L, "TRY", true));
                mockSaveReturnsId();

                TransferResponse response = placeTransferUseCase.execute(request);

                assertNotNull(response);
                assertEquals(10L, response.id());
        }
}
