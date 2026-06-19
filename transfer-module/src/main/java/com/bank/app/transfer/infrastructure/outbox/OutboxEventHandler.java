package com.bank.app.transfer.infrastructure.outbox;

/**
 * Outbox event tipi işleme stratejisi — yeni event tipleri OCP ile eklenebilir.
 */
public interface OutboxEventHandler {

    boolean supports(String eventType);

    void handle(OutboxEventJpaEntity event) throws Exception;
}
