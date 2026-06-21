package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.exception.SameAccountTransferException;
import com.bank.app.common.exception.CurrencyMismatchException;
import java.util.Objects;

public class TransferDomainService {

    public Transfer validateAndCreateTransfer(TransferParticipants participants, Money amount) {
        Objects.requireNonNull(participants, "Transfer katılımcıları null olamaz");
        Objects.requireNonNull(amount, "Transfer tutarı null olamaz");

        if (participants.senderIban().equalsIgnoreCase(participants.receiverIban())
                || Objects.equals(participants.senderId(), participants.receiverId())) {
            throw new SameAccountTransferException(participants.senderIban());
        }

        if (participants.senderCurrency() != amount.currency()) {
            throw new CurrencyMismatchException(
                    "Gönderici hesap para birimi (" + participants.senderCurrency() +
                            ") ile transfer tutarı para birimi (" + amount.currency() + ") eşleşmiyor.");
        }

        if (participants.receiverCurrency() != amount.currency()) {
            throw new CurrencyMismatchException(
                    "Alıcı hesap para birimi (" + participants.receiverCurrency() +
                            ") ile transfer tutarı para birimi (" + amount.currency() + ") eşleşmiyor.");
        }

        return Transfer.create(participants.senderId(), participants.receiverId(), amount);
    }
}
