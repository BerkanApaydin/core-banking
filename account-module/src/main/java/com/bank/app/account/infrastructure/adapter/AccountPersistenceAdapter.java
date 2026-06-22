package com.bank.app.account.infrastructure.adapter;

import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.account.infrastructure.persistence.AccountJpaRepository;
import com.bank.app.account.infrastructure.persistence.AccountMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AccountPersistenceAdapter implements SaveAccountPort {

    private final AccountJpaRepository springDataRepo;
    private final AccountMapper mapper;

    public AccountPersistenceAdapter(AccountJpaRepository springDataRepo, AccountMapper mapper) {
        this.springDataRepo = springDataRepo;
        this.mapper = mapper;
    }

    @Override
    public Account save(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account must not be null");
        }
        if (account.getId() == null) {
            AccountJpaEntity entity = mapper.toJpaEntity(account);
            AccountJpaEntity savedEntity = springDataRepo.save(entity);
            return mapper.toDomain(savedEntity);
        }

        AccountJpaEntity entity = springDataRepo.findById(account.getId())
                .orElseThrow(() -> new IllegalArgumentException("Account bulunamadı: " + account.getId()));

        entity.setBalance(account.getBalance().amount());
        entity.setCurrency(account.getBalance().currency().name());
        entity.setStatus(account.getStatus().name());

        return mapper.toDomain(entity);
    }
}
