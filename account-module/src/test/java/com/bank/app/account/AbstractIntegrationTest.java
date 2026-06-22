package com.bank.app.account;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EntityScan(basePackages = "com.bank.app")
@EnableJpaRepositories(basePackages = "com.bank.app")
public abstract class AbstractIntegrationTest
        extends com.bank.app.common.AbstractIntegrationTest {
}
