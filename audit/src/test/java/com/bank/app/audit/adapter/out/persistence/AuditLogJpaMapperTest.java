package com.bank.app.audit.adapter.out.persistence;

import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("AuditLogJpaMapper")
class AuditLogJpaMapperTest {

    private AuditLogJpaMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AuditLogJpaMapper();
    }

    @Nested
    @DisplayName("toJpaEntity")
    class ToJpaEntity {

        @Test
        @DisplayName("should map valid AuditLog")
        void shouldMapValidAuditLog() {
            LocalDateTime now = LocalDateTime.now();
            AuditLog auditLog = new AuditLog(1L, "testuser", AuditAction.ACCOUNT_CREATED, "details", now);

            AuditLogJpaEntity entity = mapper.toJpaEntity(auditLog);

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getUsername()).isEqualTo("testuser");
            assertThat(entity.getAction()).isEqualTo("ACCOUNT_CREATED");
            assertThat(entity.getDetails()).isEqualTo("details");
            assertThat(entity.getTimestamp()).isEqualTo(now);
        }

        @Test
        @DisplayName("should throw when auditLog is null")
        void shouldThrowWhenAuditLogIsNull() {
            assertThatThrownBy(() -> mapper.toJpaEntity(null))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("AuditLog must not be null");
        }
    }

    @Nested
    @DisplayName("toDomain")
    class ToDomain {

        @Test
        @DisplayName("should map valid entity")
        void shouldMapValidEntity() {
            LocalDateTime now = LocalDateTime.now();
            AuditLogJpaEntity entity = new AuditLogJpaEntity(1L, "testuser", "ACCOUNT_CREATED", "details", now);

            AuditLog auditLog = mapper.toDomain(entity);

            assertThat(auditLog.getId()).isEqualTo(1L);
            assertThat(auditLog.getUsername()).isEqualTo("testuser");
            assertThat(auditLog.getAction()).isEqualTo(AuditAction.ACCOUNT_CREATED);
            assertThat(auditLog.getDetails()).isEqualTo("details");
            assertThat(auditLog.getTimestamp()).isEqualTo(now);
        }

        @Test
        @DisplayName("should throw when entity is null")
        void shouldThrowWhenEntityIsNull() {
            assertThatThrownBy(() -> mapper.toDomain(null))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Entity must not be null");
        }
    }
}
