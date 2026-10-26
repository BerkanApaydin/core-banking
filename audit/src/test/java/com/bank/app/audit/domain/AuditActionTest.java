package com.bank.app.audit.domain;

import com.bank.app.audit.domain.exception.UnknownAuditActionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AuditActionTest {

    @Test
    void shouldHaveAllExpectedEnumValues() {
        // 7 money-movement actions + 5 authentication-lifecycle actions (K11/D4)
        // + 1 reserved reconciliation outcome (TRANSFER_MARKED_FAILED)
        // + 4 MFA/password-reset lifecycle actions (V54/V55)
        // + 1 MFA re-authentication failure (R-U08/V58).
        assertEquals(18, AuditAction.values().length);
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

    @Test
    void shouldRejectAnUnknownActionName() {
        UnknownAuditActionException failure =
                assertThrows(UnknownAuditActionException.class, () -> AuditAction.fromString("unknown_action"));

        assertTrue(failure.getMessage().contains("unknown_action"));
    }

    @Test
    void shouldRejectAnEmptyActionName() {
        // An empty string is not a mapped action, so it takes the same
        // server-side path as any other unmapped name.
        assertThrows(UnknownAuditActionException.class, () -> AuditAction.fromString(""));
    }

    @Test
    void shouldRejectABlankActionName() {
        assertThrows(UnknownAuditActionException.class, () -> AuditAction.fromString("  "));
    }

    @Test
    void shouldResolveOnlyTheExactUppercaseConstant() {
        // The mapping is case sensitive: a producer that publishes a lowercase
        // action would silently kill the audit row of that event.
        assertEquals(AuditAction.ACCOUNT_CREATED, AuditAction.fromString("ACCOUNT_CREATED"));
        assertThrows(UnknownAuditActionException.class, () -> AuditAction.fromString("account_created"));
    }

    @Test
    void shouldRejectATransferCreatedActionBecauseNoSuchActionExists() {
        // There is no TRANSFER_CREATED action in the ledger: the transfer
        // lifecycle is TRANSFER_EXECUTED / TRANSFER_CANCELLED /
        // TRANSFER_MARKED_FAILED. A producer publishing TRANSFER_CREATED
        // would abort the business transaction instead of writing a row.
        assertThrows(UnknownAuditActionException.class, () -> AuditAction.fromString("TRANSFER_CREATED"));
        assertThrows(UnknownAuditActionException.class, () -> AuditAction.fromString("transfer_created"));
    }
}
