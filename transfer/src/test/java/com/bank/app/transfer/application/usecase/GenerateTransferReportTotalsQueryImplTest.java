package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportTotalsResponse;
import com.bank.app.transfer.application.port.in.GenerateTransferReportTotalsQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.common.domain.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class GenerateTransferReportTotalsQueryImplTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private TransferAuthorizationService transferAuthorizationService;
    private GenerateTransferReportTotalsQuery totalsQuery;

    @BeforeEach
    void setUp() {
        totalsQuery = new GenerateTransferReportTotalsQueryImpl(loadTransferPort, transferAuthorizationService);
    }

    @Test
    void shouldSummarizeWholeRangeInOneScan() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString()))
                .thenReturn(new AccountInfo(1L, 100L, "TRY", "ACTIVE"));
        when(loadTransferPort.summarizeRange(1L, start, end))
                .thenReturn(new LoadTransferPort.ReportTotals(7, new BigDecimal("1234.50")));

        TransferReportTotalsResponse response =
                totalsQuery.execute(new ReportCriteria(1L, start, end));

        assertEquals(1L, response.accountId());
        assertEquals(7, response.totalTransferCount());
        assertEquals(new BigDecimal("1234.50"), response.totalVolume());
        assertEquals("TRY", response.currency());
        verify(loadTransferPort).summarizeRange(1L, start, end);
    }

    @Test
    void shouldRejectInvertedRange() {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.minusDays(1);

        assertThrows(IllegalArgumentException.class,
                () -> totalsQuery.execute(new ReportCriteria(1L, start, end)));
        verifyNoInteractions(loadTransferPort);
    }

    @Test
    void shouldRejectRangeOverTwelveMonths() {
        LocalDateTime start = LocalDateTime.now().minusMonths(13);
        LocalDateTime end = LocalDateTime.now();

        assertThrows(IllegalArgumentException.class,
                () -> totalsQuery.execute(new ReportCriteria(1L, start, end)));
        verifyNoInteractions(loadTransferPort);
    }

    @Test
    void shouldPropagateAuthorizationDenial() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString()))
                .thenThrow(new AuthorizationException("Denied."));

        assertThrows(AuthorizationException.class,
                () -> totalsQuery.execute(new ReportCriteria(1L, start, end)));
        verifyNoInteractions(loadTransferPort);
    }

    @Test
    void shouldRejectNullCriteria() {
        assertThrows(NullPointerException.class, () -> totalsQuery.execute(null));
        verifyNoInteractions(loadTransferPort, transferAuthorizationService);
    }
}
