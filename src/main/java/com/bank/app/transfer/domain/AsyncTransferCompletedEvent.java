package com.bank.app.transfer.domain;

/**
 * Outbox poller tarafından asenkron yayınlanan domain event — framework bağımsız POJO.
 */
public record AsyncTransferCompletedEvent(Transfer transfer) {
}
