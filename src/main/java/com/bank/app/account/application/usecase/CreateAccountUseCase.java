package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.audit.application.AuditLogger;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.account.exception.DuplicateIbanException;
import com.bank.app.common.domain.Money;
import com.bank.app.account.application.port.in.CreateAccountPort;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
@Transactional
public class CreateAccountUseCase implements CreateAccountPort {

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final AuditLogger auditLogger;
    private final SecurityContextPort securityContextPort;

    public CreateAccountUseCase(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort, AuditLogger auditLogger, SecurityContextPort securityContextPort) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.auditLogger = auditLogger;
        this.securityContextPort = securityContextPort;
    }

    public AccountResponse execute(CreateAccountRequest request) {
        Objects.requireNonNull(request, "Request null olamaz");
        
        // Authorization check: User can only create accounts for themselves
        securityContextPort.checkUserAuthorization(request.userId(), "Başka bir kullanıcı adına hesap oluşturamazsınız.");

        Iban iban = new Iban(request.iban());
        if (loadAccountPort.findByIban(iban).isPresent()) {
            throw new DuplicateIbanException("Bu IBAN ile kayıtlı bir hesap zaten mevcut: " + iban.value());
        }

        Money.Currency currency = request.currency();

        Money balance = new Money(request.initialBalance(), currency);
        Account account = new Account(null, request.userId(), iban, request.ownerName(), balance, true);

        Account savedAccount = saveAccountPort.save(account);

        auditLogger.log(
            AuditAction.ACCOUNT_CREATED,
            String.format("Yeni hesap oluşturuldu. ID: %d, IBAN: %s, Kullanıcı ID: %d, Bakiye: %s %s",
                savedAccount.getId(), savedAccount.getIban().value(), savedAccount.getUserId(),
                savedAccount.getBalance().amount(), savedAccount.getBalance().currency())
        );

        return AccountResponse.from(savedAccount);
    }
}
