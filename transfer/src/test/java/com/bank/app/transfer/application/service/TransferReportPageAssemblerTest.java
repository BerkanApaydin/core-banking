package com.bank.app.transfer.application.service;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.bank.app.common.domain.AccountId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class TransferReportPageAssemblerTest {

    @Mock
    private LoadTransferPort loadTransferPort;
    @Mock
    private AccountAclPort accountAclPort;

    @Test
    void shouldClampSizeToMaxPageSize() {
        assertEquals(100, TransferReportPageAssembler.clampSize(500, 100));
        assertEquals(1, TransferReportPageAssembler.clampSize(0, 100));
        assertEquals(10, TransferReportPageAssembler.clampSize(10, 0));
        assertEquals(25, TransferReportPageAssembler.clampSize(25, 100));
    }

    @Test
    void shouldAssembleOffsetPageWithCursor() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end, 0, 2);
        TransferViewEnricher enricher = new TransferViewEnricher(accountAclPort);

        Transfer t1 = new Transfer(10L, new AccountId(1L), new AccountId(2L), Money.of("100.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusDays(1));
        Transfer t2 = new Transfer(11L, new AccountId(1L), new AccountId(3L), Money.of("250.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusDays(2));
        Transfer extra = new Transfer(12L, new AccountId(1L), new AccountId(2L), Money.of("50.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusDays(3));

        when(loadTransferPort.findHistoryBetween(1L, start, end, 0, 2))
                .thenReturn(List.of(t1, t2, extra));
        when(accountAclPort.getIbansForAccounts(any())).thenReturn(Map.of(
                1L, "TR770006200000000000000111",
                2L, "TR870006200000000000000222",
                3L, "TR970006200000000000000333"));

        TransferReportPageAssembler.ReportPage page =
                TransferReportPageAssembler.assemble(loadTransferPort, enricher, criteria, 2);

        assertTrue(page.hasNext());
        assertEquals(2, page.transfers().size());
        assertEquals(new BigDecimal("350.00"), page.pageVolume());
        assertEquals(2, page.responses().size());
        assertNotNull(page.nextCursorCreatedAt());
        assertEquals(11L, page.nextCursorId());
    }

    @Test
    void shouldAssembleKeysetFirstPageWithoutNext() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        LocalDateTime end = LocalDateTime.now();
        ReportCriteria criteria = new ReportCriteria(1L, start, end);
        TransferViewEnricher enricher = new TransferViewEnricher(accountAclPort);

        Transfer t1 = new Transfer(10L, new AccountId(1L), new AccountId(2L), Money.of("100.00", Currency.TRY),
                TransferStatus.COMPLETED, start.plusDays(1));

        when(loadTransferPort.findHistoryBetween(eq(1L), eq(start), eq(end), eq(0), eq(100)))
                .thenReturn(List.of(t1));
        when(accountAclPort.getIbansForAccounts(eq(Set.of(1L, 2L)))).thenReturn(Map.of(
                1L, "TR770006200000000000000111",
                2L, "TR870006200000000000000222"));

        TransferReportPageAssembler.ReportPage page =
                TransferReportPageAssembler.assemble(loadTransferPort, enricher, criteria, 100);

        assertFalse(page.hasNext());
        assertEquals(1, page.transfers().size());
        assertNull(page.nextCursorCreatedAt());
        assertNull(page.nextCursorId());
    }
}
