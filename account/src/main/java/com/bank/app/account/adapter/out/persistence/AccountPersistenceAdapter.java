package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.common.domain.Iban;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class AccountPersistenceAdapter implements LoadAccountPort, SaveAccountPort {

    private final AccountJpaRepository repository;
    private final AccountJpaMapper mapper;

    public AccountPersistenceAdapter(AccountJpaRepository repository, AccountJpaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Account> findByIban(Iban iban) {
        return repository.findByIban(Iban.normalize(iban.value()))
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findByIbanForUpdate(Iban iban) {
        return repository.findByIbanForUpdate(Iban.normalize(iban.value()))
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id)
                .map(mapper::toDomain);
    }

    @Override
    public AccountPage findPageByUserId(Long userId, int page, int size) {
        var result = repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, size));
        return new AccountPage(result.stream().map(mapper::toDomain).toList(), result.getTotalElements());
    }

    @Override
    public List<Account> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repository.findByIdIn(ids).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<AccountInfo> findInfoById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        List<Object[]> rows = repository.findInfoById(id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(toInfo(rows.get(0)));
    }

    @Override
    public Optional<AccountInfo> findInfoByIban(Iban iban) {
        Objects.requireNonNull(iban, "IBAN must not be null");
        List<Object[]> rows = repository.findInfoByIban(Iban.normalize(iban.value()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(toInfo(rows.get(0)));
    }

    @Override
    public Map<Long, String> findIbansByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (Object[] row : repository.findIbansByIds(ids)) {
            if (row[0] instanceof Long id && row[1] instanceof String iban) {
                result.put(id, iban);
            }
        }
        return Map.copyOf(result);
    }

    private static AccountInfo toInfo(Object[] row) {
        return new AccountInfo(
                (Long) row[0],
                (Long) row[1],
                enumName(row[2]),
                enumName(row[3]));
    }

    /**
     * Hibernate may materialize a projected native-enum column as the enum or
     * its label depending on the query path; accept both so the projection
     * never ClassCastExceptions in production.
     */
    private static String enumName(Object value) {
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        return String.valueOf(value);
    }

    @Override
    public Account save(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account must not be null");
        }
        // Perf-1/P-1: happy path is a single versioned UPDATE (no SELECT).
        // Only the rare 0-row path falls back to a load to distinguish
        // not-found (AccountNotFoundException) from conflict (409).
        AccountJpaEntity entity;
        if (account.getId() == null) {
            entity = mapper.toJpaEntity(account);
            AccountJpaEntity saved = repository.save(entity);
            return mapper.toDomain(saved);
        }
        if (account.getVersion() != null) {
            int updated = repository.updateIfVersionMatch(
                    account.getId(),
                    account.getVersion(),
                    account.getBalance().amount(),
                    account.getStatus(),
                    account.getOwnerName());
            if (updated == 1) {
                // No re-read: the bulk UPDATE already applied the state; the
                // caller-held aggregate plus a bumped version is the truth.
                // Saves 1 SELECT per account write (2 per transfer).
                Long bumped = account.getVersion() + 1;
                return new Account(account.getId(), account.getUserId(), account.getIban(),
                        account.getOwnerName(), account.getBalance(), account.getStatus(), bumped);
            }
            // 0 rows: distinguish missing row vs concurrent write with one load.
            repository.findById(account.getId())
                    .orElseThrow(() -> new AccountNotFoundException(account.getId()));
            throw new ObjectOptimisticLockingFailureException(AccountJpaEntity.class, account.getId());
        }
        // Legacy path (version null): same contract as transfer — a missing row
        // reports not-found, an existing row rejects the blind write with an
        // optimistic failure. Callers always carry the version they read, so
        // this load runs only on the exceptional path.
        repository.findById(account.getId())
                .orElseThrow(() -> new AccountNotFoundException(account.getId()));
        throw new ObjectOptimisticLockingFailureException(AccountJpaEntity.class, account.getId());
    }
}
