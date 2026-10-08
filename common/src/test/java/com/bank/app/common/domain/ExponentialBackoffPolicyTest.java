package com.bank.app.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExponentialBackoffPolicyTest {

    @Test
    void shouldDoubleUntilMax() {
        var policy = new ExponentialBackoffPolicy(500, 2000, 0);
        assertThat(policy.nextDelay(500)).isEqualTo(1000);
        assertThat(policy.nextDelay(1000)).isEqualTo(2000);
        assertThat(policy.nextDelay(2000)).isEqualTo(2000);
    }

    @Test
    void shouldRejectBadBounds() {
        assertThatThrownBy(() -> new ExponentialBackoffPolicy(0, 100, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExponentialBackoffPolicy(500, 100, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowMaxEqualToInitial() {
        // Boundary: maxDelayMs < initialDelayMs rejects, == must be accepted.
        // Kills the ConditionalsBoundary mutant (<= vs <) on the constructor.
        var policy = new ExponentialBackoffPolicy(500, 500, 0);
        assertThat(policy.initialDelayMs()).isEqualTo(500);
        assertThat(policy.nextDelay(500)).isEqualTo(500);
    }

    @Test
    void shouldExposeInitialDelay() {
        assertThat(new ExponentialBackoffPolicy(500, 2000, 0).initialDelayMs()).isEqualTo(500);
        assertThat(new ExponentialBackoffPolicy(1, 1, 0).initialDelayMs()).isEqualTo(1);
    }

    @Test
    void shouldRejectNegativeJitter() {
        assertThatThrownBy(() -> new ExponentialBackoffPolicy(500, 2000, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldImplementValueEquality() {
        assertThat(new ExponentialBackoffPolicy(500, 2000, 100))
                .isEqualTo(new ExponentialBackoffPolicy(500, 2000, 100))
                .hasSameHashCodeAs(new ExponentialBackoffPolicy(500, 2000, 100));
        assertThat(new ExponentialBackoffPolicy(500, 2000, 100))
                .isNotEqualTo(new ExponentialBackoffPolicy(500, 2000, 0));
        var policy = new ExponentialBackoffPolicy(500, 2000, 100);
        assertThat(policy.equals(policy)).isTrue();
        assertThat(policy.equals(null)).isFalse();
        assertThat(policy.equals("not-a-policy")).isFalse();
        assertThat(new ExponentialBackoffPolicy(500, 2000, 100))
                .isNotEqualTo(new ExponentialBackoffPolicy(501, 2000, 100));
        assertThat(new ExponentialBackoffPolicy(500, 2000, 100))
                .isNotEqualTo(new ExponentialBackoffPolicy(500, 2001, 100));
    }

    @Test
    void shouldStayWithinJitterBounds() {
        var policy = new ExponentialBackoffPolicy(500, 2000, 100);
        boolean observedAboveDoubled = false;
        for (int i = 0; i < 200; i++) {
            long next = policy.nextDelay(500);
            // Lower bound kills the MATH mutant (doubled - jitter);
            // the strict upper bound kills overflow-style mutants.
            assertThat(next).isBetween(1000L, 1100L);
            if (next > 1000L) {
                observedAboveDoubled = true;
            }
        }
        // Kills the NEGATE_CONDITIONALS mutant on (jitterMs == 0):
        // a negated mutant always returns the bare doubled value (1000),
        // so at least one sample must observe actual jitter.
        assertThat(observedAboveDoubled)
                .as("jitter must be applied at least once in 200 samples")
                .isTrue();
    }

    @Test
    void shouldReturnBareDoubledWhenJitterIsZero() {
        var policy = new ExponentialBackoffPolicy(500, 2000, 0);
        // Exact equality (not a range) kills mutants that add jitter unconditionally.
        assertThat(policy.nextDelay(500)).isEqualTo(1000);
        assertThat(policy.nextDelay(0)).isEqualTo(0);
    }

    @Test
    void shouldCapAtMaxBeforeJitter() {
        var policy = new ExponentialBackoffPolicy(500, 1200, 10);
        for (int i = 0; i < 50; i++) {
            assertThat(policy.nextDelay(1000)).isBetween(1200L, 1210L);
        }
    }

    @Test
    void shouldApplyJitterAdditionExactly() {
        // Deterministic kill for both MATH mutants on nextDelay:
        // (doubled + jitterSample) vs (doubled - jitterSample) and
        // (jitterMs + 1) vs (jitterMs - 1). With jitter=1 the sample space
        // is {0,1}: 500 trials observe both extremes with probability
        // 1 - 2*(1/2)^500, while any minus-mutant can only produce {500,499}.
        var policy = new ExponentialBackoffPolicy(500, 2000, 1);
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (int i = 0; i < 500; i++) {
            long next = policy.nextDelay(500);
            assertThat(next).isBetween(1000L, 1001L);
            min = Math.min(min, next);
            max = Math.max(max, next);
        }
        assertThat(min).isEqualTo(1000L);
        assertThat(max).isEqualTo(1001L);
    }
}
