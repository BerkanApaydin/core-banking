package com.bank.app.transfer.infrastructure.notification;

import com.bank.app.transfer.application.port.SendNotificationPort;
import com.bank.app.transfer.domain.Transfer;
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
    public void notifyTransferCompleted(Transfer transfer) {
        if (transfer == null) {
            log.warn("Email bildirimi için null transfer alındı, atlanıyor.");
            return;
        }
        log.info("Email Bildirimi: {} ID'li transfer başarıyla tamamlandı. Tutar: {} {}", 
                transfer.getId(), transfer.getAmount().amount(), transfer.getAmount().currency());
    }

    public void fallbackNotify(Transfer transfer, Throwable throwable) {
        if (transfer == null) {
            log.warn("Email fallback için null transfer alındı, atlanıyor.");
            return;
        }
        log.error("Email bildirimi gönderimi başarısız oldu. Hata: {}. Bildirim kuyruğa alındı (Fallback). Transfer ID: {}", 
                throwable != null ? throwable.getMessage() : "null", transfer.getId());
    }
}
