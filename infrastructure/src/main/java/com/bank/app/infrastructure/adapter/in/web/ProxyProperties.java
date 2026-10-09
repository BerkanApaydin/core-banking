package com.bank.app.infrastructure.adapter.in.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.proxy")
public record ProxyProperties(
        @DefaultValue("false") boolean trustForwardedHeaders
) {}
