package com.bank.app.account.application.port.in;

import com.bank.app.account.domain.AccountStatus;
import org.springframework.lang.NonNull;

public record AccountInfo(@NonNull Long id, @NonNull Long userId, @NonNull String currency, @NonNull AccountStatus status) {
}
