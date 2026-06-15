package com.bank.app.transfer.domain;

import com.bank.app.account.domain.Account;
import com.bank.app.common.domain.Money;
import com.bank.app.common.exception.SameAccountTransferException;
import com.bank.app.common.exception.CurrencyMismatchException;
import java.util.Objects;

public class TransferDomainService {

    public Transfer execute(Account sender, Account receiver, Money amount) {
        Objects.requireNonNull(sender, "Gönderici hesap null olamaz");
        Objects.requireNonNull(receiver, "Alıcı hesap null olamaz");
        Objects.requireNonNull(amount, "Transfer tutarı null olamaz");

        Transfer transfer = execute(
                sender.getId(),
                sender.getIban().value(),
                sender.getBalance().currency(),
                receiver.getId(),
                receiver.getIban().value(),
                receiver.getBalance().currency(),
                amount
        );

        sender.debit(amount);
        receiver.credit(amount);

        return transfer;
    }

    public Transfer execute(Long senderId, String senderIban, Money.Currency senderCurrency,
                            Long receiverId, String receiverIban, Money.Currency receiverCurrency,
                            Money amount) {
        Objects.requireNonNull(amount, "Transfer tutarı null olamaz");
        Objects.requireNonNull(senderIban, "Gönderici IBAN null olamaz");
        Objects.requireNonNull(receiverIban, "Alıcı IBAN null olamaz");
        Objects.requireNonNull(senderCurrency, "Gönderici para birimi null olamaz");
        Objects.requireNonNull(receiverCurrency, "Alıcı para birimi null olamaz");

        if (senderIban.equalsIgnoreCase(receiverIban) || Objects.equals(senderId, receiverId)) {
            throw new SameAccountTransferException(
                "Aynı hesaba transfer yapılamaz: " + senderIban);
        }

        if (senderCurrency != amount.currency()) {
            throw new CurrencyMismatchException(
                "Gönderici hesap para birimi (" + senderCurrency + 
                ") ile transfer tutarı para birimi (" + amount.currency() + ") eşleşmiyor."
            );
        }

        if (receiverCurrency != amount.currency()) {
            throw new CurrencyMismatchException(
                "Alıcı hesap para birimi (" + receiverCurrency + 
                ") ile transfer tutarı para birimi (" + amount.currency() + ") eşleşmiyor."
            );
        }

        return Transfer.create(senderId, receiverId, amount);
    }
}
