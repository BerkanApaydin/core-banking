package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.domain.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fallback contract for problem-detail messages.
 *
 * <p>Missing keys, echoed keys and empty bundle values must all degrade to the
 * caller-supplied generic message — never to raw exception detail.
 */
@DisplayName("ProblemMessageResolver")
class ProblemMessageResolverTest {

    private static final Locale LOCALE = Locale.ENGLISH;

    private StaticMessageSource messageSource;
    private ProblemMessageResolver resolver;

    @BeforeEach
    void setUp() {
        // Pin the resolution locale: StaticMessageSource entries are registered
        // for ENGLISH, and the machine default varies (e.g. tr_TR on dev boxes).
        LocaleContextHolder.setLocale(LOCALE);
        messageSource = new StaticMessageSource();
        resolver = new ProblemMessageResolver(messageSource);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("should resolve a known key with arguments")
    void shouldResolveKnownKey() {
        // Arrange
        messageSource.addMessage("error.insufficient_balance", LOCALE,
                "Insufficient balance: {0}");

        // Act
        String resolved = resolver.resolveMessage(
                "error.insufficient_balance", new Object[]{"100.00"});

        // Assert
        assertThat(resolved).isEqualTo("Insufficient balance: 100.00");
    }

    @Test
    @DisplayName("should echo the key when the bundle has no entry")
    void shouldEchoMissingKey() {
        // Act
        String resolved = resolver.resolveMessage("error.unknown_key");

        // Assert
        assertThat(resolved).isEqualTo("error.unknown_key");
    }

    @Test
    @DisplayName("should return empty string for null key")
    void shouldReturnEmptyForNullKey() {
        assertThat(resolver.resolveMessage(null)).isEmpty();
        assertThat(resolver.resolveMessage(null, new Object[]{"x"})).isEmpty();
    }

    @Test
    @DisplayName("should fall back when the bundle echoes the key")
    void shouldFallBackOnEchoedKey() {
        // Arrange — a bundle that returns the key itself (misconfiguration).
        messageSource.addMessage("error.echo", LOCALE, "error.echo");

        // Act
        String resolved = resolver.resolveOrDefault("error.echo", "Something went wrong");

        // Assert
        assertThat(resolved).isEqualTo("Something went wrong");
    }

    @Test
    @DisplayName("should fall back when the bundle value is empty")
    void shouldFallBackOnEmptyValue() {
        // Arrange
        messageSource.addMessage("error.empty", LOCALE, "");

        // Act
        String resolved = resolver.resolveOrDefault("error.empty", "Something went wrong");

        // Assert
        assertThat(resolved).isEqualTo("Something went wrong");
    }

    @Test
    @DisplayName("should prefer the resolved message over the fallback")
    void shouldPreferResolvedOverFallback() {
        // Arrange
        messageSource.addMessage("error.known", LOCALE, "Known problem");

        // Act
        String resolved = resolver.resolveOrDefault("error.known", "Something went wrong");

        // Assert
        assertThat(resolved).isEqualTo("Known problem");
    }

    @Test
    @DisplayName("should resolve business messages from the bundle")
    void shouldResolveBusinessMessage() {
        // Arrange
        messageSource.addMessage("error.account_not_found", LOCALE, "Account not found: TR1");
        BusinessException ex = businessException("error.account_not_found", null, "default detail");

        // Act
        String resolved = resolver.resolveBusinessMessage(ex);

        // Assert
        assertThat(resolved).isEqualTo("Account not found: TR1");
    }

    @Test
    @DisplayName("should never reflect raw detail when the key is missing (fail-closed catalog)")
    void shouldUseGenericMessageWhenKeyMissing() {
        // Arrange — the default message carries PII-like detail that must
        // never reach the wire; only the server log may keep it.
        BusinessException ex = businessException("error.missing", null, "Balance: 100.00 TRY, IBAN: TR123");

        // Act
        String resolved = resolver.resolveBusinessMessage(ex);

        // Assert
        assertThat(resolved).isEqualTo("Request could not be completed.");
        assertThat(resolved).doesNotContain("100.00", "TR123");
    }

    @Test
    @DisplayName("should prefer the catalog generic message when it exists")
    void shouldPreferCatalogGenericMessage() {
        // Arrange
        messageSource.addMessage("error.general_internal_error", LOCALE, "Catalog generic failure.");
        BusinessException ex = businessException("error.missing", null, null);

        // Act
        String resolved = resolver.resolveBusinessMessage(ex);

        // Assert
        assertThat(resolved).isEqualTo("Catalog generic failure.");
    }

    private static BusinessException businessException(
            String messageKey, Object[] args, String defaultMessage) {
        return new BusinessException(messageKey, args, defaultMessage) {
            private static final long serialVersionUID = 1L;
        };
    }
}
