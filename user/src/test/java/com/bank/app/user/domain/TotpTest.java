package com.bank.app.user.domain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TotpTest {

    // RFC 6238 Appendix B: SHA1 secret "12345678901234567890", T0=0, X=30.
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private static Clock fixed(long epochSecond) {
        return Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC);
    }

    @Test
    void shouldMatchRfc6238Sha1Vectors() {
        // T=59 -> counter 1 -> 94287082 (8 digits).
        assertEquals("94287082", Totp.generateCode(RFC_SECRET, 1L, 8));
        // T=1111111109 -> counter 37037036 -> 07081804.
        assertEquals("07081804", Totp.generateCode(RFC_SECRET, 37037036L, 8));
        // T=1111111111 -> counter 37037037 -> 14050471.
        assertEquals("14050471", Totp.generateCode(RFC_SECRET, 37037037L, 8));
        // T=1234567890 -> counter 41152263 -> 89005924.
        assertEquals("89005924", Totp.generateCode(RFC_SECRET, 41152263L, 8));
        // T=2000000000 -> counter 66666666 -> 69279037.
        assertEquals("69279037", Totp.generateCode(RFC_SECRET, 66666666L, 8));
    }

    @Test
    void shouldVerifyCurrentCode() {
        String code = Totp.generateCode(RFC_SECRET, 59L / 30L, 8);

        assertTrue(Totp.verify(RFC_SECRET, code, fixed(59L), 30, 8, 1));
    }

    @Test
    void shouldAcceptAdjacentStepWithinSkew() {
        String previous = Totp.generateCode(RFC_SECRET, (59L / 30L) - 1, 8);

        assertTrue(Totp.verify(RFC_SECRET, previous, fixed(59L), 30, 8, 1));
        assertFalse(Totp.verify(RFC_SECRET, previous, fixed(59L), 30, 8, 0));
    }

    @Test
    void shouldRejectWrongCode() {
        assertFalse(Totp.verify(RFC_SECRET, "12345678", fixed(59L), 30, 8, 1));
    }

    @Test
    void shouldRejectMalformedCodes() {
        assertFalse(Totp.verify(RFC_SECRET, null, fixed(59L), 30, 8, 1));
        assertFalse(Totp.verify(RFC_SECRET, "  ", fixed(59L), 30, 8, 1));
        assertFalse(Totp.verify(RFC_SECRET, "12345", fixed(59L), 30, 8, 1));
        assertFalse(Totp.verify(RFC_SECRET, "123456789", fixed(59L), 30, 8, 1));
        assertFalse(Totp.verify(RFC_SECRET, "12ab5678", fixed(59L), 30, 8, 1));
    }

    @Test
    void shouldRoundTripSixDigitCodes() {
        byte[] secret = Totp.generateSecret();
        String code = Totp.generateCode(secret, 1000L, 6);

        assertEquals(6, code.length());
        assertTrue(Totp.verify(secret, code, fixed(1000L * 30L), 30, 6, 1));
    }

    @Test
    void shouldGenerateUniqueSecrets() {
        assertFalse(Arrays.equals(Totp.generateSecret(), Totp.generateSecret()));
    }

    @Test
    void shouldRoundTripBase32() {
        byte[] secret = Totp.generateSecret();
        String encoded = Totp.base32Encode(secret);

        assertEquals(32, encoded.length());
        assertArrayEquals(secret, Totp.base32Decode(encoded));
        assertArrayEquals(secret, Totp.base32Decode(encoded.toLowerCase(Locale.ROOT)));
    }

    @Test
    void shouldRejectInvalidBase32() {
        assertThrows(IllegalArgumentException.class, () -> Totp.base32Decode("!!!!"));
        assertThrows(IllegalArgumentException.class, () -> Totp.base32Decode("  "));
    }

    @Test
    void shouldBuildProvisioningUri() {
        String uri = Totp.provisioningUri("bank", "alice", "JBSWY3DPEHPK3PXP");

        assertEquals("otpauth://totp/bank:alice?secret=JBSWY3DPEHPK3PXP&issuer=bank&algorithm=SHA1&digits=6&period=30",
                uri);
    }

    @Test
    void shouldRejectInvalidInputs() {
        assertThrows(NullPointerException.class, () -> Totp.generateCode(null, 1L, 6));
        assertThrows(IllegalArgumentException.class, () -> Totp.generateCode(new byte[0], 1L, 6));
        assertThrows(IllegalArgumentException.class, () -> Totp.generateCode(RFC_SECRET, 1L, 5));
        assertThrows(IllegalArgumentException.class, () -> Totp.generateCode(RFC_SECRET, 1L, 9));
        assertThrows(NullPointerException.class, () -> Totp.base32Encode(null));
        assertThrows(NullPointerException.class, () -> Totp.verify(RFC_SECRET, "123456", null, 30, 6, 1));
    }
}
