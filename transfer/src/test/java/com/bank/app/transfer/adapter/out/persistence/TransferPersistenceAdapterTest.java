package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.transfer.domain.exception.TransferNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;
import com.bank.app.common.domain.AccountId;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class TransferPersistenceAdapterTest {

    @Mock
    private TransferJpaRepository springDataRepo;

    private TransferPersistenceAdapter repository;

    @BeforeEach
    void setUp() {
        repository = new TransferPersistenceAdapter(springDataRepo, new TransferJpaMapper());
    }

    private TransferJpaEntity createEntity(Long id, Long senderAccountId, Long receiverAccountId, BigDecimal amount,
            Currency currency, TransferStatus status, Long version, LocalDateTime createdAt) {
        TransferJpaEntity entity = new TransferJpaEntity(id, senderAccountId, receiverAccountId, amount, currency,
                status, version);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    @Test
    void shouldFindByIdSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity jpaEntity = createEntity(10L, 1L, 2L, new BigDecimal("200.00"), Currency.TRY, TransferStatus.COMPLETED, null, now);

        when(springDataRepo.findById(10L)).thenReturn(Optional.of(jpaEntity));

        Optional<Transfer> result = repository.findById(10L);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getId());
        assertEquals(new AccountId(1L), result.get().getSenderAccountId());
        assertEquals(new AccountId(2L), result.get().getReceiverAccountId());
        assertEquals(new BigDecimal("200.00"), result.get().getAmount().amount());
        assertEquals(Currency.TRY, result.get().getAmount().currency());
        assertEquals(TransferStatus.COMPLETED, result.get().getStatus());
        assertEquals(now, result.get().getCreatedAt());
        verify(springDataRepo).findById(10L);
    }

    @Test
    @SuppressWarnings("null")
    void shouldSaveSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        Transfer domainTransfer = new Transfer(null, new AccountId(1L), new AccountId(2L), Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED,
                now);
        TransferJpaEntity savedEntity = createEntity(10L, 1L, 2L, new BigDecimal("200.00"), Currency.TRY, TransferStatus.COMPLETED, null, now);

        when(springDataRepo.save(any(TransferJpaEntity.class))).thenReturn(savedEntity);

        Transfer result = repository.save(domainTransfer);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(new AccountId(1L), result.getSenderAccountId());
        assertEquals(new AccountId(2L), result.getReceiverAccountId());
        assertEquals(new BigDecimal("200.00"), result.getAmount().amount());
        assertEquals(Currency.TRY, result.getAmount().currency());
        assertEquals(TransferStatus.COMPLETED, result.getStatus());
        verify(springDataRepo).save(any(TransferJpaEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenSaveReturnsNull() {
        LocalDateTime now = LocalDateTime.now();
        Transfer domainTransfer = new Transfer(null, new AccountId(1L), new AccountId(2L), Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED,
                now);

        when(springDataRepo.save(any(TransferJpaEntity.class))).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> repository.save(domainTransfer));
    }

    @Test
    @SuppressWarnings("null")
    void shouldThrowExceptionWhenSavingNullTransfer() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }

    @Test
    void shouldUpdateExistingTransfer() {
        LocalDateTime now = LocalDateTime.now();
        // The domain carries the version it read; the bulk path issues a single
        // versioned UPDATE (no SELECT) and returns the bumped version.
        Transfer domainTransfer = new Transfer(10L, new AccountId(1L), new AccountId(2L), Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED, now, 1L);

        when(springDataRepo.updateStatusIfVersionMatch(10L, 1L, TransferStatus.COMPLETED)).thenReturn(1);

        Transfer result = repository.save(domainTransfer);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(new AccountId(1L), result.getSenderAccountId());
        assertEquals(new AccountId(2L), result.getReceiverAccountId());
        assertEquals(TransferStatus.COMPLETED, result.getStatus());
        assertEquals(2L, result.getVersion());
        verify(springDataRepo).updateStatusIfVersionMatch(10L, 1L, TransferStatus.COMPLETED);
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentTransfer() {
        LocalDateTime now = LocalDateTime.now();
        Transfer domainTransfer = new Transfer(999L, new AccountId(1L), new AccountId(2L), Money.of("200.00",
Currency.TRY), TransferStatus.COMPLETED, now);

        when(springDataRepo.findById(999L)).thenReturn(Optional.empty());

        // Domain NOT_FOUND (404 like the account sibling), never bare
        // IllegalArgumentException (500).
        TransferNotFoundException ex = assertThrows(TransferNotFoundException.class,
                () -> repository.save(domainTransfer));
        verify(springDataRepo).findById(999L);
        verify(springDataRepo, never()).save(any());
    }
    @Test
    void shouldFindByIdForUpdateSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity jpaEntity = createEntity(10L, 1L, 2L, new BigDecimal("200.00"), Currency.TRY, TransferStatus.COMPLETED, null, now);

        when(springDataRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(jpaEntity));

        Optional<Transfer> result = repository.findByIdForUpdate(10L);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getId());
        verify(springDataRepo).findByIdForUpdate(10L);
    }

    @Test
    void shouldReturnEmptyWhenFindByIdForUpdateNotFound() {
        when(springDataRepo.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        Optional<Transfer> result = repository.findByIdForUpdate(999L);

        assertTrue(result.isEmpty());
        verify(springDataRepo).findByIdForUpdate(999L);
    }

    @Test
    void shouldReturnEmptyWhenFindByIdNotFound() {
        when(springDataRepo.findById(999L)).thenReturn(Optional.empty());

        Optional<Transfer> result = repository.findById(999L);

        assertTrue(result.isEmpty());
        verify(springDataRepo).findById(999L);
    }

    @Test
    void shouldFindHistoryPageWithTotalFromSingleQuery() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity1 = createEntity(1L, 100L, 200L, new BigDecimal("100.00"), Currency.TRY, TransferStatus.COMPLETED, null, now);
        TransferJpaEntity entity2 = createEntity(2L, 300L, 100L, new BigDecimal("200.00"), Currency.TRY, TransferStatus.COMPLETED, null, now);

        when(springDataRepo.findHistoryPage(eq(100L), eq(10), eq(0L)))
                .thenReturn(List.<Object[]>of(new Object[]{entity1, 5L}, new Object[]{entity2, 5L}));

        var result = repository.findHistoryPage(100L, 0, 10);

        assertEquals(2, result.items().size());
        assertEquals(5L, result.totalElements());
        assertEquals(new AccountId(100L), result.items().getFirst().getSenderAccountId());
        verify(springDataRepo).findHistoryPage(eq(100L), eq(10), eq(0L));
    }

    @Test
    void shouldReturnEmptyHistoryPageWhenNotFound() {
        when(springDataRepo.findHistoryPage(eq(999L), eq(10), eq(0L))).thenReturn(List.of());

        var result = repository.findHistoryPage(999L, 0, 10);

        assertTrue(result.items().isEmpty());
        assertEquals(0L, result.totalElements());
    }

    @Test
    void shouldFindHistoryBetweenWithPaginationSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);
        TransferJpaEntity entity1 = createEntity(1L, 100L, 200L, new BigDecimal("100.00"), Currency.TRY, TransferStatus.COMPLETED, null, now);

        when(springDataRepo.findHistoryBetween(eq(100L), eq(start), eq(end), any())).thenReturn(List.of(entity1));

        var result = repository.findHistoryBetween(100L, start, end, 0, 10);

        assertEquals(1, result.size());
        assertEquals(new AccountId(100L), result.getFirst().getSenderAccountId());
        verify(springDataRepo).findHistoryBetween(eq(100L), eq(start), eq(end), any());
    }

    @Test
    void shouldOverfetchOneRowWithLogicalOffset() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        when(springDataRepo.findHistoryBetween(eq(100L), eq(start), eq(end), any())).thenReturn(List.of());

        repository.findHistoryBetween(100L, start, end, 2, 10);

        // Offset stays page * size (2 * 10), limit grows by one for hasNext:
        // PageRequest.of(page, size + 1) would wrongly offset by page * (size + 1).
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        verify(springDataRepo).findHistoryBetween(eq(100L), eq(start), eq(end), pageableCaptor.capture());
        assertEquals(20L, pageableCaptor.getValue().getOffset());
        assertEquals(11, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void shouldReturnEmptyListWhenFindHistoryBetweenWithPaginationNotFound() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        when(springDataRepo.findHistoryBetween(eq(999L), eq(start), eq(end), any())).thenReturn(List.of());

        var result = repository.findHistoryBetween(999L, start, end, 0, 10);

        assertTrue(result.isEmpty());
        verify(springDataRepo).findHistoryBetween(eq(999L), eq(start), eq(end), any());
    }

    @Test
    void shouldComputeOffsetFromPageAndSize() {
        when(springDataRepo.findHistoryPage(eq(100L), eq(10), eq(20L))).thenReturn(List.of());

        repository.findHistoryPage(100L, 2, 10);

        // Offset is page * size (not page * (size + 1)); limit is the page size.
        verify(springDataRepo).findHistoryPage(eq(100L), eq(10), eq(20L));
    }

    @Test
    void shouldSummarizeRangeFromSingleAggregateRow() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        when(springDataRepo.summarizeRange(eq(1L), eq(start), eq(end)))
                .thenReturn(List.<Object[]>of(new Object[]{3L, new BigDecimal("300.00")}));

        var totals = repository.summarizeRange(1L, start, end);

        assertEquals(3L, totals.count());
        assertEquals(new BigDecimal("300.00"), totals.volume());
        verify(springDataRepo).summarizeRange(eq(1L), eq(start), eq(end));
    }

    @Test
    void shouldSummarizeEmptyRangeAsZero() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        when(springDataRepo.summarizeRange(eq(1L), eq(start), eq(end)))
                .thenReturn(List.<Object[]>of(new Object[]{0L, new BigDecimal("0")}));

        var totals = repository.summarizeRange(1L, start, end);

        assertEquals(0L, totals.count());
        assertEquals(0, totals.volume().compareTo(BigDecimal.ZERO));
    }

    @Test
    void shouldSummarizeMissingRowAsZero() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        when(springDataRepo.summarizeRange(eq(1L), eq(start), eq(end)))
                .thenReturn(List.of());

        LoadTransferPort.ReportTotals totals = repository.summarizeRange(1L, start, end);

        assertEquals(0L, totals.count());
        assertEquals(BigDecimal.ZERO, totals.volume());
    }

    @Test
    void shouldServeKeysetPageWithoutOffset() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime cursor = now.minusHours(1);
        TransferJpaEntity row = createEntity(10L, 1L, 2L, new BigDecimal("200.00"),
                Currency.TRY, TransferStatus.COMPLETED, 0L, now);
        when(springDataRepo.findHistoryBetweenKeyset(eq(1L), eq(start), eq(now), eq(cursor), eq(9L),
                any(Pageable.class))).thenReturn(List.of(row));

        List<Transfer> result = repository.findHistoryBetweenKeyset(1L, start, now, cursor, 9L, 20);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
    }

    @Test
    void shouldOverfetchKeysetLimitByOne() {
        // Kills the MATH mutant (safeSize + 1 -> safeSize - 1): the keyset
        // query must request one row beyond the logical page for hasNext.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime cursor = now.minusHours(1);
        when(springDataRepo.findHistoryBetweenKeyset(eq(1L), eq(start), eq(now), eq(cursor), eq(9L),
                any(Pageable.class))).thenReturn(List.of());

        repository.findHistoryBetweenKeyset(1L, start, now, cursor, 9L, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(springDataRepo).findHistoryBetweenKeyset(eq(1L), eq(start), eq(now), eq(cursor), eq(9L),
                captor.capture());
        assertEquals(21, captor.getValue().getPageSize());
    }

    @Test
    void shouldSummarizeDoubleVolumeWithoutLosingCents() {
        // Kills the NegateConditionals mutant on (row[1] instanceof BigDecimal):
        // a Double sum must still convert exactly via Number.toString.
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        when(springDataRepo.summarizeRange(eq(1L), eq(start), eq(end)))
                .thenReturn(List.<Object[]>of(new Object[]{2L, 300.50}));

        var totals = repository.summarizeRange(1L, start, end);

        assertEquals(2L, totals.count());
        assertEquals(0, totals.volume().compareTo(new BigDecimal("300.50")));
    }

    @Test
    void shouldSummarizeNullRowAsZero() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        when(springDataRepo.summarizeRange(eq(1L), eq(start), eq(end)))
                .thenReturn(Collections.singletonList(null));

        var totals = repository.summarizeRange(1L, start, end);

        assertEquals(0L, totals.count());
        assertEquals(BigDecimal.ZERO, totals.volume());
    }

    @Test
    void shouldSummarizeShortRowAsZero() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        when(springDataRepo.summarizeRange(eq(1L), eq(start), eq(end)))
                .thenReturn(List.<Object[]>of(new Object[]{5L}));

        var totals = repository.summarizeRange(1L, start, end);

        assertEquals(0L, totals.count());
        assertEquals(BigDecimal.ZERO, totals.volume());
    }

    @Test
    void shouldThrowNotFoundWhenBulkUpdateHitsMissingRow() {
        // Kills the NullReturnVals mutant on lambda$save$1: the 0-row bulk path
        // must distinguish "row gone" (TransferNotFoundException/404) from conflict.
        LocalDateTime now = LocalDateTime.now();
        Transfer missing = new Transfer(999L, new AccountId(1L), new AccountId(2L), Money.of("200.00", Currency.TRY),
                TransferStatus.COMPLETED, now, 1L);
        when(springDataRepo.updateStatusIfVersionMatch(999L, 1L, TransferStatus.COMPLETED))
                .thenReturn(0);
        when(springDataRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(TransferNotFoundException.class,
                () -> repository.save(missing));
    }

    @Test
    void shouldRejectStaleVersionOnBulkUpdate() {
        LocalDateTime now = LocalDateTime.now();
        Transfer stale = new Transfer(10L, new AccountId(1L), new AccountId(2L), Money.of("200.00", Currency.TRY),
                TransferStatus.COMPLETED, now, 1L);
        when(springDataRepo.updateStatusIfVersionMatch(10L, 1L, TransferStatus.COMPLETED))
                .thenReturn(0);
        when(springDataRepo.findById(10L)).thenReturn(Optional.of(
                createEntity(10L, 1L, 2L, new BigDecimal("200.00"), Currency.TRY,
                        TransferStatus.COMPLETED, 2L, now)));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> repository.save(stale));
    }

}
