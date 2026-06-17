package com.bank.app.transfer.application.port;

import com.bank.app.common.domain.Money;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.Map;

/**
 * Transfer bounded context'in hesap işlemlerine erişim portu.
 * Account modülü bu portu implement eder — transfer → account somut bağımlılığı kaldırılır.
 */
public interface AccountOperationsPort {

    @NonNull
    AccountInfo getAccountInfo(@NonNull Long accountId);

    @NonNull
    AccountInfo getAccountInfoForTransfer(String ibanValue);

    Map<Long, String> getIbansForAccounts(Collection<Long> accountIds);

    void debitAndCredit(@NonNull Long senderId, @NonNull Long receiverId, @NonNull Money amount);

    void reverseBalancesForCancellation(@NonNull Long senderId, @NonNull Long receiverId, @NonNull Money amount);

    record AccountInfo(@NonNull Long id, @NonNull Long userId, @NonNull String currency, boolean active) {
    }
}
