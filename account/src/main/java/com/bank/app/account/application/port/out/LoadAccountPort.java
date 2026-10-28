package com.bank.app.account.application.port.out;

import com.bank.app.account.application.dto.AccountView;
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

    List<Account> findByIdsForUpdate(Collection<Long> ids);

    record AccountPage(List<Account> content, long total) {
        public AccountPage {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

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
    Optional<AccountView> findInfoById(Long id);

    Optional<AccountView> findInfoByIban(Iban iban);

    /**
     * Id-to-IBAN pairs for batch enrichment (transfer history/reports),
     * without hydrating full aggregates per row.
     */
    Map<Long, String> findIbansByIds(Collection<Long> ids);
}
