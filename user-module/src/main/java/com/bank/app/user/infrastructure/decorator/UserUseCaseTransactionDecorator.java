package com.bank.app.user.infrastructure.decorator;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import org.springframework.transaction.annotation.Transactional;

public class UserUseCaseTransactionDecorator implements RegisterUserUseCase {

    private final RegisterUserUseCase delegate;

    public UserUseCaseTransactionDecorator(RegisterUserUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void execute(AuthRequest request) {
        delegate.execute(request);
    }
}
