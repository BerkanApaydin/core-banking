package com.bank.app.common.application.port.out;

import com.bank.app.common.domain.event.AuditEvent;

public interface AuditEventPort {
    void publish(AuditEvent event);
}
