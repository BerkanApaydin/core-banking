package com.bank.app.transfer.infrastructure.persistence;

import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.transfer.application.port.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class JpaTransferRepository implements SaveTransferPort, LoadTransferPort {

    private final SpringDataTransferRepo springDataRepo;
    private final TransferMapper mapper;

    public JpaTransferRepository(SpringDataTransferRepo springDataRepo, TransferMapper mapper) {
        this.springDataRepo = springDataRepo;
        this.mapper = mapper;
    }

    @Override
    public Transfer save(Transfer domainTransfer) {
        TransferJpaEntity entity = mapper.toJpaEntity(domainTransfer);
        if (entity != null) {
            TransferJpaEntity savedEntity = springDataRepo.save(entity);
            Transfer domain = mapper.toDomain(savedEntity);
            if (domain != null) {
                return domain;
            }
        }
        throw new IllegalStateException("Transfer kaydedilemedi.");
    }

    @Override
    public Optional<Transfer> findById(@NonNull Long id) {
        return springDataRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Transfer> findByIdWithLock(@NonNull Long id) {
        return springDataRepo.findByIdWithLock(id).map(mapper::toDomain);
    }

    @Override
    public List<Transfer> findBySenderAccountId(Long accountId) {
        return springDataRepo.findBySenderAccountId(accountId).stream()
                .map(mapper::toDomain)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Transfer> findBySenderAccountIdAndCreatedAtBetween(Long accountId, LocalDateTime start,
            LocalDateTime end) {
        return springDataRepo.findBySenderAccountIdAndCreatedAtBetween(accountId, start, end).stream()
                .map(mapper::toDomain)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Transfer> findHistory(Long accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return springDataRepo.findHistory(accountId, pageable).stream()
                .map(mapper::toDomain)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Transfer> findHistoryBetween(Long accountId, LocalDateTime start, LocalDateTime end) {
        return springDataRepo.findHistoryBetween(accountId, start, end).stream()
                .map(mapper::toDomain)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
