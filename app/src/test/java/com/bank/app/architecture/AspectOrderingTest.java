package com.bank.app.architecture;

import com.bank.app.common.application.aspect.UseCaseAspectOrders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins use-case aspect precedence. The retry aspect probes
 * {@code isActualTransactionActive()} to avoid reusing a rollback-only
 * transaction — silently reordering the aspects would disable retries.
 */
class AspectOrderingTest {

    @Test
    @DisplayName("transfer retry wraps the programmatic transaction boundary")
    void retryWrapsTransaction() {
        assertThat(UseCaseAspectOrders.TRANSFER_RETRY)
                .isLessThan(UseCaseAspectOrders.USE_CASE_TRANSACTION);
    }

    @Test
    @DisplayName("aspects use the shared order constants")
    void aspectsUseSharedConstants() throws Exception {
        Order retryOrder = AnnotationUtils.findAnnotation(
                Class.forName("com.bank.app.transfer.adapter.in.config.TransferUseCaseRetryAspect"),
                Order.class);
        Order txOrder = AnnotationUtils.findAnnotation(
                Class.forName(
                        "com.bank.app.infrastructure.adapter.in.aspect.UseCaseTransactionAspect"),
                Order.class);
        assertThat(retryOrder).isNotNull();
        assertThat(txOrder).isNotNull();
        assertThat(retryOrder.value()).isEqualTo(UseCaseAspectOrders.TRANSFER_RETRY);
        assertThat(txOrder.value()).isEqualTo(UseCaseAspectOrders.USE_CASE_TRANSACTION);
        assertThat(retryOrder.value()).isLessThan(txOrder.value());
        assertThat(txOrder.value()).isLessThan(Ordered.LOWEST_PRECEDENCE);
    }
}
