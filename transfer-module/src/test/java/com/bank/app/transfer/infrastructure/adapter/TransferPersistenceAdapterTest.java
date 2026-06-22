package com.bank.app.transfer.infrastructure.adapter;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.infrastructure.persistence.TransferJpaEntity;
import com.bank.app.transfer.infrastructure.persistence.TransferJpaRepository;
import com.bank.app.transfer.infrastructure.persistence.TransferMapper;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.Answers;

@ExtendWith(MockitoExtension.class)
class TransferPersistenceAdapterTest {

    @Mock private TransferJpaRepository springDataRepo;
    @Mock(answer = Answers.CALLS_REAL_METHODS) private TransferMapper mapper;

    private TransferPersistenceAdapter repository;

    @BeforeEach
    void setUp() {
        repository = new TransferPersistenceAdapter(springDataRepo, mapper);
    }

    @Test
    void shouldFindByIdSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity jpaEntity = new TransferJpaEntity(10L, 1L, 2L, new BigDecimal("200.00"), "TRY", TransferStatus.COMPLETED, now);

        when(springDataRepo.findById(10L)).thenReturn(Optional.of(jpaEntity));

        Optional<Transfer> result = repository.findById(10L);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getId());
        assertEquals(1L, result.get().getSenderAccountId());
        assertEquals(2L, result.get().getReceiverAccountId());
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
        Transfer domainTransfer = new Transfer(null, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED, now);
        TransferJpaEntity savedEntity = new TransferJpaEntity(10L, 1L, 2L, new BigDecimal("200.00"), "TRY", TransferStatus.COMPLETED, now);

        when(springDataRepo.save(any(TransferJpaEntity.class))).thenReturn(savedEntity);

        Transfer result = repository.save(domainTransfer);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(1L, result.getSenderAccountId());
        assertEquals(2L, result.getReceiverAccountId());
        assertEquals(new BigDecimal("200.00"), result.getAmount().amount());
        assertEquals(Currency.TRY, result.getAmount().currency());
        assertEquals(TransferStatus.COMPLETED, result.getStatus());
        verify(springDataRepo).save(any(TransferJpaEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenSaveReturnsNull() {
        LocalDateTime now = LocalDateTime.now();
        Transfer domainTransfer = new Transfer(null, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED, now);

        when(springDataRepo.save(any(TransferJpaEntity.class))).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> repository.save(domainTransfer));
    }

    @Test
    void shouldFindBySenderAccountIdSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity1 = new TransferJpaEntity(1L, 100L, 200L, new BigDecimal("100.00"), "TRY", TransferStatus.COMPLETED, now);
        TransferJpaEntity entity2 = new TransferJpaEntity(2L, 100L, 300L, new BigDecimal("200.00"), "TRY", TransferStatus.COMPLETED, now);

        when(springDataRepo.findBySenderAccountId(100L)).thenReturn(List.of(entity1, entity2));

        var result = repository.findBySenderAccountId(100L);

        assertEquals(2, result.size());
        assertEquals(100L, result.get(0).getSenderAccountId());
        assertEquals(100L, result.get(1).getSenderAccountId());
        verify(springDataRepo).findBySenderAccountId(100L);
    }

    @Test
    void shouldReturnEmptyListWhenFindBySenderAccountIdNotFound() {
        when(springDataRepo.findBySenderAccountId(999L)).thenReturn(List.of());

        var result = repository.findBySenderAccountId(999L);

        assertTrue(result.isEmpty());
        verify(springDataRepo).findBySenderAccountId(999L);
    }

    @Test
    void shouldFindBySenderAccountIdAndCreatedAtBetweenSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);
        TransferJpaEntity entity1 = new TransferJpaEntity(1L, 100L, 200L, new BigDecimal("100.00"), "TRY", TransferStatus.COMPLETED, now);
        TransferJpaEntity entity2 = new TransferJpaEntity(2L, 100L, 300L, new BigDecimal("200.00"), "TRY", TransferStatus.COMPLETED, now);

        when(springDataRepo.findBySenderAccountIdAndCreatedAtBetween(100L, start, end)).thenReturn(List.of(entity1, entity2));

        var result = repository.findBySenderAccountIdAndCreatedAtBetween(100L, start, end);

        assertEquals(2, result.size());
        assertEquals(100L, result.get(0).getSenderAccountId());
        assertEquals(100L, result.get(1).getSenderAccountId());
        verify(springDataRepo).findBySenderAccountIdAndCreatedAtBetween(100L, start, end);
    }

    @Test
    void shouldReturnEmptyListWhenFindBySenderAccountIdAndCreatedAtBetweenNotFound() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        when(springDataRepo.findBySenderAccountIdAndCreatedAtBetween(999L, start, end)).thenReturn(List.of());

        var result = repository.findBySenderAccountIdAndCreatedAtBetween(999L, start, end);

        assertTrue(result.isEmpty());
        verify(springDataRepo).findBySenderAccountIdAndCreatedAtBetween(999L, start, end);
    }

    @Test
    @SuppressWarnings("null")
    void shouldThrowExceptionWhenSavingNullTransfer() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }

    @Test
    void shouldFindByIdWithLockSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity jpaEntity = new TransferJpaEntity(10L, 1L, 2L, new BigDecimal("200.00"), "TRY", TransferStatus.COMPLETED, now);

        when(springDataRepo.findByIdWithLock(10L)).thenReturn(Optional.of(jpaEntity));

        Optional<Transfer> result = repository.findByIdWithLock(10L);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getId());
        verify(springDataRepo).findByIdWithLock(10L);
    }

    @Test
    void shouldReturnEmptyWhenFindByIdWithLockNotFound() {
        when(springDataRepo.findByIdWithLock(999L)).thenReturn(Optional.empty());

        Optional<Transfer> result = repository.findByIdWithLock(999L);

        assertTrue(result.isEmpty());
        verify(springDataRepo).findByIdWithLock(999L);
    }

    @Test
    void shouldReturnEmptyWhenFindByIdNotFound() {
        when(springDataRepo.findById(999L)).thenReturn(Optional.empty());

        Optional<Transfer> result = repository.findById(999L);

        assertTrue(result.isEmpty());
        verify(springDataRepo).findById(999L);
    }

    @Test
    void shouldThrowWhenMapperReturnsNullForFindBySenderAccountId() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity1 = new TransferJpaEntity(1L, 100L, 200L, new BigDecimal("100.00"), "TRY", TransferStatus.COMPLETED, now);

        when(springDataRepo.findBySenderAccountId(100L)).thenReturn(List.of(entity1));
        lenient().when(mapper.toDomain(entity1)).thenThrow(new IllegalArgumentException("Mapping failed"));

        assertThrows(IllegalArgumentException.class, () -> repository.findBySenderAccountId(100L));
        verify(springDataRepo).findBySenderAccountId(100L);
    }

    @Test
    void shouldThrowWhenMapperReturnsNullForFindBySenderAccountIdAndCreatedAtBetween() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);
        TransferJpaEntity entity1 = new TransferJpaEntity(1L, 100L, 200L, new BigDecimal("100.00"), "TRY", TransferStatus.COMPLETED, now);

        when(springDataRepo.findBySenderAccountIdAndCreatedAtBetween(100L, start, end)).thenReturn(List.of(entity1));
        lenient().when(mapper.toDomain(entity1)).thenThrow(new IllegalArgumentException("Mapping failed"));

        assertThrows(IllegalArgumentException.class, () -> repository.findBySenderAccountIdAndCreatedAtBetween(100L, start, end));
        verify(springDataRepo).findBySenderAccountIdAndCreatedAtBetween(100L, start, end);
    }

    @Test
    void shouldFindHistorySuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity1 = new TransferJpaEntity(1L, 100L, 200L, new BigDecimal("100.00"), "TRY", TransferStatus.COMPLETED, now);
        TransferJpaEntity entity2 = new TransferJpaEntity(2L, 300L, 100L, new BigDecimal("200.00"), "TRY", TransferStatus.COMPLETED, now);

        when(springDataRepo.findHistory(eq(100L), any())).thenReturn(List.of(entity1, entity2));

        var result = repository.findHistory(100L, 0, 10);

        assertEquals(2, result.size());
        verify(springDataRepo).findHistory(eq(100L), any());
    }

    @Test
    void shouldReturnEmptyListWhenFindHistoryNotFound() {
        when(springDataRepo.findHistory(eq(999L), any())).thenReturn(List.of());

        var result = repository.findHistory(999L, 0, 10);

        assertTrue(result.isEmpty());
        verify(springDataRepo).findHistory(eq(999L), any());
    }

    @Test
    void shouldThrowWhenMapperReturnsNullForFindHistory() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity1 = new TransferJpaEntity(1L, 100L, 200L, new BigDecimal("100.00"), "TRY", TransferStatus.COMPLETED, now);
        TransferJpaEntity entity2 = new TransferJpaEntity(2L, 300L, 100L, new BigDecimal("200.00"), "TRY", TransferStatus.COMPLETED, now);

        when(springDataRepo.findHistory(eq(100L), any())).thenReturn(List.of(entity1, entity2));
        lenient().when(mapper.toDomain(entity2)).thenThrow(new IllegalArgumentException("Mapping failed"));

        assertThrows(IllegalArgumentException.class, () -> repository.findHistory(100L, 0, 10));
        verify(springDataRepo).findHistory(eq(100L), any());
    }

    @Test
    void shouldFindHistoryBetweenWithPaginationSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);
        TransferJpaEntity entity1 = new TransferJpaEntity(1L, 100L, 200L, new BigDecimal("100.00"), "TRY", TransferStatus.COMPLETED, now);

        when(springDataRepo.findHistoryBetween(eq(100L), eq(start), eq(end), any())).thenReturn(List.of(entity1));

        var result = repository.findHistoryBetween(100L, start, end, 0, 10);

        assertEquals(1, result.size());
        assertEquals(100L, result.getFirst().getSenderAccountId());
        verify(springDataRepo).findHistoryBetween(eq(100L), eq(start), eq(end), any());
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
    void shouldCountHistorySuccessfully() {
        when(springDataRepo.countHistory(100L)).thenReturn(5L);

        long count = repository.countHistory(100L);

        assertEquals(5L, count);
        verify(springDataRepo).countHistory(100L);
    }

    @Test
    void shouldReturnZeroWhenCountHistoryNotFound() {
        when(springDataRepo.countHistory(999L)).thenReturn(0L);

        long count = repository.countHistory(999L);

        assertEquals(0L, count);
        verify(springDataRepo).countHistory(999L);
    }

    @Test
    void shouldThrowWhenMapperReturnsNullForFindHistoryBetweenWithPagination() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);
        TransferJpaEntity entity1 = new TransferJpaEntity(1L, 100L, 200L, new BigDecimal("100.00"), "TRY", TransferStatus.COMPLETED, now);
        TransferJpaEntity entity2 = new TransferJpaEntity(2L, 300L, 100L, new BigDecimal("200.00"), "TRY", TransferStatus.COMPLETED, now);

        when(springDataRepo.findHistoryBetween(eq(100L), eq(start), eq(end), any())).thenReturn(List.of(entity1, entity2));
        lenient().when(mapper.toDomain(entity1)).thenThrow(new IllegalArgumentException("Mapping failed"));

        assertThrows(IllegalArgumentException.class, () -> repository.findHistoryBetween(100L, start, end, 0, 10));
        verify(springDataRepo).findHistoryBetween(eq(100L), eq(start), eq(end), any());
    }
}
