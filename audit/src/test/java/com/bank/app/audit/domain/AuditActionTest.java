package com.bank.app.audit.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AuditActionTest {

    @Test
    void shouldHaveAllExpectedEnumValues() {
        assertEquals(7, AuditAction.values().length);
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
    void shouldResolveAccountDebitedAction() {
        AuditAction action = AuditAction.valueOf("ACCOUNT_DEBITED");
        assertEquals(AuditAction.ACCOUNT_DEBITED, action);
    }

    @Test
    void shouldResolveAccountCreditedAction() {
        AuditAction action = AuditAction.valueOf("ACCOUNT_CREDITED");
        assertEquals(AuditAction.ACCOUNT_CREDITED, action);
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

    @Test
    void shouldReturnCorrectNameForAccountDebited() {
        assertEquals("ACCOUNT_DEBITED", AuditAction.ACCOUNT_DEBITED.name());
    }

    @Test
    void shouldReturnCorrectNameForAccountCredited() {
        assertEquals("ACCOUNT_CREDITED", AuditAction.ACCOUNT_CREDITED.name());
    }

    @Test
    void shouldResolveAccountSuspendedAction() {
        AuditAction action = AuditAction.valueOf("ACCOUNT_SUSPENDED");
        assertEquals(AuditAction.ACCOUNT_SUSPENDED, action);
    }

    @Test
    void shouldResolveAccountClosedAction() {
        AuditAction action = AuditAction.valueOf("ACCOUNT_CLOSED");
        assertEquals(AuditAction.ACCOUNT_CLOSED, action);
    }

    @Test
    void shouldResolveFromString() {
        AuditAction action = AuditAction.fromString("ACCOUNT_CREATED");
        assertEquals(AuditAction.ACCOUNT_CREATED, action);
    }

    @Test
    void shouldThrowWhenFromStringIsNull() {
        assertThrows(IllegalArgumentException.class, () -> AuditAction.fromString(null));
    }
}
