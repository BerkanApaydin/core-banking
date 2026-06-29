package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.exception.SameAccountTransferException;
import com.bank.app.transfer.domain.exception.TransferCurrencyMismatchException;
import java.util.Objects;

public final class TransferDomainService {

    public Transfer validateAndCreateTransfer(TransferParticipants participants, Money amount) {
        Objects.requireNonNull(participants, "Transfer participants must not be null");
        Objects.requireNonNull(amount, "Transfer amount must not be null");

        if (participants.senderIban().equalsIgnoreCase(participants.receiverIban())
                || Objects.equals(participants.senderId(), participants.receiverId())) {
            throw new SameAccountTransferException(participants.senderIban());
        }

        if (participants.senderCurrency() != amount.currency()) {
            throw new TransferCurrencyMismatchException(
                    "Sender account currency (" + participants.senderCurrency() +
                            ") does not match transfer amount currency (" + amount.currency() + ").");
        }

        if (participants.receiverCurrency() != amount.currency()) {
            throw new TransferCurrencyMismatchException(
                    "Receiver account currency (" + participants.receiverCurrency() +
                            ") does not match transfer amount currency (" + amount.currency() + ").");
        }

        return Transfer.create(participants.senderId(), participants.receiverId(), amount);
    }
}
