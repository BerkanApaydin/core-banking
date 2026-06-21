package com.bank.app.transfer.infrastructure.adapter;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Component
@Primary
public class EmailNotificationAdapter implements SendNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationAdapter.class);

    @Override
    @CircuitBreaker(name = "emailNotification", fallbackMethod = "fallbackNotify")
    public void notifyTransferCompleted(AsyncTransferCompletedEvent event) {
        if (event == null) {
            log.warn("Email bildirimi için null event alındı, atlanıyor.");
            return;
        }
        log.info("Email Bildirimi: {} ID'li transfer başarıyla tamamlandı. Tutar: {} {}", 
                event.transferId(), event.amount().amount(), event.amount().currency());
    }

    public void fallbackNotify(AsyncTransferCompletedEvent event, Throwable throwable) {
        if (event == null) {
            log.warn("Email fallback için null event alındı, atlanıyor.");
            return;
        }
        log.error("Email bildirimi gönderimi başarısız oldu. Hata: {}. Bildirim kuyruğa alındı (Fallback). Transfer ID: {}", 
                throwable != null ? throwable.getMessage() : "null", event.transferId());
    }
}
