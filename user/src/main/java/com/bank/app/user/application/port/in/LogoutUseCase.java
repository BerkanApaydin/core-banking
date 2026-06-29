package com.bank.app.user.application.port.in;

public interface LogoutUseCase {
    void execute(String authHeader);
}
