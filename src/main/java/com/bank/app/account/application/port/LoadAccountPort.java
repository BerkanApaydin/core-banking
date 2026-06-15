package com.bank.app.account.application.port;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import org.springframework.lang.NonNull;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface LoadAccountPort {
    Optional<Account> findByIban(Iban iban);

    Optional<Account> findByIbanWithLock(Iban iban);

    Optional<Account> findById(@NonNull Long id);

    Optional<Account> findByIdWithLock(@NonNull Long id);

    List<Account> findAll();

    List<Account> findByUserId(Long userId);

    List<Account> findByIds(Collection<Long> ids);
}
