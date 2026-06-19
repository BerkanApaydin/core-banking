package com.bank.app.transfer.infrastructure.adapter;

import com.bank.app.transfer.application.port.SendNotificationPort;
import com.bank.app.transfer.domain.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Component
public class SmsNotificationAdapter implements SendNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationAdapter.class);

    @Override
    @CircuitBreaker(name = "smsNotification", fallbackMethod = "fallbackNotify")
    public void notifyTransferCompleted(Transfer transfer) {
        if (transfer == null) {
            log.warn("SMS bildirimi için null transfer alındı, atlanıyor.");
            return;
        }
        log.info("SMS Bildirimi: {} ID'li transfer başarıyla tamamlandı. Tutar: {} {}", 
                transfer.getId(), transfer.getAmount().amount(), transfer.getAmount().currency());
    }

    public void fallbackNotify(Transfer transfer, Throwable throwable) {
        if (transfer == null) {
            log.warn("SMS fallback için null transfer alındı, atlanıyor.");
            return;
        }
        log.error("SMS bildirimi gönderimi başarısız oldu. Hata: {}. Bildirim kuyruğa alındı (Fallback). Transfer ID: {}", 
                throwable != null ? throwable.getMessage() : "null", transfer.getId());
    }
}
