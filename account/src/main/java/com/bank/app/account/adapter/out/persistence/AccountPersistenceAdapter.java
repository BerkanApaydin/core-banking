package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.common.domain.Iban;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.List;
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
    public Optional<Account> findByIbanWithLock(Iban iban) {
        return repository.findByIbanWithLock(Iban.normalize(iban.value()))
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
    public Optional<Account> findByIdWithLock(Long id) {
        return repository.findByIdWithLock(id)
                .map(mapper::toDomain);
    }

    @Override
    @Deprecated(since = "1.0", forRemoval = false)
    public List<Account> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Page<Account> findAll(Pageable pageable) {
        return repository.findAll(pageable)
                .map(mapper::toDomain);
    }

    @Override
    public Page<Account> findByUserId(Long userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(mapper::toDomain);
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
    public Account save(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account must not be null");
        }
        AccountJpaEntity entity = mapper.toJpaEntity(account);
        AccountJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }
}
