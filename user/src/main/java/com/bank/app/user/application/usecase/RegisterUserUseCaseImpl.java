package com.bank.app.user.application.usecase;

import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.domain.EmailAddress;
import com.bank.app.user.domain.EncodedPassword;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.user.domain.PhoneNumber;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.exception.UsernameAlreadyTakenException;
import com.bank.app.user.domain.exception.WeakPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;

@TransactionalUseCase
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterUserUseCaseImpl.class);

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final PasswordPolicy passwordPolicy;
    private final DomainEventPublisherService domainEventPublisherService;
    private final ClockProviderPort clockProvider;

    public RegisterUserUseCaseImpl(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                                    PasswordEncoderPort passwordEncoderPort, PasswordPolicy passwordPolicy,
                                    DomainEventPublisherService domainEventPublisherService,
                                    ClockProviderPort clockProvider) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.passwordEncoderPort = passwordEncoderPort;
        this.passwordPolicy = passwordPolicy;
        this.domainEventPublisherService = domainEventPublisherService;
        this.clockProvider = clockProvider;
    }

    @Override
    public void execute(AuthRequest request) {
        if (loadUserPort.findByUsername(request.username()).isPresent()) {
            throw new UsernameAlreadyTakenException(request.username());
        }

        List<String> policyErrors = passwordPolicy.validate(request.password());
        if (!policyErrors.isEmpty()) {
            throw new WeakPasswordException(policyErrors);
        }

        String encodedPassword = passwordEncoderPort.encode(request.password());
        EmailAddress email = request.email() != null ? new EmailAddress(request.email()) : null;
        PhoneNumber phone = request.phone() != null ? new PhoneNumber(request.phone()) : null;
        // EncodedPassword.of fails fast if the encoder ever returns raw input.
        User user = User.create(request.username(), EncodedPassword.of(encodedPassword), email, phone,
                clockProvider.clock());
        final User savedUser;
        try {
            savedUser = saveUserPort.save(user);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // Check-then-save TOCTOU: two concurrent registrations with the same
            // username both pass the findByUsername guard above. The DB unique
            // constraint is the final arbiter — translate it back to the domain
            // conflict instead of leaking a generic 409/500.
            throw new UsernameAlreadyTakenException(request.username());
        }
        savedUser.recordRegistration(clockProvider.clock());
        domainEventPublisherService.publishEvents(savedUser);

        log.info("User registration completed");
    }
}
