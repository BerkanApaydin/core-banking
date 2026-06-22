package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Iban;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.account.domain.exception.DuplicateIbanException;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.security.port.out.SecurityContextPort;
import java.util.Objects;

public class CreateAccountUseCaseImpl implements CreateAccountUseCase {

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final EventPublisherPort eventPublisherPort;
    private final SecurityContextPort securityContextPort;

    public CreateAccountUseCaseImpl(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort, EventPublisherPort eventPublisherPort, SecurityContextPort securityContextPort) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.eventPublisherPort = eventPublisherPort;
        this.securityContextPort = securityContextPort;
    }

    @Override
    public AccountResponse execute(CreateAccountRequest request) {
        Objects.requireNonNull(request, "Request null olamaz");
        
        // Authorization check: User can only create accounts for themselves
        securityContextPort.checkUserAuthorization(request.userId(), "Başka bir kullanıcı adına hesap oluşturamazsınız.");

        Iban iban = new Iban(request.iban());
        if (loadAccountPort.findByIban(iban).isPresent()) {
            throw new DuplicateIbanException("Bu IBAN ile kayıtlı bir hesap zaten mevcut: " + iban.value());
        }

        Currency currency = request.currency();

        Money balance = new Money(request.initialBalance(), currency);
        Account account = new Account(null, request.userId(), iban, request.ownerName(), balance, AccountStatus.ACTIVE);

        Account savedAccount = saveAccountPort.save(account);

        eventPublisherPort.publish(new AccountCreatedEvent(
            savedAccount.getId(), savedAccount.getUserId(), savedAccount.getIban().value(),
            savedAccount.getOwnerName(), savedAccount.getBalance()
        ));

        return AccountResponse.from(savedAccount);
    }
}
