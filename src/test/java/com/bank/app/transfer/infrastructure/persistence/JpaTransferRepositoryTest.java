package com.bank.app.transfer.infrastructure.persistence;

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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaTransferRepositoryTest {

    @Mock private SpringDataTransferRepo springDataRepo;

    private TransferMapper mapper;
    private JpaTransferRepository repository;

    @BeforeEach
    void setUp() {
        mapper = new TransferMapper();
        repository = new JpaTransferRepository(springDataRepo, mapper);
    }

    @Test
    void shouldFindByIdSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity jpaEntity = new TransferJpaEntity(10L, 1L, 2L, new BigDecimal("200.00"), "TRY", "COMPLETED", now);

        when(springDataRepo.findById(10L)).thenReturn(Optional.of(jpaEntity));

        Optional<Transfer> result = repository.findById(10L);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getId());
        assertEquals(1L, result.get().getSenderAccountId());
        assertEquals(2L, result.get().getReceiverAccountId());
        assertEquals(new BigDecimal("200.00"), result.get().getAmount().amount());
        assertEquals(Money.Currency.TRY, result.get().getAmount().currency());
        assertEquals(TransferStatus.COMPLETED, result.get().getStatus());
        assertEquals(now, result.get().getCreatedAt());
        verify(springDataRepo).findById(10L);
    }

    @Test
    @SuppressWarnings("null")
    void shouldSaveSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        Transfer domainTransfer = new Transfer(null, 1L, 2L, Money.of("200.00", Money.Currency.TRY), TransferStatus.COMPLETED, now);
        TransferJpaEntity savedEntity = new TransferJpaEntity(10L, 1L, 2L, new BigDecimal("200.00"), "TRY", "COMPLETED", now);

        when(springDataRepo.save(any(TransferJpaEntity.class))).thenReturn(savedEntity);

        Transfer result = repository.save(domainTransfer);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(1L, result.getSenderAccountId());
        assertEquals(2L, result.getReceiverAccountId());
        assertEquals(new BigDecimal("200.00"), result.getAmount().amount());
        assertEquals(Money.Currency.TRY, result.getAmount().currency());
        assertEquals(TransferStatus.COMPLETED, result.getStatus());
        verify(springDataRepo).save(any(TransferJpaEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenSaveReturnsNull() {
        LocalDateTime now = LocalDateTime.now();
        Transfer domainTransfer = new Transfer(null, 1L, 2L, Money.of("200.00", Money.Currency.TRY), TransferStatus.COMPLETED, now);

        when(springDataRepo.save(any(TransferJpaEntity.class))).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> repository.save(domainTransfer));
    }

    @Test
    void shouldFindBySenderAccountIdSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity1 = new TransferJpaEntity(1L, 100L, 200L, new BigDecimal("100.00"), "TRY", "COMPLETED", now);
        TransferJpaEntity entity2 = new TransferJpaEntity(2L, 100L, 300L, new BigDecimal("200.00"), "TRY", "COMPLETED", now);

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
        TransferJpaEntity entity1 = new TransferJpaEntity(1L, 100L, 200L, new BigDecimal("100.00"), "TRY", "COMPLETED", now);
        TransferJpaEntity entity2 = new TransferJpaEntity(2L, 100L, 300L, new BigDecimal("200.00"), "TRY", "COMPLETED", now);

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
        assertThrows(IllegalStateException.class, () -> repository.save(null));
    }
}
