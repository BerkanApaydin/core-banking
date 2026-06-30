package com.bank.app.account.application.port.out;

import com.bank.app.account.domain.Account;
import com.bank.app.common.domain.Iban;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.lang.Nullable;

public interface LoadAccountPort {
    Optional<Account> findByIban(Iban iban);

    Optional<Account> findByIbanWithLock(Iban iban);

    Optional<Account> findById(@Nullable Long id);

    Optional<Account> findByIdWithLock(Long id);

    @Deprecated(since = "1.0", forRemoval = false)
    List<Account> findAll();

    Page<Account> findByUserId(Long userId, Pageable pageable);

    Page<Account> findAll(Pageable pageable);

    List<Account> findByIds(Collection<Long> ids);
}
