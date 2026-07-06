package com.bank.app.common.application.port.out;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.event.DomainEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface AccountAclPort {
    AccountInfo getAccountInfo(Long accountId);

    AccountInfo getAccountInfoForTransfer(String ibanValue);

    Map<Long, String> getIbansForAccounts(Collection<Long> accountIds);

    List<DomainEvent> debitAndCredit(Long senderId, Long receiverId, Money amount);

    List<DomainEvent> reverseBalancesForCancellation(Long senderId, Long receiverId, Money amount);

    record AccountInfo(Long id, Long userId, String currency, String status) {
        public AccountInfo {
            Objects.requireNonNull(id);
            Objects.requireNonNull(userId);
            Objects.requireNonNull(currency);
            Objects.requireNonNull(status);
        }
    }
}
