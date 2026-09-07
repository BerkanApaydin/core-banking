package com.bank.app.account.application.port.out;

import com.bank.app.account.domain.Account;
import com.bank.app.common.domain.Iban;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoadAccountPort {
    Optional<Account> findByIban(Iban iban);

    Optional<Account> findByIbanForUpdate(Iban iban);

    Optional<Account> findById(Long id);

    Optional<Account> findByIdForUpdate(Long id);

    List<Account> findByUserId(Long userId, int page, int size);

    long countByUserId(Long userId);

    List<Account> findByIds(Collection<Long> ids);
}
