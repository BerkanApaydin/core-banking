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

    @Test
    void everyOperationKeyComponentContributes() {
        // Kills the per-component VoidMethodCall mutants: each pair differs
        // in exactly one component, so dropping that component's digest update
        // collapses the pair and fails the assertion.
        String base = IdempotencyFingerprint.operationKey("user", "alice", "POST", "/transfers", "key-1");
        assertNotEquals(base, IdempotencyFingerprint.operationKey("user", "alice", "PUT", "/transfers", "key-1"));
        assertNotEquals(base, IdempotencyFingerprint.operationKey("user", "bob", "POST", "/transfers", "key-1"));
        assertNotEquals(base, IdempotencyFingerprint.operationKey("user", "alice", "POST", "/register", "key-1"));
        assertNotEquals(base, IdempotencyFingerprint.operationKey("admin", "alice", "POST", "/transfers", "key-1"));
        assertNotEquals(base, IdempotencyFingerprint.operationKey("user", "alice", "POST", "/transfers", "key-2"));
    }

    @Test
    void everyRequestHashComponentContributes() {
        String base = IdempotencyFingerprint.requestHash("POST", "/transfers", "a=1", "100".getBytes());
        assertNotEquals(base, IdempotencyFingerprint.requestHash("PUT", "/transfers", "a=1", "100".getBytes()));
        assertNotEquals(base, IdempotencyFingerprint.requestHash("POST", "/register", "a=1", "100".getBytes()));
        assertNotEquals(base, IdempotencyFingerprint.requestHash("POST", "/transfers", "a=2", "100".getBytes()));
        assertNotEquals(base, IdempotencyFingerprint.requestHash("POST", "/transfers", "a=1", "200".getBytes()));
        assertNotEquals(base, IdempotencyFingerprint.requestHash("POST", "/transfers", "a=1", null));
    }

    @Test
    void fingerprintsAreStableAcrossVersions() {
        // Golden pins: the "v1" namespace and length-delimited encoding are a
        // cross-deploy dedup contract — any dropped digest update (including
        // the constant "v1") changes these hashes and fails the build.
        assertEquals("http_303ccfc3077562920be6ad14bc5f18e0c0a8ff80655bf997325346df70318fdd",
                IdempotencyFingerprint.operationKey("user", "a_b", "POST", "/transfers", "c"));
        assertEquals("8195a5c574ae73d2b29eb6bdddf5d1ec8be767e2aa178237f656115098e41af8",
                IdempotencyFingerprint.requestHash("POST", "/transfers", null, "100".getBytes()));
    }
}
