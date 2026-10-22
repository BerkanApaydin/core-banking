package com.bank.app.transfer.application.service;

import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Keyset branch of {@link TransferReportPageAssembler}.
 *
 * <p>The assembler's two fetch branches are the offset path (no cursor) and
 * the keyset path (cursor present). A cursor scroll must neither duplicate nor
 * skip a transfer across pages, and the cursor it hands out must point at the
 * last row it actually returned.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
@DisplayName("TransferReportPageAssembler keyset pagination")
class TransferReportPageAssemblerKeysetTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final int PAGE_SIZE = 2;
    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 6, 30, 0, 0);

    @Mock
    private LoadTransferPort loadTransferPort;
    @Mock
    private AccountAclPort accountAclPort;

    private TransferViewEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new TransferViewEnricher(accountAclPort);
        when(accountAclPort.getIbansForAccounts(any())).thenReturn(Map.of(
                1L, "TR770006200000000000000111",
                2L, "TR870006200000000000000222"));
    }

    private static Transfer transfer(long id, String amount, LocalDateTime createdAt) {
        return new Transfer(id, new AccountId(1L), new AccountId(2L),
                Money.of(amount, Currency.TRY), TransferStatus.COMPLETED, createdAt);
    }

    /** Rows strictly after the (createdAt, id) cursor, in business order. */
    private static List<Transfer> afterCursor(List<Transfer> all, LocalDateTime cursorCreatedAt, Long cursorId) {
        List<Transfer> remaining = new ArrayList<>();
        for (Transfer candidate : all) {
            boolean after = candidate.getCreatedAt().isAfter(cursorCreatedAt)
                    || (candidate.getCreatedAt().isEqual(cursorCreatedAt) && candidate.getId() > cursorId);
            if (after) {
                remaining.add(candidate);
            }
        }
        return remaining;
    }

    @Test
    @DisplayName("should walk every row exactly once across keyset pages")
    void shouldWalkEveryRowExactlyOnceAcrossKeysetPages() {
        List<Transfer> all = List.of(
                transfer(10L, "100.00", START.plusDays(1)),
                transfer(11L, "250.00", START.plusDays(2)),
                transfer(12L, "50.00", START.plusDays(3)),
                transfer(13L, "75.00", START.plusDays(4)),
                transfer(14L, "25.00", START.plusDays(5)));

        when(loadTransferPort.findHistoryBetween(eq(ACCOUNT_ID), eq(START), eq(END), eq(0), eq(PAGE_SIZE)))
                .thenAnswer(invocation -> all.subList(0, PAGE_SIZE + 1));
        when(loadTransferPort.findHistoryBetweenKeyset(eq(ACCOUNT_ID), eq(START), eq(END), any(), any(), eq(PAGE_SIZE)))
                .thenAnswer(invocation -> {
                    LocalDateTime cursorCreatedAt = invocation.getArgument(3);
                    Long cursorId = invocation.getArgument(4);
                    List<Transfer> remaining = afterCursor(all, cursorCreatedAt, cursorId);
                    return remaining.subList(0, Math.min(PAGE_SIZE + 1, remaining.size()));
                });

        List<Long> seen = new ArrayList<>();
        LocalDateTime cursorCreatedAt = null;
        Long cursorId = null;
        int pages = 0;

        while (true) {
            ReportCriteria criteria = cursorCreatedAt == null
                    ? new ReportCriteria(ACCOUNT_ID, START, END, 0, PAGE_SIZE)
                    : new ReportCriteria(ACCOUNT_ID, START, END, 0, PAGE_SIZE, cursorCreatedAt, cursorId);

            TransferReportPageAssembler.ReportPage page =
                    TransferReportPageAssembler.assemble(loadTransferPort, enricher, criteria, PAGE_SIZE);
            pages++;

            assertThat(page.transfers()).hasSizeLessThanOrEqualTo(PAGE_SIZE);
            page.transfers().forEach(row -> seen.add(row.getId()));

            if (!page.hasNext()) {
                // The cursor must be exhausted together with the hasNext flag,
                // otherwise the client would keep asking for another page.
                assertThat(page.nextCursorCreatedAt()).isNull();
                assertThat(page.nextCursorId()).isNull();
                break;
            }
            cursorCreatedAt = LocalDateTime.parse(page.nextCursorCreatedAt());
            cursorId = page.nextCursorId();
            assertThat(pages).isLessThan(10);
        }

        assertThat(pages).isEqualTo(3);
        assertThat(seen).containsExactly(10L, 11L, 12L, 13L, 14L);
        assertThat(seen).doesNotHaveDuplicates();
        // The first page is served by the offset path, the following pages by
        // the keyset path: the scroll really used the cursor.
        verify(loadTransferPort).findHistoryBetween(ACCOUNT_ID, START, END, 0, PAGE_SIZE);
        verify(loadTransferPort, times(2)).findHistoryBetweenKeyset(eq(ACCOUNT_ID), eq(START), eq(END),
                any(), any(), eq(PAGE_SIZE));
    }

    @Test
    @DisplayName("should expose the last returned row as the next cursor")
    void shouldExposeLastReturnedRowAsNextCursor() {
        when(loadTransferPort.findHistoryBetween(eq(ACCOUNT_ID), eq(START), eq(END), eq(0), eq(PAGE_SIZE)))
                .thenReturn(List.of(
                        transfer(10L, "100.00", START.plusDays(1)),
                        transfer(11L, "250.00", START.plusDays(2)),
                        transfer(12L, "50.00", START.plusDays(3))));

        TransferReportPageAssembler.ReportPage page = TransferReportPageAssembler.assemble(loadTransferPort, enricher,
                new ReportCriteria(ACCOUNT_ID, START, END, 0, PAGE_SIZE), PAGE_SIZE);

        assertThat(page.hasNext()).isTrue();
        // The over-fetched third row must be trimmed off the page ...
        assertThat(page.transfers()).extracting(Transfer::getId).containsExactly(10L, 11L);
        // ... and the cursor must point at the last row of the page, not at
        // the over-fetched one.
        assertThat(page.nextCursorId()).isEqualTo(11L);
        assertThat(page.nextCursorCreatedAt()).isEqualTo(START.plusDays(2).toString());
        assertThat(page.pageVolume()).isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(page.responses()).hasSize(2);
    }

    @Test
    @DisplayName("should use the keyset port once a cursor is present")
    void shouldUseKeysetPortOnceCursorIsPresent() {
        LocalDateTime cursorCreatedAt = START.plusDays(2);
        Long cursorId = 11L;

        when(loadTransferPort.findHistoryBetweenKeyset(eq(ACCOUNT_ID), eq(START), eq(END),
                eq(cursorCreatedAt), eq(cursorId), eq(PAGE_SIZE)))
                .thenReturn(List.of(transfer(12L, "50.00", START.plusDays(3))));

        ReportCriteria criteria = new ReportCriteria(ACCOUNT_ID, START, END, 0, PAGE_SIZE,
                cursorCreatedAt, cursorId);

        TransferReportPageAssembler.ReportPage page =
                TransferReportPageAssembler.assemble(loadTransferPort, enricher, criteria, PAGE_SIZE);

        // A cursor page must never fall back to OFFSET: that is the whole
        // point of the keyset path on deep windows.
        verify(loadTransferPort).findHistoryBetweenKeyset(ACCOUNT_ID, START, END, cursorCreatedAt, cursorId, PAGE_SIZE);
        verify(loadTransferPort, never()).findHistoryBetween(any(), any(), any(), anyInt(), anyInt());
        assertThat(page.transfers()).extracting(Transfer::getId).containsExactly(12L);
    }

    @Test
    @DisplayName("should report no more pages when the last page is shorter than the page size")
    void shouldReportNoMorePagesOnShortLastPage() {
        LocalDateTime cursorCreatedAt = START.plusDays(3);
        Long cursorId = 12L;

        when(loadTransferPort.findHistoryBetweenKeyset(eq(ACCOUNT_ID), eq(START), eq(END),
                eq(cursorCreatedAt), eq(cursorId), eq(PAGE_SIZE)))
                .thenReturn(List.of(transfer(13L, "75.00", START.plusDays(4))));

        ReportCriteria criteria = new ReportCriteria(ACCOUNT_ID, START, END, 0, PAGE_SIZE,
                cursorCreatedAt, cursorId);

        TransferReportPageAssembler.ReportPage page =
                TransferReportPageAssembler.assemble(loadTransferPort, enricher, criteria, PAGE_SIZE);

        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursorCreatedAt()).isNull();
        assertThat(page.nextCursorId()).isNull();
        assertThat(page.transfers()).extracting(Transfer::getId).containsExactly(13L);
        assertThat(page.pageVolume()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    @DisplayName("should return an empty page when no transfer matches the range")
    void shouldReturnEmptyPageWhenNothingMatches() {
        when(loadTransferPort.findHistoryBetween(eq(ACCOUNT_ID), eq(START), eq(END), eq(0), eq(PAGE_SIZE)))
                .thenReturn(List.of());

        TransferReportPageAssembler.ReportPage page = TransferReportPageAssembler.assemble(loadTransferPort, enricher,
                new ReportCriteria(ACCOUNT_ID, START, END, 0, PAGE_SIZE), PAGE_SIZE);

        assertThat(page.transfers()).isEmpty();
        assertThat(page.responses()).isEmpty();
        assertThat(page.pageVolume()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursorCreatedAt()).isNull();
        assertThat(page.nextCursorId()).isNull();
    }

    @Test
    @DisplayName("should return an empty page when a keyset page has no successor")
    void shouldReturnEmptyPageWhenCursorIsAlreadyAtTheEnd() {
        LocalDateTime cursorCreatedAt = START.plusDays(5);
        Long cursorId = 14L;

        when(loadTransferPort.findHistoryBetweenKeyset(eq(ACCOUNT_ID), eq(START), eq(END),
                eq(cursorCreatedAt), eq(cursorId), eq(PAGE_SIZE)))
                .thenReturn(List.of());

        ReportCriteria criteria = new ReportCriteria(ACCOUNT_ID, START, END, 0, PAGE_SIZE,
                cursorCreatedAt, cursorId);

        TransferReportPageAssembler.ReportPage page =
                TransferReportPageAssembler.assemble(loadTransferPort, enricher, criteria, PAGE_SIZE);

        assertThat(page.transfers()).isEmpty();
        assertThat(page.pageVolume()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursorCreatedAt()).isNull();
        assertThat(page.nextCursorId()).isNull();
    }
}
