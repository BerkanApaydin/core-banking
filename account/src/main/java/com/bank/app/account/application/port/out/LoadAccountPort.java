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

    record AccountPage(List<Account> content, long total) {}

    /**
     * Single paged listing (content + total from one repository page).
     * Replaces the former findByUserId/countByUserId pair, which duplicated
     * this query and forced a second round-trip for the total.
     */
    AccountPage findPageByUserId(Long userId, int page, int size);

    List<Account> findByIds(Collection<Long> ids);
}
