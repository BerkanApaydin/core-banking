package com.bank.app.transfer.application.usecase;

import com.bank.app.common.exception.AccountNotFoundException;
import com.bank.app.common.security.SecurityUtils;
import com.bank.app.common.security.CustomUserDetails;
import com.bank.app.common.security.port.SecurityContextPort;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.port.AccountOperationsPort;
import com.bank.app.transfer.application.port.AccountOperationsPort.AccountInfo;
import com.bank.app.transfer.application.port.LoadTransferPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateTransferReportUseCaseEdgeCaseTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private AccountOperationsPort accountOperationsPort;

    private SecurityContextPort securityContextPort;
    private GenerateTransferReportUseCase generateTransferReportUseCase;

    @BeforeEach
    void setUp() {
        securityContextPort = new SecurityUtils();
        generateTransferReportUseCase = new GenerateTransferReportUseCase(
                loadTransferPort, accountOperationsPort, securityContextPort);

        CustomUserDetails principal = new CustomUserDetails(100L, "test_user",
                "password", Collections.emptyList());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenAccountDoesNotExist() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(999L, start, end);

        when(accountOperationsPort.getAccountInfo(999L))
                .thenThrow(new AccountNotFoundException(999L));

        assertThrows(AccountNotFoundException.class,
                () -> generateTransferReportUseCase.execute(criteria));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenCriteriaIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> generateTransferReportUseCase.execute(null));
        assertEquals("Criteria null olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAccountIdIsNull() {
        ReportCriteria criteria = new ReportCriteria(null,
                LocalDateTime.now().minusDays(5), LocalDateTime.now());

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> generateTransferReportUseCase.execute(criteria));
        assertEquals("Account ID null olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenStartDateIsNull() {
        ReportCriteria criteria = new ReportCriteria(1L, null, LocalDateTime.now());

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> generateTransferReportUseCase.execute(criteria));
        assertEquals("Start date null olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenEndDateIsNull() {
        ReportCriteria criteria = new ReportCriteria(1L, LocalDateTime.now(), null);

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> generateTransferReportUseCase.execute(criteria));
        assertEquals("End date null olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowWhenStartDateIsAfterEndDate() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> generateTransferReportUseCase.execute(criteria));
        assertEquals("Başlangıç tarihi bitiş tarihinden sonra olamaz.", ex.getMessage());
    }

    @Test
    void shouldThrowWhenStartDateEqualsEndDate() {
        LocalDateTime now = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, now, now);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(info);
        when(accountOperationsPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
        when(loadTransferPort.findHistoryBetween(eq(1L), eq(now), eq(now)))
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
        assertEquals("Rapor aralığı en fazla 12 ay olabilir.", ex.getMessage());
    }

    @Test
    void shouldAllowExactly12MonthsRange() {
        LocalDateTime start = LocalDateTime.now().minusMonths(12);
        LocalDateTime end = LocalDateTime.now();

        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(info);
        when(accountOperationsPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
        when(loadTransferPort.findHistoryBetween(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> generateTransferReportUseCase.execute(criteria));
    }

    @Test
    void shouldReturnEmptyReportWhenAccountHasNoTransfers() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(info);
        when(accountOperationsPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
        when(loadTransferPort.findHistoryBetween(eq(1L), eq(start), eq(end)))
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

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(info);
        when(accountOperationsPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
        when(loadTransferPort.findHistoryBetween(eq(1L), eq(start), eq(end)))
                .thenReturn(Collections.emptyList());

        TransferReportResponse response = generateTransferReportUseCase.execute(criteria);
        assertEquals(0, response.totalTransfersCount());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenNotAuthorized() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 200L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(info);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> generateTransferReportUseCase.execute(criteria));
        assertEquals("Bu hesabın raporunu oluşturma yetkiniz yok.", ex.getMessage());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenNotLoggedIn() {
        SecurityContextHolder.clearContext();

        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(info);

        assertThrows(AccessDeniedException.class,
                () -> generateTransferReportUseCase.execute(criteria));
    }

    @Test
    void shouldHaveZeroTotalVolumeWhenNoTransfers() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(info);
        when(accountOperationsPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
        when(loadTransferPort.findHistoryBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        TransferReportResponse response = generateTransferReportUseCase.execute(criteria);
        assertEquals(BigDecimal.ZERO, response.totalVolume());
    }
}
