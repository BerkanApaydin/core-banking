package com.bank.app.user.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
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
    void shouldEncodeBase32BlockRemainders() {
        // 5 bytes = 40 bits: exact block multiple, no remainder.
        assertEquals("JBSWY3DP", Totp.base32Encode("Hello".getBytes(StandardCharsets.US_ASCII)));
        // 1 byte = 8 bits: one full 5-bit group plus a 3-bit remainder. A
        // right-shift mutation of the discard turns 0xFF into "77" not "74".
        assertEquals("74", Totp.base32Encode(new byte[]{(byte) 0xFF}));
        // 20-byte RFC secret: boundary between 5-bit groups and the remainder.
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",
                Totp.base32Encode(RFC_SECRET));
        assertEquals("", Totp.base32Encode(new byte[0]));
    }

    @Test
    void shouldCompareConstantTimeStringsByContent() throws Exception {
        Method equals = Totp.class.getDeclaredMethod("constantTimeEquals", String.class, String.class);
        equals.setAccessible(true);

        assertTrue((Boolean) equals.invoke(null, "94287082", "94287082"));
        assertFalse((Boolean) equals.invoke(null, "94287082", "94287083"));
        assertFalse((Boolean) equals.invoke(null, "94287082", "9428708"));
        assertFalse((Boolean) equals.invoke(null, "9428708", "94287082"));
        assertTrue((Boolean) equals.invoke(null, "", ""));
    }

    @Test
    void shouldLeaveUnreservedUriCharactersUnencodedAtAsciiBoundaries() {
        String uri = Totp.provisioningUri("azAZ09", "ZzAa09", "JBSWY3DPEHPK3PXP");

        assertEquals("otpauth://totp/azAZ09:ZzAa09?secret=JBSWY3DPEHPK3PXP"
                        + "&issuer=azAZ09&algorithm=SHA1&digits=6&period=30",
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
