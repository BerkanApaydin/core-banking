package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@Component
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
            // Loading a managed entity must not discard the caller's expected version.
            // Hibernate still detects a concurrent write after this comparison at flush.
            if (transfer.getVersion() == null || !transfer.getVersion().equals(entity.getVersion())) {
                throw new ObjectOptimisticLockingFailureException(TransferJpaEntity.class, transfer.getId());
            }
            mapper.updateJpaEntity(entity, transfer);
        }
        TransferJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Transfer> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Transfer> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public List<Transfer> findHistory(Long accountId, int page, int size) {
        return repository.findBySenderAccountIdOrReceiverAccountIdOrderByCreatedAtDescIdDesc(
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
