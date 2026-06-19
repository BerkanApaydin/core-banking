package com.bank.app.user.application.port.out;

import com.bank.app.user.domain.User;
import java.util.Optional;

public interface LoadUserPort {
    Optional<User> findByUsername(String username);
}
