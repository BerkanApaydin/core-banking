package com.bank.app.user.domain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation and encoding edge cases for the TOTP second factor.
 *
 * <p>The risk surface is the login path: malformed policy values, a corrupt
 * (empty) secret row, padded Base32 input from an authenticator app, and
 * enrollment URIs for issuers/usernames containing reserved characters must
 * never turn a rejected code into a server error or produce an unusable
 * enrollment.
 */
@SuppressWarnings("null")
class TotpEdgeCaseTest {

    private static final byte[] SECRET =
            "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final int PERIOD = 30;
    private static final int DIGITS = 6;
    private static final int SKEW = 1;
    private static final int MAX_SKEW = 2;

    /**
     * A fixed base instant whose step is comfortably inside the epoch range.
     * The codes of {@code step-3 .. step+3} are pairwise distinct for this
     * secret, so a rejection can only come from the skew window and never
     * from an accidental 6-digit collision.
     */
    private static final long BASE_EPOCH_SECOND = 1_700_000_000L;
    private static final long BASE_STEP = BASE_EPOCH_SECOND / PERIOD;

    private static Clock at(long epochSecond) {
        return Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC);
    }

    private static Clock atStep(long step) {
        return at(step * PERIOD);
    }

    @Test
    void shouldFailClosedForEmptySecretInsteadOfThrowing() {
        Clock clock = at(59L);

        assertFalse(Totp.verify(new byte[0], "123456", clock, PERIOD, DIGITS, SKEW));
        assertTrue(Totp.verifyWithCounter(new byte[0], "123456", clock, PERIOD, DIGITS, SKEW).isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullCode() {
        assertTrue(Totp.verifyWithCounter(SECRET, null, at(59L), PERIOD, DIGITS, SKEW).isEmpty());
    }

    @Test
    void shouldReturnEmptyForNonPositivePeriod() {
        assertTrue(Totp.verifyWithCounter(SECRET, "123456", at(59L), 0, DIGITS, SKEW).isEmpty());
        assertTrue(Totp.verifyWithCounter(SECRET, "123456", at(59L), -30, DIGITS, SKEW).isEmpty());
    }

    @Test
    void shouldReturnEmptyForNegativeSkew() {
        assertTrue(Totp.verifyWithCounter(SECRET, "123456", at(59L), PERIOD, DIGITS, -1).isEmpty());
    }

    @Test
    void shouldRejectZeroSkewForAdjacentStep() {
        long step = 59L / PERIOD;
        String previousStepCode = Totp.generateCode(SECRET, step - 1, DIGITS);

        assertFalse(Totp.verify(SECRET, previousStepCode, at(59L), PERIOD, DIGITS, 0));
    }

    @Test
    void shouldReturnTheMatchedStepCounter() {
        long step = 59L / PERIOD;
        String previousStepCode = Totp.generateCode(SECRET, step - 1, DIGITS);

        OptionalLong matched = Totp.verifyWithCounter(
                SECRET, previousStepCode, at(59L), PERIOD, DIGITS, SKEW);

        assertTrue(matched.isPresent());
        assertEquals(step - 1, matched.getAsLong());
    }

    @Test
    void shouldAcceptTheForwardNeighbourStepAtSkewOne() {
        // A phone clock running fast produces the NEXT step's code. Rejecting
        // it would lock the legitimate user out of their own second factor.
        String forwardCode = Totp.generateCode(SECRET, BASE_STEP + 1, DIGITS);

        assertTrue(Totp.verify(SECRET, forwardCode, at(BASE_EPOCH_SECOND), PERIOD, DIGITS, SKEW));

        OptionalLong matched = Totp.verifyWithCounter(
                SECRET, forwardCode, at(BASE_EPOCH_SECOND), PERIOD, DIGITS, SKEW);
        assertTrue(matched.isPresent());
        assertEquals(BASE_STEP + 1, matched.getAsLong());
    }

    @Test
    void shouldRejectTheStepBeyondTheForwardSkewBoundary() {
        // One step past the forward boundary must stay outside the window:
        // the acceptance window is exactly 2 * skew + 1 steps.
        String beyondCode = Totp.generateCode(SECRET, BASE_STEP + 2, DIGITS);

        assertFalse(Totp.verify(SECRET, beyondCode, at(BASE_EPOCH_SECOND), PERIOD, DIGITS, SKEW));
        assertTrue(Totp.verifyWithCounter(
                SECRET, beyondCode, at(BASE_EPOCH_SECOND), PERIOD, DIGITS, SKEW).isEmpty());
    }

    @Test
    void shouldRejectTheForwardNeighbourStepAtZeroSkew() {
        String forwardCode = Totp.generateCode(SECRET, BASE_STEP + 1, DIGITS);

        assertFalse(Totp.verify(SECRET, forwardCode, at(BASE_EPOCH_SECOND), PERIOD, DIGITS, 0));
        assertTrue(Totp.verifyWithCounter(
                SECRET, forwardCode, at(BASE_EPOCH_SECOND), PERIOD, DIGITS, 0).isEmpty());
    }

    @Test
    void shouldAcceptTheForwardNeighbourAtMaxSkew() {
        String forwardCode = Totp.generateCode(SECRET, BASE_STEP + MAX_SKEW, DIGITS);

        assertTrue(Totp.verify(SECRET, forwardCode, at(BASE_EPOCH_SECOND), PERIOD, DIGITS, MAX_SKEW));

        OptionalLong matched = Totp.verifyWithCounter(
                SECRET, forwardCode, at(BASE_EPOCH_SECOND), PERIOD, DIGITS, MAX_SKEW);
        assertTrue(matched.isPresent());
        assertEquals(BASE_STEP + MAX_SKEW, matched.getAsLong());
    }

    @Test
    void shouldRejectTheStepBeyondTheMaxSkewForwardBoundary() {
        String beyondCode = Totp.generateCode(SECRET, BASE_STEP + MAX_SKEW + 1, DIGITS);

        assertFalse(Totp.verify(SECRET, beyondCode, at(BASE_EPOCH_SECOND), PERIOD, DIGITS, MAX_SKEW));
    }

    @Test
    void shouldAcceptTheBackwardNeighbourAtMaxSkew() {
        String backwardCode = Totp.generateCode(SECRET, BASE_STEP - MAX_SKEW, DIGITS);

        assertTrue(Totp.verify(SECRET, backwardCode, at(BASE_EPOCH_SECOND), PERIOD, DIGITS, MAX_SKEW));

        OptionalLong matched = Totp.verifyWithCounter(
                SECRET, backwardCode, at(BASE_EPOCH_SECOND), PERIOD, DIGITS, MAX_SKEW);
        assertTrue(matched.isPresent());
        assertEquals(BASE_STEP - MAX_SKEW, matched.getAsLong());
    }

    @Test
    void shouldRejectTheStepBeyondTheMaxSkewBackwardBoundary() {
        String beyondCode = Totp.generateCode(SECRET, BASE_STEP - MAX_SKEW - 1, DIGITS);

        assertFalse(Totp.verify(SECRET, beyondCode, at(BASE_EPOCH_SECOND), PERIOD, DIGITS, MAX_SKEW));
    }

    @Test
    void shouldReturnTheHighestMatchingStepWhenTwoNeighboursShareACode() {
        // Steps 910737 and 910738 produce the same 6-digit code ("911617")
        // for this secret. The javadoc promises the HIGHEST matching step is
        // returned, otherwise the next legitimate code would be rejected as
        // a replay and the user would be locked out.
        long lowerStep = 910_737L;
        long higherStep = 910_738L;
        String collidingCode = Totp.generateCode(SECRET, lowerStep, DIGITS);
        assertEquals(collidingCode, Totp.generateCode(SECRET, higherStep, DIGITS));

        OptionalLong matched = Totp.verifyWithCounter(
                SECRET, collidingCode, atStep(lowerStep), PERIOD, DIGITS, SKEW);

        assertTrue(matched.isPresent());
        assertEquals(higherStep, matched.getAsLong());
    }

    @Test
    void shouldReturnTheHighestMatchingStepAcrossTwoStepsAtMaxSkew() {
        // Steps 153567 and 153569 share a code ("468457"): the window at
        // skew 2 spans both, and the newer one must win.
        long lowerStep = 153_567L;
        long higherStep = 153_569L;
        String collidingCode = Totp.generateCode(SECRET, lowerStep, DIGITS);
        assertEquals(collidingCode, Totp.generateCode(SECRET, higherStep, DIGITS));

        OptionalLong matched = Totp.verifyWithCounter(
                SECRET, collidingCode, atStep(lowerStep), PERIOD, DIGITS, MAX_SKEW);

        assertTrue(matched.isPresent());
        assertEquals(higherStep, matched.getAsLong());
    }

    @Test
    void shouldAcceptTheLowestPossibleCode() {
        // "000000" is a legal code, not a canonical "wrong" one. A leading
        // zero must not be read as "no code given".
        long step = 349_495L;
        assertEquals("000000", Totp.generateCode(SECRET, step, DIGITS));

        assertTrue(Totp.verify(SECRET, "000000", atStep(step), PERIOD, DIGITS, 0));

        OptionalLong matched = Totp.verifyWithCounter(
                SECRET, "000000", atStep(step), PERIOD, DIGITS, 0);
        assertTrue(matched.isPresent());
        assertEquals(step, matched.getAsLong());
    }

    @Test
    void shouldAcceptTheHighestPossibleCode() {
        // "999999" is the upper bound of the 6-digit range and must verify
        // when it is the correct code for the step.
        long step = 691L;
        assertEquals("999999", Totp.generateCode(SECRET, step, DIGITS));

        assertTrue(Totp.verify(SECRET, "999999", atStep(step), PERIOD, DIGITS, 0));

        OptionalLong matched = Totp.verifyWithCounter(
                SECRET, "999999", atStep(step), PERIOD, DIGITS, 0);
        assertTrue(matched.isPresent());
        assertEquals(step, matched.getAsLong());
    }

    @Test
    void shouldStripSurroundingWhitespaceFromCode() {
        String code = Totp.generateCode(SECRET, 1L, DIGITS);

        assertTrue(Totp.verify(SECRET, "  " + code + "\t", at(30L), PERIOD, DIGITS, SKEW));
    }

    @Test
    void shouldRejectInternalWhitespaceInCode() {
        String code = Totp.generateCode(SECRET, 1L, DIGITS);
        String withInternalSpace = code.substring(0, 3) + " " + code.substring(3);

        assertFalse(Totp.verify(SECRET, withInternalSpace, at(30L), PERIOD, DIGITS, SKEW));
    }

    @Test
    void shouldRejectCodeWithWrongLengthForConfiguredDigits() {
        String sixDigitCode = Totp.generateCode(SECRET, 1L, 6);

        assertFalse(Totp.verify(SECRET, sixDigitCode, at(30L), PERIOD, 8, SKEW));
        assertFalse(Totp.verify(SECRET, sixDigitCode + "00", at(30L), PERIOD, 6, SKEW));
    }

    @Test
    void shouldAcceptEightDigitPolicy() {
        String code = Totp.generateCode(SECRET, 1L, 8);

        assertEquals(8, code.length());
        assertTrue(Totp.verify(SECRET, code, at(30L), PERIOD, 8, SKEW));
    }

    @Test
    void shouldThrowForNullSecretOnVerification() {
        assertThrows(NullPointerException.class,
                () -> Totp.verifyWithCounter(null, "123456", at(59L), PERIOD, DIGITS, SKEW));
        assertThrows(NullPointerException.class,
                () -> Totp.verify(null, "123456", at(59L), PERIOD, DIGITS, SKEW));
    }

    @Test
    void shouldDecodePaddedAndLowercaseBase32() {
        byte[] expected = Totp.base32Decode("JBSWY3DP");

        assertArrayEquals(expected, Totp.base32Decode("JBSWY3DP="));
        assertArrayEquals(expected, Totp.base32Decode("  jbswy3dp  "));
    }

    @Test
    void shouldRejectWhitespaceInsideBase32Secret() {
        assertThrows(IllegalArgumentException.class, () -> Totp.base32Decode("JBSW Y3DP"));
    }

    @Test
    void shouldEncodeSingleByteWithRemainderBits() {
        String encoded = Totp.base32Encode(new byte[]{0x00});

        assertEquals("AA", encoded);
        assertArrayEquals(new byte[]{0x00}, Totp.base32Decode(encoded));
    }

    @Test
    void shouldEncodeSingleByteWithHighBits() {
        String encoded = Totp.base32Encode(new byte[]{(byte) 0xFF});

        assertArrayEquals(new byte[]{(byte) 0xFF}, Totp.base32Decode(encoded));
    }

    @Test
    void shouldPercentEncodeReservedCharactersInProvisioningUri() {
        String uri = Totp.provisioningUri(
                "Acme Bank", "alice+ops@example.com", "JBSWY3DPEHPK3PXP", 8, 60);

        assertEquals("otpauth://totp/Acme%20Bank:alice%2Bops%40example.com"
                + "?secret=JBSWY3DPEHPK3PXP&issuer=Acme%20Bank"
                + "&algorithm=SHA1&digits=8&period=60", uri);
    }

    @Test
    void shouldPercentEncodeNonAsciiIssuerAndUsernameAsUtf8() {
        // A bank-branded issuer (or a legacy username) outside ASCII must be
        // encoded byte-wise as UTF-8. Emitting the code point directly creates
        // invalid escapes like %15F, which authenticator apps reject.
        String uri = Totp.provisioningUri(
                "Bänk Şubesi", "şeyma", "JBSWY3DPEHPK3PXP", 6, 30);

        assertTrue(uri.startsWith(
                "otpauth://totp/B%C3%A4nk%20%C5%9Eubesi:%C5%9Feyma?"));
        assertTrue(uri.contains("&issuer=B%C3%A4nk%20%C5%9Eubesi&"));
        assertFalse(uri.contains("%15F"));
    }

    @Test
    void shouldAcceptDigitsAtBothBoundaries() {
        assertTrue(Totp.provisioningUri("bank", "alice", "SECRET", 6, 30)
                .contains("digits=6"));
        assertTrue(Totp.provisioningUri("bank", "alice", "SECRET", 8, 30)
                .contains("digits=8"));
    }

    @Test
    void shouldRejectDigitsOutsideSupportedRange() {
        assertThrows(IllegalArgumentException.class,
                () -> Totp.provisioningUri("bank", "alice", "SECRET", 5, 30));
        assertThrows(IllegalArgumentException.class,
                () -> Totp.provisioningUri("bank", "alice", "SECRET", 9, 30));
    }

    @Test
    void shouldRejectNonPositivePeriodInProvisioningUri() {
        assertThrows(IllegalArgumentException.class,
                () -> Totp.provisioningUri("bank", "alice", "SECRET", 6, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Totp.provisioningUri("bank", "alice", "SECRET", 6, -1));
    }

    @Test
    void shouldRejectNullProvisioningInputs() {
        assertThrows(NullPointerException.class,
                () -> Totp.provisioningUri(null, "alice", "SECRET"));
        assertThrows(NullPointerException.class,
                () -> Totp.provisioningUri("bank", null, "SECRET"));
        assertThrows(NullPointerException.class,
                () -> Totp.provisioningUri("bank", "alice", null));
    }

    @Test
    void shouldRoundTripSixDigitCodesIncludingLeadingZeroes() {
        boolean sawLeadingZero = false;
        for (long counter = 0; counter < 500 && !sawLeadingZero; counter++) {
            String code = Totp.generateCode(SECRET, counter, DIGITS);
            assertEquals(DIGITS, code.length());
            assertTrue(code.chars().allMatch(Character::isDigit));
            assertTrue(Totp.verify(SECRET, code, at(counter * PERIOD), PERIOD, DIGITS, 0));
            sawLeadingZero = code.charAt(0) == '0';
        }
        assertTrue(sawLeadingZero, "expected at least one leading-zero code in 500 steps");
    }
}
