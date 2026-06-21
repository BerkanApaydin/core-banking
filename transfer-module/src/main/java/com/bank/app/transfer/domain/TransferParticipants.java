package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import java.util.Objects;

public record TransferParticipants(
        Long senderId,
        String senderIban,
        Currency senderCurrency,
        Long receiverId,
        String receiverIban,
        Currency receiverCurrency) {
    public TransferParticipants {
        Objects.requireNonNull(senderId, "Gönderici ID null olamaz");
        Objects.requireNonNull(senderIban, "Gönderici IBAN null olamaz");
        Objects.requireNonNull(senderCurrency, "Gönderici para birimi null olamaz");
        Objects.requireNonNull(receiverId, "Alıcı ID null olamaz");
        Objects.requireNonNull(receiverIban, "Alıcı IBAN null olamaz");
        Objects.requireNonNull(receiverCurrency, "Alıcı para birimi null olamaz");
    }
}
