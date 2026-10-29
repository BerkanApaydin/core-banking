package com.bank.app.infrastructure.config;

import org.springframework.transaction.PlatformTransactionManager;

/**
 * Scheduler-scoped transaction manager wrapper.
 *
 * <p>Exposing a raw {@code PlatformTransactionManager} bean would suppress
 * Boot's primary transaction-manager auto-configuration.
 */
public record SchedulerTransactionManager(PlatformTransactionManager transactionManager) {
}
