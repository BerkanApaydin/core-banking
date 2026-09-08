package com.bank.app.infrastructure.adapter.in.config;

import com.bank.app.common.domain.Iban;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.common.iban")
public class IbanProperties {

    private String pattern = Iban.DEFAULT_IBAN_REGEX;

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    @PostConstruct
    public void init() {
        Iban.configurePattern(pattern);
    }
}
