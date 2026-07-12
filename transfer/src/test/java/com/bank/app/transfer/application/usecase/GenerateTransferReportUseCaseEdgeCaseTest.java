package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.domain.exception.AccountNotFoundException;
import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class GenerateTransferReportUseCaseEdgeCaseTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private AccountAclPort accountOperationPort;
    @Mock private TransferAuthorizationService transferAuthorizationService;

    private GenerateTransferReportQuery generateTransferReportUseCase;

    @BeforeEach
    void setUp() {
        generateTransferReportUseCase = new GenerateTransferReportUseCaseImpl(
                loadTransferPort, accountOperationPort, transferAuthorizationService);
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenAccountDoesNotExist() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(999L, start, end);

        when(transferAuthorizationService.authorizeAccountAccess(eq(999L), anyString()))
                .thenThrow(new AccountNotFoundException(999L));

        assertThrows(AccountNotFoundException.class,
                () -> generateTransferReportUseCase.execute(criteria));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenCriteriaIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> generateTransferReportUseCase.execute(null));
        assertEquals("Criteria must not be null", ex.getMessage());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAccountIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new ReportCriteria(null, LocalDateTime.now().minusDays(5), LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenStartDateIsNull() {
        assertThrows(NullPointerException.class,
                () -> new ReportCriteria(1L, null, LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenEndDateIsNull() {
        assertThrows(NullPointerException.class,
                () -> new ReportCriteria(1L, LocalDateTime.now(), null));
    }

    @Test
    void shouldThrowWhenStartDateIsAfterEndDate() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> generateTransferReportUseCase.execute(criteria));
        assertEquals("Start date must not be after end date.", ex.getMessage());
    }

    @Test
    void shouldReturnEmptyReportWhenStartDateEqualsEndDate() {
        LocalDateTime now = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, now, now);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(info);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
        when(loadTransferPort.findHistoryBetween(eq(1L), eq(now), eq(now), eq(0), eq(100)))
                .thenReturn(Collections.emptyList());

        TransferReportResponse response = generateTransferReportUseCase.execute(criteria);
        assertNotNull(response);
        assertEquals(0, response.totalTransfersCount());
    }

    @Test
    void shouldThrowWhenDateRangeExceeds12Months() {
        LocalDateTime start = LocalDateTime.now().minusMonths(12).minusDays(1);
        LocalDateTime end = LocalDateTime.now();

        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> generateTransferReportUseCase.execute(criteria));
        assertEquals("Report range must be at most 12 months.", ex.getMessage());
    }

    @Test
    void shouldAllowExactly12MonthsRange() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusMonths(12);
        LocalDateTime end = now;

        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(info);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
        when(loadTransferPort.findHistoryBetween(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(100)))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> generateTransferReportUseCase.execute(criteria));
    }

    @Test
    void shouldReturnEmptyReportWhenAccountHasNoTransfers() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(info);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
        when(loadTransferPort.findHistoryBetween(eq(1L), eq(start), eq(end), eq(0), eq(100)))
                .thenReturn(Collections.emptyList());

        TransferReportResponse response = generateTransferReportUseCase.execute(criteria);

        assertNotNull(response);
        assertEquals(1L, response.accountId());
        assertEquals(0, response.totalTransfersCount());
        assertEquals(BigDecimal.ZERO, response.totalVolume());
        assertEquals("TRY", response.currency());
        assertTrue(response.transfers().isEmpty());
    }

    @Test
    void shouldReturnReportWithFutureDates() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(10);
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(info);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
        when(loadTransferPort.findHistoryBetween(eq(1L), eq(start), eq(end), eq(0), eq(100)))
                .thenReturn(Collections.emptyList());

        TransferReportResponse response = generateTransferReportUseCase.execute(criteria);
        assertEquals(0, response.totalTransfersCount());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenNotAuthorized() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        doThrow(new AccessDeniedException("You are not authorized to generate a report for this account."))
                .when(transferAuthorizationService).authorizeAccountAccess(eq(1L), anyString());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> generateTransferReportUseCase.execute(criteria));
        assertEquals("You are not authorized to generate a report for this account.", ex.getMessage());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenNotLoggedIn() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        doThrow(new AccessDeniedException("Session not found."))
                .when(transferAuthorizationService).authorizeAccountAccess(eq(1L), anyString());

        assertThrows(AccessDeniedException.class,
                () -> generateTransferReportUseCase.execute(criteria));
    }

    @Test
    void shouldHaveZeroTotalVolumeWhenNoTransfers() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(info);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
        when(loadTransferPort.findHistoryBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        TransferReportResponse response = generateTransferReportUseCase.execute(criteria);
        assertEquals(BigDecimal.ZERO, response.totalVolume());
    }
}
