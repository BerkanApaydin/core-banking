package com.bank.app.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.demo.seed")
public record DemoSeedProperties(
        @DefaultValue("") String ahmetPassword,
        @DefaultValue("") String aysePassword
) {}
