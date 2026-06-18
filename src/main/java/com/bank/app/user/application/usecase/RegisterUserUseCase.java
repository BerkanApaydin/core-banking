package com.bank.app.user.application.usecase;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.LoadUserPort;
import com.bank.app.user.application.port.SaveUserPort;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.user.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RegisterUserUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final PasswordEncoder passwordEncoder;

    public RegisterUserUseCase(LoadUserPort loadUserPort, SaveUserPort saveUserPort, PasswordEncoder passwordEncoder) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.passwordEncoder = passwordEncoder;
    }

    public void execute(AuthRequest request) {
        if (loadUserPort.findByUsername(request.username()).isPresent()) {
            throw new IllegalArgumentException("Kullanıcı adı zaten kullanımda.");
        }

        List<String> policyErrors = PasswordPolicy.DEFAULT.validate(request.password());
        if (!policyErrors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", policyErrors));
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.create(request.username(), encodedPassword);
        saveUserPort.save(user);
    }
}
