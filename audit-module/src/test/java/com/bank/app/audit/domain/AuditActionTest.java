package com.bank.app.audit.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditActionTest {

    @Test
    void shouldHaveAllExpectedEnumValues() {
        assertEquals(3, AuditAction.values().length);
    }

    @Test
    void shouldResolveCreatedAction() {
        AuditAction action = AuditAction.valueOf("ACCOUNT_CREATED");
        assertEquals(AuditAction.ACCOUNT_CREATED, action);
    }

    @Test
    void shouldResolveTransferExecutedAction() {
        AuditAction action = AuditAction.valueOf("TRANSFER_EXECUTED");
        assertEquals(AuditAction.TRANSFER_EXECUTED, action);
    }

    @Test
    void shouldResolveTransferCancelledAction() {
        AuditAction action = AuditAction.valueOf("TRANSFER_CANCELLED");
        assertEquals(AuditAction.TRANSFER_CANCELLED, action);
    }

    @Test
    void shouldThrowExceptionForInvalidAction() {
        assertThrows(IllegalArgumentException.class, () -> AuditAction.valueOf("INVALID_ACTION"));
    }

    @Test
    void shouldReturnCorrectNameForCreated() {
        assertEquals("ACCOUNT_CREATED", AuditAction.ACCOUNT_CREATED.name());
    }

    @Test
    void shouldReturnCorrectNameForTransferExecuted() {
        assertEquals("TRANSFER_EXECUTED", AuditAction.TRANSFER_EXECUTED.name());
    }

    @Test
    void shouldReturnCorrectNameForTransferCancelled() {
        assertEquals("TRANSFER_CANCELLED", AuditAction.TRANSFER_CANCELLED.name());
    }
}
