package com.bank.app.common.outbox;

public interface OutboxEventHandler {

    boolean supports(String eventType);

    void handle(OutboxEventJpaEntity event) throws Exception;
}
