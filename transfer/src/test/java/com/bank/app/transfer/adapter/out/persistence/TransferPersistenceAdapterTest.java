package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

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
            String currency, String status, Long version, LocalDateTime createdAt) {
        TransferJpaEntity entity = new TransferJpaEntity(id, senderAccountId, receiverAccountId, amount, currency,
                status, version);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    @Test
    void shouldFindByIdSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity jpaEntity = createEntity(10L, 1L, 2L, new BigDecimal("200.00"), "TRY",
                "COMPLETED", null, now);

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
        Transfer domainTransfer = new Transfer(null, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED,
                now);
        TransferJpaEntity savedEntity = createEntity(10L, 1L, 2L, new BigDecimal("200.00"), "TRY",
                "COMPLETED", null, now);

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
        Transfer domainTransfer = new Transfer(null, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED,
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
        // domain transfer has updated version=2L vs existing entity version=1L
        Transfer domainTransfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED, now, 2L);
        TransferJpaEntity existingEntity = createEntity(10L, 1L, 2L, new BigDecimal("200.00"), "TRY",
                "COMPLETED", 1L, now);
        TransferJpaEntity savedEntity = createEntity(10L, 1L, 2L, new BigDecimal("200.00"), "TRY",
                "COMPLETED", 2L, now);

        when(springDataRepo.findById(10L)).thenReturn(Optional.of(existingEntity));
        when(springDataRepo.save(any(TransferJpaEntity.class))).thenReturn(savedEntity);

        Transfer result = repository.save(domainTransfer);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(1L, result.getSenderAccountId());
        assertEquals(2L, result.getReceiverAccountId());
        assertEquals(TransferStatus.COMPLETED, result.getStatus());
        verify(springDataRepo).findById(10L);

        ArgumentCaptor<TransferJpaEntity> captor = ArgumentCaptor.forClass(TransferJpaEntity.class);
        verify(springDataRepo).save(captor.capture());
        TransferJpaEntity capturedEntity = captor.getValue();
        // If updateJpaEntity was not called, version would still be 1L (from existingEntity)
        assertEquals(2L, capturedEntity.getVersion());
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentTransfer() {
        LocalDateTime now = LocalDateTime.now();
        Transfer domainTransfer = new Transfer(999L, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED, now);

        when(springDataRepo.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> repository.save(domainTransfer));
        assertEquals("Transfer not found: 999", ex.getMessage());
        verify(springDataRepo).findById(999L);
        verify(springDataRepo, never()).save(any());
    }

    @Test
    void shouldFindByIdForUpdateSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity jpaEntity = createEntity(10L, 1L, 2L, new BigDecimal("200.00"), "TRY",
                "COMPLETED", null, now);

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
    void shouldFindHistorySuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity1 = createEntity(1L, 100L, 200L, new BigDecimal("100.00"), "TRY",
                "COMPLETED", null, now);
        TransferJpaEntity entity2 = createEntity(2L, 300L, 100L, new BigDecimal("200.00"), "TRY",
                "COMPLETED", null, now);

        when(springDataRepo.findBySenderAccountIdOrReceiverAccountIdOrderByCreatedAtDesc(eq(100L), eq(100L), any()))
                .thenReturn(List.of(entity1, entity2));

        var result = repository.findHistory(100L, 0, 10);

        assertEquals(2, result.size());
        verify(springDataRepo).findBySenderAccountIdOrReceiverAccountIdOrderByCreatedAtDesc(eq(100L), eq(100L), any());
    }

    @Test
    void shouldReturnEmptyListWhenFindHistoryNotFound() {
        when(springDataRepo.findBySenderAccountIdOrReceiverAccountIdOrderByCreatedAtDesc(eq(999L), eq(999L), any()))
                .thenReturn(List.of());

        var result = repository.findHistory(999L, 0, 10);

        assertTrue(result.isEmpty());
        verify(springDataRepo).findBySenderAccountIdOrReceiverAccountIdOrderByCreatedAtDesc(eq(999L), eq(999L), any());
    }

    @Test
    void shouldFindHistoryBetweenWithPaginationSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);
        TransferJpaEntity entity1 = createEntity(1L, 100L, 200L, new BigDecimal("100.00"), "TRY",
                "COMPLETED", null, now);

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
        when(springDataRepo.countBySenderAccountIdOrReceiverAccountId(100L, 100L)).thenReturn(5L);

        long count = repository.countHistory(100L);

        assertEquals(5L, count);
        verify(springDataRepo).countBySenderAccountIdOrReceiverAccountId(100L, 100L);
    }

    @Test
    void shouldReturnZeroWhenCountHistoryNotFound() {
        when(springDataRepo.countBySenderAccountIdOrReceiverAccountId(999L, 999L)).thenReturn(0L);

        long count = repository.countHistory(999L);

        assertEquals(0L, count);
        verify(springDataRepo).countBySenderAccountIdOrReceiverAccountId(999L, 999L);
    }

}
