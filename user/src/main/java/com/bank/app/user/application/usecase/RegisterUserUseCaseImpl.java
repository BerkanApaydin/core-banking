package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.domain.EmailAddress;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.user.domain.PhoneNumber;
import com.bank.app.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@TransactionalUseCase
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterUserUseCaseImpl.class);

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final PasswordPolicy passwordPolicy;

    public RegisterUserUseCaseImpl(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                                    PasswordEncoderPort passwordEncoderPort, PasswordPolicy passwordPolicy) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.passwordEncoderPort = passwordEncoderPort;
        this.passwordPolicy = passwordPolicy;
    }

    @Override
    public void execute(AuthRequest request) {
        if (loadUserPort.findByUsername(request.username()).isPresent()) {
            throw new IllegalArgumentException("Username already in use.");
        }

        List<String> policyErrors = passwordPolicy.validate(request.password());
        if (!policyErrors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", policyErrors));
        }

        String encodedPassword = passwordEncoderPort.encode(request.password());
        EmailAddress email = request.email() != null ? new EmailAddress(request.email()) : null;
        PhoneNumber phone = request.phone() != null ? new PhoneNumber(request.phone()) : null;
        User user = User.create(request.username(), encodedPassword, email, phone);
        saveUserPort.save(user);

        log.info("User registered: username={}", request.username());
    }
}
