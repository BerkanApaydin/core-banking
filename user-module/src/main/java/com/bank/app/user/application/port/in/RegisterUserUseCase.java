package com.bank.app.user.application.port.in;

import com.bank.app.user.application.dto.AuthRequest;

public interface RegisterUserUseCase {
    void execute(AuthRequest request);
}
