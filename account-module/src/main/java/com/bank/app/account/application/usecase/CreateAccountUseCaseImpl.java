package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Iban;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.account.domain.exception.DuplicateIbanException;
import com.bank.app.common.application.UseCase;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import com.bank.app.common.domain.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Objects;

@UseCase
public class CreateAccountUseCaseImpl implements CreateAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateAccountUseCaseImpl.class);

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final EventPublisherPort eventPublisherPort;
    private final AccountAuthorizationService accountAuthorizationService;

    public CreateAccountUseCaseImpl(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort, EventPublisherPort eventPublisherPort, AccountAuthorizationService accountAuthorizationService) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.eventPublisherPort = eventPublisherPort;
        this.accountAuthorizationService = accountAuthorizationService;
    }

    @Override
    public AccountResponse execute(CreateAccountRequest request) {
        Objects.requireNonNull(request, "Request must not be null");

        // Authorization check: User can only create accounts for themselves
        accountAuthorizationService.authorizeUserAction(request.userId(), "You cannot create an account on behalf of another user.");

        Iban iban = new Iban(request.iban());
        if (loadAccountPort.findByIban(iban).isPresent()) {
            throw new DuplicateIbanException(iban.value());
        }

        Currency currency = request.currency();

        Money balance = new Money(request.initialBalance(), currency);
        Account account = new Account(null, new UserId(request.userId()), iban, request.ownerName(), balance, AccountStatus.ACTIVE);

        Account savedAccount = saveAccountPort.save(account);

        eventPublisherPort.publish(new AccountCreatedEvent(
            savedAccount.getId(), savedAccount.getUserId(), savedAccount.getIban(),
            savedAccount.getOwnerName(), savedAccount.getBalance(), LocalDateTime.now()
        ));
        eventPublisherPort.publish(new AuditEvent("ACCOUNT_CREATED",
            String.format("New account created. ID: %d, IBAN: %s, User ID: %d, Balance: %s %s",
                savedAccount.getId(), savedAccount.getIban().value(), savedAccount.getUserId().value(),
                savedAccount.getBalance().amount(), savedAccount.getBalance().currency()),
            LocalDateTime.now()));

        log.info("Account created: id={}, iban={}, owner={}, userId={}",
            savedAccount.getId(), savedAccount.getIban().value(),
            savedAccount.getOwnerName(), savedAccount.getUserId().value());

        return AccountResponse.from(savedAccount);
    }
}
