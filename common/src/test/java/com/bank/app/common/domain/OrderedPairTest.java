package com.bank.app.common.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void shouldInvokeTheLowerIdSupplierFirst() {
        // The whole point of this type: the supplier bound to the lower id
        // must run first so every transaction acquires the pessimistic locks
        // in the same sequence (deadlock prevention).
        List<String> invocations = new ArrayList<>();

        OrderedPair.from(1L, () -> {
            invocations.add("supplier1");
            return "first";
        }, 2L, () -> {
            invocations.add("supplier2");
            return "second";
        });

        assertThat(invocations).containsExactly("supplier1", "supplier2");
    }

    @Test
    void shouldInvokeTheSecondSupplierFirstWhenItsIdIsLower() {
        List<String> invocations = new ArrayList<>();

        OrderedPair.from(2L, () -> {
            invocations.add("supplier1");
            return "second";
        }, 1L, () -> {
            invocations.add("supplier2");
            return "first";
        });

        assertThat(invocations).containsExactly("supplier2", "supplier1");
    }

    @Test
    void shouldBreakTheIdTieDeterministically() {
        // Equal ids are a self-transfer; the tie-break must still be stable,
        // otherwise two transactions on the same pair could lock in opposite
        // orders.
        List<String> invocations = new ArrayList<>();

        OrderedPair.from(1L, () -> {
            invocations.add("supplier1");
            return "first";
        }, 1L, () -> {
            invocations.add("supplier2");
            return "second";
        });

        assertThat(invocations).containsExactly("supplier2", "supplier1");
    }

    @Test
    void shouldRejectNullIdsAndSuppliers() {
        assertThatThrownBy(() -> OrderedPair.from(null, () -> "first", 2L, () -> "second"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("id1 must not be null");
        assertThatThrownBy(() -> OrderedPair.from(1L, () -> "first", null, () -> "second"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("id2 must not be null");
        assertThatThrownBy(() -> OrderedPair.from(1L, null, 2L, () -> "second"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("supplier1 must not be null");
        assertThatThrownBy(() -> OrderedPair.from(1L, () -> "first", 2L, null))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("supplier2 must not be null");
    }

    @Test
    void shouldRejectANullResultFromTheLowerIdSupplier() {
        assertThatThrownBy(() -> OrderedPair.from(1L, () -> null, 2L, () -> "second"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("supplier1 must not return null");
    }

    @Test
    void shouldRejectANullResultFromTheHigherIdSupplier() {
        assertThatThrownBy(() -> OrderedPair.from(1L, () -> "first", 2L, () -> null))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("supplier2 must not return null");
    }

    @Test
    void shouldRejectANullResultWithTheGenericMessageWhenTheSecondSupplierRunsFirst() {
        // The reversed branch reports a supplier-agnostic message, so a null
        // result is not attributed to the wrong supplier.
        assertThatThrownBy(() -> OrderedPair.from(2L, () -> "second", 1L, () -> null))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("supplier must not return null");
        assertThatThrownBy(() -> OrderedPair.from(2L, () -> null, 1L, () -> "first"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("supplier must not return null");
    }
}
