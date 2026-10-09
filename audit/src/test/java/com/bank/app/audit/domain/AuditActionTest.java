package com.bank.app.audit.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AuditActionTest {

    @Test
    void shouldHaveAllExpectedEnumValues() {
        // 7 money-movement actions + 5 authentication-lifecycle actions (K11/D4)
        // + 1 crash-window reaper outcome (TRANSFER_MARKED_FAILED).
        assertEquals(13, AuditAction.values().length);
    }

    @Test
    void shouldResolveAuthLifecycleActions() {
        assertEquals(AuditAction.LOGIN_SUCCEEDED, AuditAction.fromString("LOGIN_SUCCEEDED"));
        assertEquals(AuditAction.LOGIN_FAILED, AuditAction.fromString("LOGIN_FAILED"));
        assertEquals(AuditAction.LOGOUT, AuditAction.fromString("LOGOUT"));
        assertEquals(AuditAction.PASSWORD_CHANGED, AuditAction.fromString("PASSWORD_CHANGED"));
        assertEquals(AuditAction.TOKEN_REVOKED, AuditAction.fromString("TOKEN_REVOKED"));
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

    @Test
    void shouldThrowUnknownAuditActionExceptionForUnmappedValue() {
        // A typo'd action name is a server wiring mistake, not a client error:
        // it must surface as IllegalStateException (generic 500 handler),
        // never as IllegalArgumentException (400 invalid-argument handler).
        UnknownAuditActionException failure = assertThrows(UnknownAuditActionException.class,
                () -> AuditAction.fromString("TRANSFER_EXECUTD"));
        assertTrue(failure instanceof IllegalStateException);
        assertTrue(failure.getMessage().contains("TRANSFER_EXECUTD"));
    }
}
