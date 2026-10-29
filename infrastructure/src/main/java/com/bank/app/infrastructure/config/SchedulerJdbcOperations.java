package com.bank.app.infrastructure.config;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Scheduler-scoped {@link JdbcTemplate} wrapper.
 *
 * <p>Exposing a raw {@code JdbcTemplate} bean would suppress Boot's primary
 * {@code jdbcTemplate} auto-configuration for the rest of the application.
 */
public record SchedulerJdbcOperations(JdbcTemplate jdbcTemplate) {
}
