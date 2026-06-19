package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.exception.TransferNotFoundException;
import com.bank.app.common.security.port.out.SecurityContextPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.ConcurrencyFailureException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelTransferUseCaseEdgeCaseTest {

        @Mock
        private LoadTransferPort loadTransferPort;
        @Mock
        private SaveTransferPort saveTransferPort;
        @Mock
        private AccountOperationPort accountOperationPort;
        @Mock
        private EventPublisherPort eventPublisherPort;
        @Mock
        private SecurityContextPort securityContextPort;

        private CancelTransferUseCase cancelTransferUseCase;

        @BeforeEach
        void setUp() {
                cancelTransferUseCase = new CancelTransferUseCase(
                                loadTransferPort, saveTransferPort, accountOperationPort,
                                eventPublisherPort, securityContextPort, 24);
        }

        @Test
        void shouldThrowWhenTransferNotFound() {
                when(loadTransferPort.findByIdWithLock(999L)).thenReturn(Optional.empty());

                TransferNotFoundException ex = assertThrows(TransferNotFoundException.class,
                                () -> cancelTransferUseCase.execute(999L));
                assertTrue(ex.getMessage().contains("Transfer bulunamad"));
                assertTrue(ex.getMessage().contains("ID: 999"));
                verifyNoInteractions(accountOperationPort);
        }

        @Test
        void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
                assertThrows(NullPointerException.class,
                                () -> cancelTransferUseCase.execute(null));
        }

        @Test
        void shouldPropagateConcurrencyFailureFromSave() {
                Transfer transfer = new Transfer(10L, 1L, 2L,
                                Money.of("200.00", Money.Currency.TRY),
                                TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

                when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
                when(accountOperationPort.getAccountInfo(1L))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
                doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

                doThrow(new ConcurrencyFailureException("Save conflict"))
                                .when(saveTransferPort).save(any(Transfer.class));

                assertThrows(ConcurrencyFailureException.class,
                                () -> cancelTransferUseCase.execute(10L));
                verify(eventPublisherPort, never()).publish(any());
        }

        @Test
        void shouldRollbackWhenBalanceReversalFails() {
                Transfer transfer = new Transfer(10L, 1L, 2L,
                                Money.of("200.00", Money.Currency.TRY),
                                TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

                when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
                when(accountOperationPort.getAccountInfo(1L))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
                doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

                doThrow(new RuntimeException("Balance reversal failed"))
                                .when(accountOperationPort)
                                .reverseBalancesForCancellation(eq(1L), eq(2L), any(Money.class));

                assertThrows(RuntimeException.class,
                                () -> cancelTransferUseCase.execute(10L));

                verify(saveTransferPort, never()).save(any());
                verify(eventPublisherPort, never()).publish(any());
        }

}
