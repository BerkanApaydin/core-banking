package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.config.MfaProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Dev/test backend of the account-keyed MFA budget. The important behaviours
 * are the exact threshold semantics (failures are counted before the next
 * check, so N failures block the N+1 attempt), per-account isolation and the
 * null no-ops that keep a missing id from turning into an NPE.
 */
@DisplayName("CaffeineMfaAttemptAdapter")
class CaffeineMfaAttemptAdapterTest {

    private static final Long USER_A = 100L;
    private static final Long USER_B = 200L;

    private static MfaProperties props(int maxAttempts, int windowMinutes) {
        return new MfaProperties("issuer", "", "", 30, 6, 1, 15,
                maxAttempts, windowMinutes, 10000);
    }

    @Test
    @DisplayName("is disabled by a negative threshold without recording state")
    void shouldNotBlockWhenDisabled() {
        CaffeineMfaAttemptAdapter adapter = new CaffeineMfaAttemptAdapter(props(-1, 15));

        adapter.recordFailure(USER_A);
        adapter.recordFailure(USER_A);

        assertThat(adapter.isBlocked(USER_A)).isFalse();
    }

    @Test
    @DisplayName("does not block before the threshold, blocks exactly at it")
    void shouldBlockAtThreshold() {
        CaffeineMfaAttemptAdapter adapter = new CaffeineMfaAttemptAdapter(props(3, 15));

        adapter.recordFailure(USER_A);
        adapter.recordFailure(USER_A);

        assertThat(adapter.isBlocked(USER_A)).isFalse();

        adapter.recordFailure(USER_A);

        assertThat(adapter.isBlocked(USER_A)).isTrue();
    }

    @Test
    @DisplayName("keeps budgets isolated per account")
    void shouldIsolateAccounts() {
        CaffeineMfaAttemptAdapter adapter = new CaffeineMfaAttemptAdapter(props(2, 15));

        adapter.recordFailure(USER_A);
        adapter.recordFailure(USER_A);

        assertThat(adapter.isBlocked(USER_A)).isTrue();
        assertThat(adapter.isBlocked(USER_B)).isFalse();
    }

    @Test
    @DisplayName("treats a zero threshold as immediate lockout after one failure")
    void shouldTreatZeroThresholdAsImmediateLockout() {
        CaffeineMfaAttemptAdapter adapter = new CaffeineMfaAttemptAdapter(props(0, 15));

        assertThat(adapter.isBlocked(USER_A)).isFalse();

        adapter.recordFailure(USER_A);

        assertThat(adapter.isBlocked(USER_A)).isTrue();
    }

    @Test
    @DisplayName("clears a blocked account on reset")
    void shouldResetBlockedAccount() {
        CaffeineMfaAttemptAdapter adapter = new CaffeineMfaAttemptAdapter(props(1, 15));
        adapter.recordFailure(USER_A);
        assertThat(adapter.isBlocked(USER_A)).isTrue();

        adapter.reset(USER_A);

        assertThat(adapter.isBlocked(USER_A)).isFalse();
    }

    @Test
    @DisplayName("increments only the target account")
    void shouldIncrementOnlyTargetAccount() {
        CaffeineMfaAttemptAdapter adapter = new CaffeineMfaAttemptAdapter(props(2, 15));

        adapter.recordFailure(USER_A);

        assertThat(adapter.isBlocked(USER_A)).isFalse();
        assertThat(adapter.isBlocked(USER_B)).isFalse();

        adapter.recordFailure(USER_A);

        assertThat(adapter.isBlocked(USER_A)).isTrue();
    }

    @Test
    @DisplayName("null user is a no-op on read, record and reset")
    void shouldIgnoreNullUser() {
        CaffeineMfaAttemptAdapter adapter = new CaffeineMfaAttemptAdapter(props(1, 15));

        assertThat(adapter.isBlocked(null)).isFalse();
        assertThatCode(() -> adapter.recordFailure(null)).doesNotThrowAnyException();
        assertThatCode(() -> adapter.reset(null)).doesNotThrowAnyException();
        assertThat(adapter.isBlocked(USER_A)).isFalse();
    }

    @Test
    @DisplayName("exposes the configured lockout window")
    void shouldReturnWindowMinutes() {
        CaffeineMfaAttemptAdapter adapter = new CaffeineMfaAttemptAdapter(props(5, 30));

        assertThat(adapter.getWindowMinutes()).isEqualTo(30);
    }
}
