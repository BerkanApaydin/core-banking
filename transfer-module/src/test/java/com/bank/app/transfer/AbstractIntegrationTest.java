package com.bank.app.transfer;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EntityScan(basePackages = {"com.bank.app.transfer", "com.bank.app.common.outbox", "com.bank.app.user", "com.bank.app.account"})
@EnableJpaRepositories(basePackages = {"com.bank.app.transfer", "com.bank.app.common.outbox", "com.bank.app.user", "com.bank.app.account"})
public abstract class AbstractIntegrationTest
        extends com.bank.app.common.AbstractIntegrationTest {
}
