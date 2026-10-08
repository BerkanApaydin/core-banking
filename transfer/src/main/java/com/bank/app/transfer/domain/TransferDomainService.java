package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.exception.SameAccountTransferException;
import com.bank.app.transfer.domain.exception.TransferCurrencyMismatchException;
import java.time.Clock;
import java.util.Objects;

public final class TransferDomainService {

    public Transfer validateAndCreateTransfer(TransferParticipants participants, Money amount, Clock clock) {
        Objects.requireNonNull(participants, "Transfer participants must not be null");
        Objects.requireNonNull(amount, "Transfer amount must not be null");
        // D-1: domain-level transfer ceiling (single source BalanceLimits).
        // Web @DecimalMax is the outer layer; this is the inner guarantee so a
        // programmatic caller can never bypass the limit.
        Money capped = Money.ofTransferAmount(amount.amount(), amount.currency());

        // MOD 97-10 checksum is mandatory on transfer input, not just format:
        // Iban itself stays format-lenient so legacy format-only rows remain
        // readable (see docs/iban-checksum-rollout.md); the boundary that
        // creates spendable transfers enforces the checksum explicitly, same
        // as account creation does (CreateAccountUseCaseImpl.selectIban).
        Iban.checked(participants.senderIban());
        Iban.checked(participants.receiverIban());

        if (participants.senderIban().equalsIgnoreCase(participants.receiverIban())
                || Objects.equals(participants.senderId(), participants.receiverId())) {
            throw new SameAccountTransferException(participants.senderIban());
        }

        if (participants.senderCurrency() != amount.currency()) {
            throw new TransferCurrencyMismatchException(
                    "Sender account currency (" + participants.senderCurrency() +
                            ") does not match transfer amount currency (" + capped.currency() + ").");
        }

        if (participants.receiverCurrency() != amount.currency()) {
            throw new TransferCurrencyMismatchException(
                    "Receiver account currency (" + participants.receiverCurrency() +
                            ") does not match transfer amount currency (" + capped.currency() + ").");
        }

        return Transfer.create(participants.senderId(), participants.receiverId(), capped, clock);
    }
}
