package com.bank.app.user.application.usecase;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.user.domain.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
@Transactional
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final PasswordEncoderPort passwordEncoderPort;

    public RegisterUserUseCaseImpl(LoadUserPort loadUserPort, SaveUserPort saveUserPort, PasswordEncoderPort passwordEncoderPort) {
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
        User user = User.create(request.username(), encodedPassword, request.email(), request.phone());
        saveUserPort.save(user);
    }
}
