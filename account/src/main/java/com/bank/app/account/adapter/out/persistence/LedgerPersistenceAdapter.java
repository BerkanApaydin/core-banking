package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.application.port.out.SaveLedgerPort;
import com.bank.app.account.domain.LedgerEntry;
import org.springframework.stereotype.Component;

@Component
public class LedgerPersistenceAdapter implements SaveLedgerPort {

    private final LedgerEntryJpaRepository repository;
    private final LedgerJpaMapper mapper;

    public LedgerPersistenceAdapter(LedgerEntryJpaRepository repository, LedgerJpaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public LedgerEntry save(LedgerEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Ledger entry must not be null");
        }
        // Append-only: entries are never updated, so no merge/version check.
        LedgerEntryJpaEntity saved = repository.save(mapper.toJpaEntity(entry));
        return mapper.toDomain(saved);
    }
}
