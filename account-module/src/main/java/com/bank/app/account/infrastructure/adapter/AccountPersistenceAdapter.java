package com.bank.app.account.infrastructure.adapter;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.account.infrastructure.persistence.AccountJpaRepository;
import com.bank.app.account.infrastructure.persistence.AccountMapper;
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

    public AccountPersistenceAdapter(AccountJpaRepository springDataRepo, AccountMapper mapper) {
        this.springDataRepo = springDataRepo;
        this.mapper = mapper;
    }

    @Override
    public Optional<Account> findByIban(Iban iban) {
        return springDataRepo.findByIban(iban.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findByIbanWithLock(Iban iban) {
        return springDataRepo.findByIbanWithLock(iban.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findById(Long id) {
        return springDataRepo.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findByIdWithLock(Long id) {
        return springDataRepo.findByIdWithLock(id).map(mapper::toDomain);
    }

    @Override
    public List<Account> findAll() {
        return springDataRepo.findAll().stream()
                .map(mapper::toDomain)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Account> findByUserId(Long userId) {
        return springDataRepo.findByUserId(userId).stream()
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
                .map(mapper::toDomain)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Account save(Account account) {
        AccountJpaEntity entity = mapper.toJpaEntity(account);
        if (entity == null) {
            throw new IllegalArgumentException("Account entity dönüşümü başarısız oldu");
        }
        return mapper.toDomain(springDataRepo.save(entity));
    }
}
