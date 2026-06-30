package com.bank.app.common.application.port.out;

import com.bank.app.common.application.port.out.OutboxPort.EventEntry;

public interface OutboxEventPort {
    boolean supports(String eventType);
    void handle(EventEntry event);
}
