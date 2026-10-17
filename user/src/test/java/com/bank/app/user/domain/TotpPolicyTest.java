package com.bank.app.user.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TotpPolicyTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldVerifyThroughPolicy() {
        TotpPolicy policy = new TotpPolicy(30, 6, 1);
        byte[] secret = Totp.generateSecret();
        long step = FIXED.millis() / 1000L / 30L;

        assertTrue(policy.verify(secret, Totp.generateCode(secret, step, 6), FIXED));
        assertFalse(policy.verify(secret, "000000", FIXED));
    }

    @Test
    void shouldRejectInvalidPolicy() {
        assertThrows(IllegalArgumentException.class, () -> new TotpPolicy(0, 6, 1));
        assertThrows(IllegalArgumentException.class, () -> new TotpPolicy(30, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> new TotpPolicy(30, 9, 1));
        assertThrows(IllegalArgumentException.class, () -> new TotpPolicy(30, 6, -1));
        assertThrows(IllegalArgumentException.class, () -> new TotpPolicy(30, 6, 3));
    }
}
