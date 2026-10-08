package com.bank.app.transfer.application.usecase;

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
import com.bank.app.transfer.application.service.TransferViewEnricher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class GenerateTransferReportWithTotalsQueryImplTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private AccountAclPort accountAclPort;
    @Mock private TransferAuthorizationService transferAuthorizationService;
    private GenerateTransferReportWithTotalsQueryImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateTransferReportWithTotalsQueryImpl(loadTransferPort,
                new TransferViewEnricher(accountAclPort), transferAuthorizationService, 100);
    }

    @Test
    void shouldReturnItemsAndWholeRangeTotalsInOneCall() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = start.plusDays(1);
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString()))
                .thenReturn(new AccountInfo(1L, 100L, "TRY", "ACTIVE"));
        when(accountAclPort.getIbansForAccounts(eq(Set.of(1L, 2L)))).thenReturn(Map.of(
                1L, "TR770006200000000000000111", 2L, "TR870006200000000000000222"));
        Transfer t1 = new Transfer(10L, 1L, 2L, Money.of("100.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusHours(1));
        Transfer t2 = new Transfer(11L, 1L, 2L, Money.of("250.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusHours(2));
        when(loadTransferPort.findHistoryBetween(1L, start, end, 0, 100))
                .thenReturn(List.of(t1, t2));
        when(loadTransferPort.summarizeRange(1L, start, end))
                .thenReturn(new LoadTransferPort.ReportTotals(2, new BigDecimal("350.00")));

        TransferReportResponse response =
                useCase.execute(new ReportCriteria(1L, start, end));

        assertEquals(2, response.pageTransferCount());
        assertEquals(new BigDecimal("350.00"), response.pageVolume());
        assertEquals(2L, response.totalCount());
        assertEquals(new BigDecimal("350.00"), response.totalVolume());
        assertFalse(response.hasNext());
        assertNull(response.nextCursorCreatedAt());
        verify(loadTransferPort).summarizeRange(1L, start, end);
    }

    @Test
    void shouldServeKeysetCursorWithNextCursor() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = start.plusDays(1);
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString()))
                .thenReturn(new AccountInfo(1L, 100L, "TRY", "ACTIVE"));
        when(accountAclPort.getIbansForAccounts(eq(Set.of(1L, 2L)))).thenReturn(Map.of(
                1L, "TR770006200000000000000111", 2L, "TR870006200000000000000222"));
        Transfer first = new Transfer(10L, 1L, 2L, Money.of("10.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusHours(1));
        Transfer second = new Transfer(11L, 1L, 2L, Money.of("20.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusHours(2));
        Transfer third = new Transfer(12L, 1L, 2L, Money.of("30.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusHours(3));
        // Explicit first-page cursor (the null/null pair is rejected by the invariant).
        ReportCriteria keyset = new ReportCriteria(1L, start, end, 0, 2,
                start.plusHours(0), 0L);
        when(loadTransferPort.findHistoryBetweenKeyset(eq(1L), eq(start), eq(end),
                eq(start.plusHours(0)), eq(0L), eq(2))).thenReturn(List.of(first, second, third));
        when(loadTransferPort.summarizeRange(1L, start, end))
                .thenReturn(new LoadTransferPort.ReportTotals(3, new BigDecimal("60.00")));

        TransferReportResponse response = useCase.execute(keyset);

        assertTrue(response.hasNext());
        assertEquals(2, response.pageTransferCount());
        assertEquals(11L, response.nextCursorId());
        assertNotNull(response.nextCursorCreatedAt());
        assertEquals(3L, response.totalCount());
    }

    @Test
    void shouldReportNoNextPageWhenExactlyOnePageReturned() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = start.plusDays(1);
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString()))
                .thenReturn(new AccountInfo(1L, 100L, "TRY", "ACTIVE"));
        when(accountAclPort.getIbansForAccounts(eq(Set.of(1L, 2L)))).thenReturn(Map.of(
                1L, "TR770006200000000000000111", 2L, "TR870006200000000000000222"));
        Transfer first = new Transfer(10L, 1L, 2L, Money.of("10.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusHours(1));
        Transfer second = new Transfer(11L, 1L, 2L, Money.of("20.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusHours(2));
        when(loadTransferPort.findHistoryBetween(1L, start, end, 0, 2))
                .thenReturn(List.of(first, second));
        when(loadTransferPort.summarizeRange(1L, start, end))
                .thenReturn(new LoadTransferPort.ReportTotals(2, new BigDecimal("30.00")));

        TransferReportResponse response =
                useCase.execute(new ReportCriteria(1L, start, end, 0, 2));

        assertFalse(response.hasNext());
        assertNull(response.nextCursorCreatedAt());
        assertNull(response.nextCursorId());
        assertEquals(2L, response.totalCount());
    }

    @Test
    void shouldFallBackToDefaultPageSizeWhenMisconfigured() {
        // Kills both <init> boundary mutants: only maxPageSize=0 distinguishes
        // `> 0` (→100) from `>= 0` / negation (→size 1 via the lower max() bound).
        GenerateTransferReportWithTotalsQueryImpl zeroMax = new GenerateTransferReportWithTotalsQueryImpl(
                loadTransferPort, new TransferViewEnricher(accountAclPort),
                transferAuthorizationService, 0);
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = start.plusDays(1);
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString()))
                .thenReturn(new AccountInfo(1L, 100L, "TRY", "ACTIVE"));
        when(loadTransferPort.findHistoryBetween(eq(1L), eq(start), eq(end), eq(0), eq(100)))
                .thenReturn(List.of());
        when(loadTransferPort.summarizeRange(1L, start, end))
                .thenReturn(new LoadTransferPort.ReportTotals(0, BigDecimal.ZERO));

        TransferReportResponse response =
                zeroMax.execute(new ReportCriteria(1L, start, end, 0, 100));

        assertEquals(0, response.pageTransferCount());
        verify(loadTransferPort).findHistoryBetween(eq(1L), eq(start), eq(end), eq(0), eq(100));
    }
}
