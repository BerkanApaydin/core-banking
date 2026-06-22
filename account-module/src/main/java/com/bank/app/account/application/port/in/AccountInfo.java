package com.bank.app.account.application.port.in;

import com.bank.app.account.domain.AccountStatus;

public record AccountInfo(Long id, Long userId, String currency, AccountStatus status) {
}
