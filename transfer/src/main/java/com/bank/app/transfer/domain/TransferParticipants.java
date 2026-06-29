package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Currency;
import java.util.Objects;

public record TransferParticipants(
        Long senderId,
        String senderIban,
        Currency senderCurrency,
        Long receiverId,
        String receiverIban,
        Currency receiverCurrency) {
    public TransferParticipants {
        Objects.requireNonNull(senderId, "Sender ID must not be null");
        Objects.requireNonNull(senderIban, "Sender IBAN must not be null");
        Objects.requireNonNull(senderCurrency, "Sender currency must not be null");
        Objects.requireNonNull(receiverId, "Receiver ID must not be null");
        Objects.requireNonNull(receiverIban, "Receiver IBAN must not be null");
        Objects.requireNonNull(receiverCurrency, "Receiver currency must not be null");
    }
}
