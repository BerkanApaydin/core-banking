package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.in.GenerateTransferReportQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferViewEnricher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.bank.app.common.domain.exception.AuthorizationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class GenerateTransferReportQueryImplTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private AccountAclPort accountOperationPort;
    @Mock private TransferAuthorizationService transferAuthorizationService;
    private GenerateTransferReportQuery generateTransferReportUseCase;

    @BeforeEach
    void setUp() {
        generateTransferReportUseCase = new GenerateTransferReportQueryImpl(loadTransferPort,
                new TransferViewEnricher(accountOperationPort), transferAuthorizationService, 100);
    }

    @Test
    void shouldGenerateReportSuccessfully() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(info);
        when(accountOperationPort.getIbansForAccounts(eq(Set.of(1L, 2L, 3L)))).thenReturn(Map.of(
                1L, "TR770006200000000000000111",
                2L, "TR870006200000000000000222",
                3L, "TR970006200000000000000333"));

        Transfer t1 = new Transfer(10L, 1L, 2L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED,
                start.plusDays(1));
        Transfer t2 = new Transfer(11L, 1L, 3L, Money.of("250.00", Currency.TRY), TransferStatus.COMPLETED,
                start.plusDays(2));

        List<Transfer> transfers = Arrays.asList(t1, t2);

        // The port over-fetches one row internally; the stub returns the page.
        when(loadTransferPort.findHistoryBetween(1L, start, end, 0, 100)).thenReturn(transfers);

        TransferReportResponse response = generateTransferReportUseCase.execute(criteria);

        assertNotNull(response);
        assertEquals(1L, response.accountId());
        assertEquals(2, response.pageTransferCount());
        assertEquals(new BigDecimal("350.00"), response.pageVolume());
        assertEquals("TRY", response.currency());
        assertEquals(2, response.transfers().size());
        assertEquals(10L, response.transfers().get(0).id());
        assertEquals(11L, response.transfers().get(1).id());
        assertFalse(response.hasNext());
        verify(accountOperationPort).getIbansForAccounts(Set.of(1L, 2L, 3L));
    }

    @Test
    void fullPageDetectsNextPageFromOverfetchWithASingleQuery() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = start.plusDays(1);
        AccountInfo info = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(info);
        when(accountOperationPort.getIbansForAccounts(Set.of(1L, 2L))).thenReturn(Map.of(
                1L, "TR770006200000000000000111", 2L, "TR870006200000000000000222"));
        Transfer first = new Transfer(10L, 1L, 2L, Money.of("10.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusHours(1));
        Transfer second = new Transfer(11L, 1L, 2L, Money.of("20.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusHours(2));
        Transfer third = new Transfer(12L, 1L, 2L, Money.of("30.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusHours(3));
        // The port returns the over-fetch row; the use case trims it and needs
        // no second query for hasNext.
        when(loadTransferPort.findHistoryBetween(1L, start, end, 0, 2))
                .thenReturn(List.of(first, second, third));
        when(loadTransferPort.findHistoryBetween(1L, start, end, 1, 2))
                .thenReturn(List.of(third));

        TransferReportResponse pageOne = generateTransferReportUseCase.execute(new ReportCriteria(1L, start, end, 0, 2));
        TransferReportResponse pageTwo = generateTransferReportUseCase.execute(new ReportCriteria(1L, start, end, 1, 2));

        assertTrue(pageOne.hasNext());
        assertEquals(2, pageOne.pageTransferCount());
        assertEquals(new BigDecimal("30.00"), pageOne.pageVolume());
        assertFalse(pageTwo.hasNext());
        assertEquals(1, pageTwo.pageTransferCount());
        assertEquals(new BigDecimal("30.00"), pageTwo.pageVolume());
        verify(loadTransferPort, times(1)).findHistoryBetween(1L, start, end, 0, 2);
        verify(loadTransferPort, times(1)).findHistoryBetween(1L, start, end, 1, 2);
        verifyNoMoreInteractions(loadTransferPort);
    }

    @Test
    void shouldGenerateEmptyReportWhenNoTransfersFound() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        AccountInfo info = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(info);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Map.of());

        when(loadTransferPort.findHistoryBetween(1L, start, end, 0, 100))
                .thenReturn(Collections.emptyList());

        TransferReportResponse response = generateTransferReportUseCase.execute(criteria);

        assertNotNull(response);
        assertEquals(1L, response.accountId());
        assertEquals(0, response.pageTransferCount());
        assertEquals(BigDecimal.ZERO, response.pageVolume());
        assertEquals("TRY", response.currency());
        assertTrue(response.transfers().isEmpty());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenUserIsNotOwnerOfAccount() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);

        doThrow(new AuthorizationException("You are not authorized to generate a report for this account."))
                .when(transferAuthorizationService).authorizeAccountAccess(eq(1L), anyString());

        AuthorizationException exception = assertThrows(AuthorizationException.class, () -> generateTransferReportUseCase.execute(criteria));
        assertEquals("You are not authorized to generate a report for this account.", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenStartDateIsAfterEndDate() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new ReportCriteria(1L, start, end));
        assertEquals("Start date must not be after end date", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDateRangeExceeds12Months() {
        LocalDateTime start = LocalDateTime.now().minusMonths(13);
        LocalDateTime end = LocalDateTime.now();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new ReportCriteria(1L, start, end));
        assertEquals("Report range must not exceed 12 months", exception.getMessage());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenCriteriaIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> generateTransferReportUseCase.execute(null));
        assertEquals("Criteria must not be null", exception.getMessage());
    }

    @Test
    void shouldFallBackToDefaultPageSizeWhenMisconfigured() {
        GenerateTransferReportQuery misconfigured = new GenerateTransferReportQueryImpl(loadTransferPort,
                new TransferViewEnricher(accountOperationPort), transferAuthorizationService, 0);
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        AccountInfo info = new AccountInfo(1L, 100L, "TRY", "ACTIVE");

        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(info);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Map.of());
        when(loadTransferPort.findHistoryBetween(eq(1L), eq(start), eq(end), eq(0), eq(100)))
                .thenReturn(Collections.emptyList());

        misconfigured.execute(new ReportCriteria(1L, start, end));

        verify(loadTransferPort).findHistoryBetween(1L, start, end, 0, 100);
    }

    @Test
    void shouldReportNoNextPageWhenExactlyOnePageReturned() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = start.plusDays(1);
        AccountInfo info = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(info);
        when(accountOperationPort.getIbansForAccounts(eq(Set.of(1L, 2L)))).thenReturn(Map.of(
                1L, "TR770006200000000000000111", 2L, "TR870006200000000000000222"));
        Transfer first = new Transfer(10L, 1L, 2L, Money.of("10.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusHours(1));
        Transfer second = new Transfer(11L, 1L, 2L, Money.of("20.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusHours(2));
        when(loadTransferPort.findHistoryBetween(1L, start, end, 0, 2))
                .thenReturn(List.of(first, second));

        TransferReportResponse response =
                generateTransferReportUseCase.execute(new ReportCriteria(1L, start, end, 0, 2));

        assertFalse(response.hasNext());
        assertEquals(2, response.pageTransferCount());
        assertNull(response.nextCursorCreatedAt());
        assertNull(response.nextCursorId());
    }
}
