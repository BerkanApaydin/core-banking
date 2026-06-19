package com.bank.app.account.infrastructure.persistence;

import com.bank.app.account.application.port.LoadAccountPort;
import com.bank.app.account.application.port.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import jakarta.persistence.EntityManager;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class AccountPersistenceAdapter implements LoadAccountPort, SaveAccountPort {

    private final AccountJpaRepository springDataRepo;
    private final AccountMapper mapper;
    private final EntityManager entityManager;

    public AccountPersistenceAdapter(AccountJpaRepository springDataRepo, AccountMapper mapper, EntityManager entityManager) {
        this.springDataRepo = springDataRepo;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Account> findByIban(Iban iban) {
        return springDataRepo.findByIban(iban.value()).map(entity -> {
            entityManager.detach(entity);
            return mapper.toDomain(entity);
        });
    }

    @Override
    public Optional<Account> findByIbanWithLock(Iban iban) {
        return springDataRepo.findByIbanWithLock(iban.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findById(@NonNull Long id) {
        return springDataRepo.findById(id).map(entity -> {
            entityManager.detach(entity);
            return mapper.toDomain(entity);
        });
    }

    @Override
    public Optional<Account> findByIdWithLock(@NonNull Long id) {
        return springDataRepo.findByIdWithLock(id).map(mapper::toDomain);
    }

    @Override
    public List<Account> findAll() {
        return springDataRepo.findAll().stream()
                .peek(entityManager::detach)
                .map(mapper::toDomain)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Account> findByUserId(Long userId) {
        return springDataRepo.findByUserId(userId).stream()
                .peek(entityManager::detach)
                .map(mapper::toDomain)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Account> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return springDataRepo.findAllById(ids).stream()
                .peek(entityManager::detach)
                .map(mapper::toDomain)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Account save(Account account) {
        AccountJpaEntity entity = mapper.toJpaEntity(account);
        if (entity == null) {
            return null;
        }
        return mapper.toDomain(springDataRepo.save(entity));
    }
}
