package com.bank.app.transfer.infrastructure.adapter;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Component
public class SmsNotificationAdapter implements SendNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationAdapter.class);

    @Override
    @CircuitBreaker(name = "smsNotification", fallbackMethod = "fallbackNotify")
    public void notifyTransferCompleted(AsyncTransferCompletedEvent event) {
        if (event == null) {
            log.warn("SMS bildirimi için null event alındı, atlanıyor.");
            return;
        }
        log.info("SMS Bildirimi: {} ID'li transfer başarıyla tamamlandı. Tutar: {} {}", 
                event.transferId(), event.amount().amount(), event.amount().currency());
    }

    public void fallbackNotify(AsyncTransferCompletedEvent event, Throwable throwable) {
        if (event == null) {
            log.warn("SMS fallback için null event alındı, atlanıyor.");
            return;
        }
        log.error("SMS bildirimi gönderimi başarısız oldu. Hata: {}. Bildirim kuyruğa alındı (Fallback). Transfer ID: {}", 
                throwable != null ? throwable.getMessage() : "null", event.transferId());
    }
}
