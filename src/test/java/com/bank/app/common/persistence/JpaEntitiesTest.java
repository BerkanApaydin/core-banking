package com.bank.app.common.persistence;

import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.infrastructure.persistence.AuditLogJpaEntity;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.transfer.infrastructure.persistence.TransferJpaEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class JpaEntitiesTest {

    static class TestAuditable extends AuditableJpaEntity {}

    @Test
    void shouldCreateAuditableJpaEntity() {
        TestAuditable entity = new TestAuditable();
        LocalDateTime now = LocalDateTime.now();

        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy("creator");
        entity.setUpdatedBy("updater");

        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
        assertEquals("creator", entity.getCreatedBy());
        assertEquals("updater", entity.getUpdatedBy());
    }

    @Test
    void shouldCreateAuditLogJpaEntity() {
        LocalDateTime now = LocalDateTime.now();
        AuditLogJpaEntity entity = new AuditLogJpaEntity(1L, "user", AuditAction.ACCOUNT_CREATED, "details", now);

        assertEquals(1L, entity.getId());
        assertEquals("user", entity.getUsername());
        assertEquals(AuditAction.ACCOUNT_CREATED, entity.getAction());
        assertEquals("details", entity.getDetails());
        assertEquals(now, entity.getTimestamp());

        AuditLogJpaEntity empty = new AuditLogJpaEntity();
        empty.setId(2L);
        empty.setUsername("user2");
        empty.setAction(AuditAction.TRANSFER_EXECUTED);
        empty.setDetails("details2");
        empty.setTimestamp(now);

        assertEquals(2L, empty.getId());
        assertEquals("user2", empty.getUsername());
        assertEquals(AuditAction.TRANSFER_EXECUTED, empty.getAction());
        assertEquals("details2", empty.getDetails());
        assertEquals(now, empty.getTimestamp());
    }

    @Test
    void shouldCreateIdempotencyKeyJpaEntity() {
        LocalDateTime now = LocalDateTime.now();
        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity("key", "PENDING", "body", now);

        assertEquals("key", entity.getKey());
        assertEquals("PENDING", entity.getStatus());
        assertEquals("body", entity.getResponseBody());
        assertEquals(now, entity.getCreatedAt());

        IdempotencyKeyJpaEntity empty = new IdempotencyKeyJpaEntity();
        empty.setKey("key2");
        empty.setStatus("COMPLETED");
        empty.setResponseBody("body2");
        empty.setCreatedAt(now);

        assertEquals("key2", empty.getKey());
        assertEquals("COMPLETED", empty.getStatus());
        assertEquals("body2", empty.getResponseBody());
        assertEquals(now, empty.getCreatedAt());
    }

    @Test
    void shouldCreateUserJpaEntity() {
        UserJpaEntity entity = new UserJpaEntity(1L, "user", "pass", "role");

        assertEquals(1L, entity.getId());
        assertEquals("user", entity.getUsername());
        assertEquals("pass", entity.getPassword());
        assertEquals("role", entity.getRole());

        UserJpaEntity empty = new UserJpaEntity();
        empty.setId(2L);
        empty.setUsername("user2");
        empty.setPassword("pass2");
        empty.setRole("role2");

        assertEquals(2L, empty.getId());
        assertEquals("user2", empty.getUsername());
        assertEquals("pass2", empty.getPassword());
        assertEquals("role2", empty.getRole());
    }

    @Test
    void shouldCreateAccountJpaEntity() {
        AccountJpaEntity entity = new AccountJpaEntity(1L, 2L, "IBAN", "name", BigDecimal.TEN, "TRY", true);

        assertEquals(1L, entity.getId());
        assertEquals(2L, entity.getUserId());
        assertEquals("IBAN", entity.getIban());
        assertEquals("name", entity.getOwnerName());
        assertEquals(BigDecimal.TEN, entity.getBalance());
        assertEquals("TRY", entity.getCurrency());
        assertTrue(entity.isActive());

        AccountJpaEntity empty = new AccountJpaEntity();
        empty.setId(10L);
        empty.setUserId(20L);
        empty.setIban("IBAN2");
        empty.setOwnerName("name2");
        empty.setBalance(BigDecimal.ONE);
        empty.setCurrency("USD");
        empty.setActive(false);
        empty.setVersion(5L);

        assertEquals(10L, empty.getId());
        assertEquals(20L, empty.getUserId());
        assertEquals("IBAN2", empty.getIban());
        assertEquals("name2", empty.getOwnerName());
        assertEquals(BigDecimal.ONE, empty.getBalance());
        assertEquals("USD", empty.getCurrency());
        assertFalse(empty.isActive());
        assertEquals(5L, empty.getVersion());
    }

    @Test
    void shouldCreateTransferJpaEntity() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity = new TransferJpaEntity(1L, 2L, 3L, BigDecimal.TEN, "TRY", "PENDING", now);

        assertEquals(1L, entity.getId());
        assertEquals(2L, entity.getSenderAccountId());
        assertEquals(3L, entity.getReceiverAccountId());
        assertEquals(BigDecimal.TEN, entity.getAmount());
        assertEquals("TRY", entity.getCurrency());
        assertEquals("PENDING", entity.getStatus());
        assertEquals(now, entity.getCreatedAt());

        TransferJpaEntity empty = new TransferJpaEntity();
        empty.setId(10L);
        empty.setSenderAccountId(20L);
        empty.setReceiverAccountId(30L);
        empty.setAmount(BigDecimal.ONE);
        empty.setCurrency("USD");
        empty.setStatus("COMPLETED");
        empty.setCreatedAt(now);

        assertEquals(10L, empty.getId());
        assertEquals(20L, empty.getSenderAccountId());
        assertEquals(30L, empty.getReceiverAccountId());
        assertEquals(BigDecimal.ONE, empty.getAmount());
        assertEquals("USD", empty.getCurrency());
        assertEquals("COMPLETED", empty.getStatus());
        assertEquals(now, empty.getCreatedAt());
    }
}
