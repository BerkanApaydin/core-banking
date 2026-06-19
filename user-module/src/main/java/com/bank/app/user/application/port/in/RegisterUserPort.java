package com.bank.app.user.application.port.in;

import com.bank.app.user.application.dto.AuthRequest;

public interface RegisterUserPort {
    void execute(AuthRequest request);
}
