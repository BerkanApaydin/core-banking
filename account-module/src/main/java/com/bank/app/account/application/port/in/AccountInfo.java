package com.bank.app.account.application.port.in;

import org.springframework.lang.NonNull;

public record AccountInfo(@NonNull Long id, @NonNull Long userId, @NonNull String currency, boolean active) {
}
