package com.bank.app.account.adapter.out.iban;

import com.bank.app.account.application.port.out.IbanGeneratorPort;
import com.bank.app.common.domain.Iban;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

@Component
public class SecureRandomIbanGenerator implements IbanGeneratorPort {
    private static final long ACCOUNT_NUMBER_SPACE = 10_000_000_000_000_000L;
    // Synthetic bank code 00000 + reserve digit 0: these are simulation-only IBANs.
    private static final String SIMULATION_BANK_PREFIX = "000000";
    private final SecureRandom random = new SecureRandom();

    @Override
    public Iban generate() {
        String accountNumber = String.format(Locale.ROOT, "%016d", random.nextLong(ACCOUNT_NUMBER_SPACE));
        return Iban.fromTurkishBban(SIMULATION_BANK_PREFIX + accountNumber);
    }
}
