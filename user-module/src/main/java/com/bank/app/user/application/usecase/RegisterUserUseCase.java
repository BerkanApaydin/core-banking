package com.bank.app.user.application.usecase;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.port.in.RegisterUserPort;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.user.domain.User;
import java.util.List;

public class RegisterUserUseCase implements RegisterUserPort {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final PasswordEncoderPort passwordEncoderPort;

    public RegisterUserUseCase(LoadUserPort loadUserPort, SaveUserPort saveUserPort, PasswordEncoderPort passwordEncoderPort) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.passwordEncoderPort = passwordEncoderPort;
    }

    @Override
    public void execute(AuthRequest request) {
        if (loadUserPort.findByUsername(request.username()).isPresent()) {
            throw new IllegalArgumentException("Kullanıcı adı zaten kullanımda.");
        }

        List<String> policyErrors = PasswordPolicy.DEFAULT.validate(request.password());
        if (!policyErrors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", policyErrors));
        }

        String encodedPassword = passwordEncoderPort.encode(request.password());
        User user = User.create(request.username(), encodedPassword);
        saveUserPort.save(user);
    }
}
