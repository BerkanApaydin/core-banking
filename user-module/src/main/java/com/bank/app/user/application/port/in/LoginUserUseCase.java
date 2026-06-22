package com.bank.app.user.application.port.in;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;

public interface LoginUserUseCase {
    AuthResponse execute(AuthRequest request);

    AuthResponse execute(AuthRequest request, String clientIp);
}
