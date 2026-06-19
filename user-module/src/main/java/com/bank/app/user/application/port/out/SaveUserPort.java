package com.bank.app.user.application.port.out;

import com.bank.app.user.domain.User;

public interface SaveUserPort {
    void save(User user);
}
