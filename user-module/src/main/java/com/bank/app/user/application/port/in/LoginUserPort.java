package com.bank.app.user.application.port.in;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;

public interface LoginUserPort {
    AuthResponse execute(AuthRequest request);
}
