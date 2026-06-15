package com.bank.app.transfer.infrastructure.persistence;

import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JpaTransferRepositoryTest {

    private SpringDataTransferRepo springDataRepo;
    private TransferMapper mapper;
    private JpaTransferRepository repository;

    @BeforeEach
    void setUp() {
        springDataRepo = mock(SpringDataTransferRepo.class);
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
}
