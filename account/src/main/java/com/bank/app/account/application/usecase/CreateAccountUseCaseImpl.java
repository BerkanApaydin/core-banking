package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.application.port.out.IbanGeneratorPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Iban;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.account.domain.exception.DuplicateIbanException;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.common.domain.exception.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Objects;

@TransactionalUseCase
public class CreateAccountUseCaseImpl implements CreateAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateAccountUseCaseImpl.class);

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final IbanGeneratorPort ibanGeneratorPort;
    private final DomainEventPublisherService domainEventPublisherService;
    private final AuditEventPort auditEventPort;
    private final AccountAuthorizationService accountAuthorizationService;
    private final ClockProviderPort clockProvider;
    private final boolean demoFundingEnabled;

    public CreateAccountUseCaseImpl(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort,
                                    IbanGeneratorPort ibanGeneratorPort,
                                    DomainEventPublisherService domainEventPublisherService, AuditEventPort auditEventPort,
                                    AccountAuthorizationService accountAuthorizationService, ClockProviderPort clockProvider,
                                    boolean demoFundingEnabled) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.ibanGeneratorPort = ibanGeneratorPort;
        this.domainEventPublisherService = domainEventPublisherService;
        this.auditEventPort = auditEventPort;
        this.accountAuthorizationService = accountAuthorizationService;
        this.clockProvider = clockProvider;
        this.demoFundingEnabled = demoFundingEnabled;
    }

    @Override
    public AccountResponse execute(CreateAccountRequest request) {
        Objects.requireNonNull(request, "Request must not be null");

        // Authorization check: User can only create accounts for themselves
        accountAuthorizationService.authorizeUserAction(request.userId(), "You cannot create an account on behalf of another user.");

        if (!demoFundingEnabled && request.initialBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new AuthorizationException("error.demo_funding_disabled", null,
                    "Initial funding is available only in demo mode.");
        }

        Iban iban = selectIban(request.iban());

        Currency currency = request.currency();

        Money balance = Money.exact(request.initialBalance(), currency);
        Account account = new Account(null, new UserId(request.userId()), iban, request.ownerName(), balance, AccountStatus.ACTIVE);

        Account savedAccount = saveAccountPort.save(account);

        domainEventPublisherService.publish(new AccountCreatedEvent(
            savedAccount.getId(), savedAccount.getUserId(), savedAccount.getIban(),
            savedAccount.getOwnerName(), savedAccount.getBalance(), LocalDateTime.now(clockProvider.clock())
        ));
        auditEventPort.publish(new AuditEvent("ACCOUNT_CREATED",
            String.format("New account created. ID: %d",
                savedAccount.getId()),
            LocalDateTime.now(clockProvider.clock()),
            accountAuthorizationService.getCurrentUsername(),
            savedAccount.getUserId().value()));

        log.info("Account created: id={}", savedAccount.getId());

        return AccountResponse.from(savedAccount);
    }

    private Iban selectIban(String trustedSeedIban) {
        if (trustedSeedIban != null) {
            // Existing demo seeds keep stable IBANs across application restarts.
            Iban iban = new Iban(trustedSeedIban);
            iban.requireValidChecksum();
            if (loadAccountPort.findByIban(iban).isPresent()) {
                throw new DuplicateIbanException(iban.value());
            }
            return iban;
        }

        // The database UNIQUE constraint remains the final guard against a
        // concurrent collision after this check.
        for (int attempt = 0; attempt < 5; attempt++) {
            Iban iban = Objects.requireNonNull(ibanGeneratorPort.generate(), "Generated IBAN must not be null");
            iban.requireValidChecksum();
            if (loadAccountPort.findByIban(iban).isEmpty()) {
                return iban;
            }
        }
        throw new IllegalStateException("Could not generate a unique IBAN after five attempts");
    }
}
