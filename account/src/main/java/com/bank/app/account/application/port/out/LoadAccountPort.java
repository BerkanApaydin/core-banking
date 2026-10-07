package com.bank.app.account.application.port.out;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.domain.Account;
import com.bank.app.common.domain.Iban;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * Lightweight read-model projections for the hot authorization paths:
     * only id, owner, currency and status columns are fetched — the
     * NUMERIC balance, IBAN string and owner name are never hydrated just to
     * answer "may this principal touch this account?".
     */
    Optional<AccountInfo> findInfoById(Long id);

    Optional<AccountInfo> findInfoByIban(Iban iban);

    /**
     * Id-to-IBAN pairs for batch enrichment (transfer history/reports),
     * without hydrating full aggregates per row.
     */
    Map<Long, String> findIbansByIds(Collection<Long> ids);
}
