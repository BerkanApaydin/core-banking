package com.bank.app.audit.application.dto;

import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mapping contract for audit reads: every domain field must surface, nullable
 * identities ({@code id}, {@code actorUserId}) must survive as null, and a
 * null log must fail fast instead of producing a half-empty row.
 */
@DisplayName("AuditLogResponse")
class AuditLogResponseTest {

    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Test
    @DisplayName("should map every field from the domain log")
    void shouldMapAllFields() {
        // Arrange
        AuditLog log = new AuditLog(
                10L, "alice", AuditAction.TRANSFER_EXECUTED, "Transfer completed", TIMESTAMP, 7L);

        // Act
        AuditLogResponse response = AuditLogResponse.from(log);

        // Assert
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.actorUserId()).isEqualTo(7L);
        assertThat(response.action()).isEqualTo("TRANSFER_EXECUTED");
        assertThat(response.details()).isEqualTo("Transfer completed");
        assertThat(response.timestamp()).isEqualTo(TIMESTAMP);
    }

    @Test
    @DisplayName("should preserve null identities for anonymous actors")
    void shouldPreserveNullIdentities() {
        // Arrange
        AuditLog log = new AuditLog(
                null, "system", AuditAction.LOGIN_FAILED, "Failed login", TIMESTAMP, null);

        // Act
        AuditLogResponse response = AuditLogResponse.from(log);

        // Assert
        assertThat(response.id()).isNull();
        assertThat(response.actorUserId()).isNull();
        assertThat(response.username()).isEqualTo("system");
    }

    @Test
    @DisplayName("should fail fast on a null log")
    void shouldFailFastOnNull() {
        assertThatThrownBy(() -> AuditLogResponse.from(null))
                .isInstanceOf(NullPointerException.class);
    }
}
