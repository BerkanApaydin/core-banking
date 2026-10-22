package com.bank.app.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MovementDirection")
class MovementDirectionTest {

    @Test
    @DisplayName("OUTFLOW must report itself as an outflow")
    void shouldReportOutflowAsOutflow() {
        assertThat(MovementDirection.OUTFLOW.isOutflow()).isTrue();
    }

    @Test
    @DisplayName("REVERSAL must not report itself as an outflow")
    void shouldReportReversalAsNotOutflow() {
        assertThat(MovementDirection.REVERSAL.isOutflow()).isFalse();
    }

    @Test
    @DisplayName("exactly one direction may be an outflow")
    void shouldHaveExactlyOneOutflowDirection() {
        // A swapped branch here moves money the wrong way: the sender would be
        // credited instead of debited on a normal placement.
        assertThat(MovementDirection.values())
                .filteredOn(MovementDirection::isOutflow)
                .containsExactly(MovementDirection.OUTFLOW);
    }
}
