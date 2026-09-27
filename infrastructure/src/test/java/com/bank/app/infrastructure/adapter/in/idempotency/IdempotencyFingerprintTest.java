package com.bank.app.infrastructure.adapter.in.idempotency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class IdempotencyFingerprintTest {
    @Test
    void namespaceComponentsCannotCollideAcrossUsersOrEndpoints() {
        String first = IdempotencyFingerprint.operationKey("user", "a_b", "POST", "/transfers", "c");
        String collisionBefore = IdempotencyFingerprint.operationKey("user", "a", "POST", "/transfers", "b_c");
        String otherEndpoint = IdempotencyFingerprint.operationKey("user", "a_b", "POST", "/register", "c");
        String otherScope = IdempotencyFingerprint.operationKey("public", "a_b", "POST", "/transfers", "c");
        assertNotEquals(first, collisionBefore);
        assertNotEquals(first, otherEndpoint);
        assertNotEquals(first, otherScope);
        assertEquals(first, IdempotencyFingerprint.operationKey("user", "a_b", "POST", "/transfers", "c"));
    }

    @Test
    void requestPayloadChangesFingerprint() {
        String first = IdempotencyFingerprint.requestHash("POST", "/transfers", null, "100".getBytes());
        String changed = IdempotencyFingerprint.requestHash("POST", "/transfers", null, "200".getBytes());
        assertNotEquals(first, changed);
    }
}
