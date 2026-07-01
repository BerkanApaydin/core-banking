package com.bank.app.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderedPairTest {

    @Test
    void shouldOrderWhenFirstIdIsSmaller() {
        OrderedPair<String> pair = OrderedPair.from(1L, () -> "first", 2L, () -> "second");
        assertThat(pair.lowerIdItem()).isEqualTo("first");
        assertThat(pair.higherIdItem()).isEqualTo("second");
    }

    @Test
    void shouldOrderWhenSecondIdIsSmaller() {
        OrderedPair<String> pair = OrderedPair.from(2L, () -> "second", 1L, () -> "first");
        assertThat(pair.lowerIdItem()).isEqualTo("first");
        assertThat(pair.higherIdItem()).isEqualTo("second");
    }

    @Test
    void shouldOrderWhenIdsAreEqual() {
        OrderedPair<String> pair = OrderedPair.from(1L, () -> "first", 1L, () -> "second");
        assertThat(pair.lowerIdItem()).isEqualTo("second");
        assertThat(pair.higherIdItem()).isEqualTo("first");
    }
}
