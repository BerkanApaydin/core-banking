package com.bank.app.common.application.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxEventTypes")
class OutboxEventTypesTest {

    @Test
    @DisplayName("matches should reject a null stored event type")
    void shouldRejectNullStoredEventType() {
        assertThat(OutboxEventTypes.matches("AccountCreated", null)).isFalse();
    }

    @Test
    @DisplayName("matches should accept the legacy simple name")
    void shouldMatchLegacySimpleName() {
        assertThat(OutboxEventTypes.matches("AccountCreated", "AccountCreated")).isTrue();
    }

    @Test
    @DisplayName("matches should accept a fully qualified event type")
    void shouldMatchFullyQualifiedEventType() {
        assertThat(OutboxEventTypes.matches("AccountCreated",
                "com.bank.app.account.domain.event.AccountCreated")).isTrue();
    }

    @Test
    @DisplayName("matches should reject a different event type")
    void shouldRejectDifferentEventType() {
        assertThat(OutboxEventTypes.matches("AccountCreated", "AccountClosed")).isFalse();
        assertThat(OutboxEventTypes.matches("AccountCreated",
                "com.bank.app.account.domain.event.AccountClosed")).isFalse();
    }

    @Test
    @DisplayName("simpleName should return null for a null event type")
    void shouldReturnNullSimpleNameForNull() {
        assertThat(OutboxEventTypes.simpleName(null)).isNull();
    }

    @Test
    @DisplayName("simpleName should preserve a name without a package separator")
    void shouldPreserveUnqualifiedSimpleName() {
        assertThat(OutboxEventTypes.simpleName("AccountCreated")).isEqualTo("AccountCreated");
    }

    @Test
    @DisplayName("simpleName should return the segment after the last package separator")
    void shouldExtractTheLastSegment() {
        assertThat(OutboxEventTypes.simpleName(
                "com.bank.app.account.domain.event.AccountCreated")).isEqualTo("AccountCreated");
        assertThat(OutboxEventTypes.simpleName(".HiddenEvent")).isEqualTo("HiddenEvent");
    }
}
