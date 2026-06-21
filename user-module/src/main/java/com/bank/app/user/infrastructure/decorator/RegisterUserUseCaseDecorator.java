package com.bank.app.user.infrastructure.decorator;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.usecase.RegisterUserUseCaseImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegisterUserUseCaseDecorator implements RegisterUserUseCase {

    private final RegisterUserUseCaseImpl delegate;

    public RegisterUserUseCaseDecorator(
            LoadUserPort loadUserPort,
            SaveUserPort saveUserPort,
            PasswordEncoderPort passwordEncoderPort) {
        this.delegate = new RegisterUserUseCaseImpl(loadUserPort, saveUserPort, passwordEncoderPort);
    }

    @Override
    @Transactional
    public void execute(AuthRequest request) {
        delegate.execute(request);
    }
}
