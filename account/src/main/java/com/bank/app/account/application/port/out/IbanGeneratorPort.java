package com.bank.app.account.application.port.out;

import com.bank.app.common.domain.Iban;

public interface IbanGeneratorPort {
    Iban generate();
}
