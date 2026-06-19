package com.bank.app.transfer.application.usecase;

import com.bank.app.audit.application.AuditService;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.exception.TransferNotFoundException;
import com.bank.app.common.security.port.SecurityContextPort;
import com.bank.app.transfer.application.port.AccountOperationsPort;
import com.bank.app.transfer.application.port.AccountOperationsPort.AccountInfo;
import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.transfer.application.port.SaveTransferPort;
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
        private AccountOperationsPort accountOperationsPort;
        @Mock
        private AuditService auditService;
        @Mock
        private SecurityContextPort securityContextPort;

        private CancelTransferUseCase cancelTransferUseCase;

        @BeforeEach
        void setUp() {
                cancelTransferUseCase = new CancelTransferUseCase(
                                loadTransferPort, saveTransferPort, accountOperationsPort,
                                auditService, securityContextPort, 24);
        }

        @Test
        void shouldThrowWhenTransferNotFound() {
                when(loadTransferPort.findByIdWithLock(999L)).thenReturn(Optional.empty());

                TransferNotFoundException ex = assertThrows(TransferNotFoundException.class,
                                () -> cancelTransferUseCase.execute(999L));
                assertEquals("Transfer bulunamadı. ID: 999", ex.getMessage());
                verifyNoInteractions(accountOperationsPort);
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
                when(accountOperationsPort.getAccountInfo(1L))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
                doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

                doThrow(new ConcurrencyFailureException("Save conflict"))
                                .when(saveTransferPort).save(any(Transfer.class));

                assertThrows(ConcurrencyFailureException.class,
                                () -> cancelTransferUseCase.execute(10L));
                verify(auditService, never()).log(any(), any());
        }

        @Test
        void shouldRollbackWhenBalanceReversalFails() {
                Transfer transfer = new Transfer(10L, 1L, 2L,
                                Money.of("200.00", Money.Currency.TRY),
                                TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

                when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
                when(accountOperationsPort.getAccountInfo(1L))
                                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
                doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

                doThrow(new RuntimeException("Balance reversal failed"))
                                .when(accountOperationsPort)
                                .reverseBalancesForCancellation(eq(1L), eq(2L), any(Money.class));

                assertThrows(RuntimeException.class,
                                () -> cancelTransferUseCase.execute(10L));

                verify(saveTransferPort, never()).save(any());
                verify(auditService, never()).log(any(), any());
        }

}
