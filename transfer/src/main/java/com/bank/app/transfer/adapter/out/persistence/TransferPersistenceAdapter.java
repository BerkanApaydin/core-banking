package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class TransferPersistenceAdapter implements SaveTransferPort, LoadTransferPort {

    private final TransferJpaRepository repository;
    private final TransferJpaMapper mapper;

    public TransferPersistenceAdapter(TransferJpaRepository repository, TransferJpaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Transfer save(Transfer transfer) {
        if (transfer == null) {
            throw new IllegalArgumentException("Transfer must not be null");
        }
        TransferJpaEntity entity;
        if (transfer.getId() == null) {
            entity = mapper.toJpaEntity(transfer);
        } else {
            entity = repository.findById(transfer.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Transfer not found: " + transfer.getId()));
            mapper.updateJpaEntity(entity, transfer);
        }
        TransferJpaEntity saved = repository.save(entity);
        if (saved == null) {
            throw new IllegalArgumentException("Saved entity must not be null");
        }
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Transfer> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Transfer> findByIdWithLock(Long id) {
        return repository.findByIdWithLock(id).map(mapper::toDomain);
    }

    @Override
    public List<Transfer> findBySenderAccountId(Long accountId) {
        return repository.findBySenderAccountIdOrderByCreatedAtDesc(accountId, Pageable.unpaged())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Transfer> findBySenderAccountIdAndCreatedAtBetween(Long accountId, LocalDateTime start, LocalDateTime end) {
        return repository.findBySenderAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(accountId, start, end)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Transfer> findHistory(Long accountId, int page, int size) {
        return repository.findBySenderAccountIdOrReceiverAccountIdOrderByCreatedAtDesc(
                        accountId, accountId, PageRequest.of(page, size))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Transfer> findHistoryBetween(Long accountId, LocalDateTime start, LocalDateTime end, int page, int size) {
        return repository.findHistoryBetween(accountId, start, end, PageRequest.of(page, size))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countHistory(Long accountId) {
        return repository.countBySenderAccountIdOrReceiverAccountId(accountId, accountId);
    }
}
