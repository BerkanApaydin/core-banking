package com.bank.app.common.application.port.out;

import com.bank.app.common.application.port.out.OutboxPort.EventEntry;

/**
 * One outbox consumer.
 *
 * <p>{@code supports} receives the persisted event type: the fully qualified
 * event class name for new rows, or the legacy simple class name for rows
 * written before that change. Use
 * {@link com.bank.app.common.application.outbox.OutboxEventTypes#matches(String, String)}
 * so both shapes resolve during a rolling deployment.
 */
public interface OutboxEventPort {
    boolean supports(String eventType);
    void handle(EventEntry event);
}
