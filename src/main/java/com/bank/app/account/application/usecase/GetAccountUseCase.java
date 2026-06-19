package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.account.exception.AccountNotFoundException;
import com.bank.app.account.application.port.in.GetAccountPort;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GetAccountUseCase implements GetAccountPort {

    private final LoadAccountPort loadAccountPort;
    private final SecurityContextPort securityContextPort;

    public GetAccountUseCase(LoadAccountPort loadAccountPort, SecurityContextPort securityContextPort) {
        this.loadAccountPort = loadAccountPort;
        this.securityContextPort = securityContextPort;
    }

    public AccountResponse getById(Long id) {
        Objects.requireNonNull(id, "Account ID null olamaz");
        Account account = loadAccountPort.findById(id)
            .orElseThrow(() -> new AccountNotFoundException(id));
        checkAuthorization(account);
        return AccountResponse.from(account);
    }

    public AccountResponse getByIban(String ibanValue) {
        Objects.requireNonNull(ibanValue, "IBAN null olamaz");
        Iban iban = new Iban(ibanValue);
        Account account = loadAccountPort.findByIban(iban)
            .orElseThrow(() -> new AccountNotFoundException(ibanValue));
        checkAuthorization(account);
        return AccountResponse.from(account);
    }

    public List<AccountResponse> getAll() {
        return securityContextPort.getCurrentUserId()
            .map(userId -> loadAccountPort.findByUserId(userId).stream()
                .map(AccountResponse::from)
                .collect(Collectors.toList()))
            .orElseThrow(() -> new AccessDeniedException("Bu işlem için giriş yapmalısınız."));
    }

    private void checkAuthorization(Account account) {
        securityContextPort.checkUserAuthorization(account.getUserId(), "Bu hesaba erişim yetkiniz yok.");
    }
}
