package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.in.AdjustAccountBalancesUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.application.port.out.SaveLedgerPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.LedgerEntry;
import com.bank.app.account.domain.MovementDirection;
import com.bank.app.account.domain.TransactionRef;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.account.domain.exception.DistinctAccountsRequiredException;
import com.bank.app.accountapi.AccountAdjustmentResult;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.OrderedPair;
import com.bank.app.common.domain.event.AuditEvent;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

@TransactionalUseCase
public class AdjustAccountBalancesUseCaseImpl implements AdjustAccountBalancesUseCase {

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final ClockProviderPort clockProvider;
    private final DomainEventPublisherService domainEventPublisherService;
    private final AuditEventPort auditEventPort;
    private final SaveLedgerPort ledgerPort;

    public AdjustAccountBalancesUseCaseImpl(LoadAccountPort loadAccountPort,
            SaveAccountPort saveAccountPort,
            ClockProviderPort clockProvider,
            DomainEventPublisherService domainEventPublisherService,
            AuditEventPort auditEventPort,
            SaveLedgerPort ledgerPort) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.clockProvider = clockProvider;
        this.domainEventPublisherService = domainEventPublisherService;
        this.auditEventPort = auditEventPort;
        this.ledgerPort = ledgerPort;
    }

    @Override
    public AccountAdjustmentResult debitAndCredit(Long senderId, Long receiverId, Money amount) {
        return move(senderId, receiverId, amount, MovementDirection.OUTFLOW);
    }

    @Override
    public AccountAdjustmentResult reverseForCancellation(Long senderId, Long receiverId, Money amount) {
        return move(senderId, receiverId, amount, MovementDirection.REVERSAL);
    }

    private AccountAdjustmentResult move(Long senderId, Long receiverId, Money amount, MovementDirection direction) {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(direction, "Direction must not be null");
        requireDistinctAccounts(senderId, receiverId);
        OrderedPair<Account> pair = loadOrderedPair(senderId, receiverId);
        Account sender = resolveSender(pair, senderId, receiverId);
        Account receiver = resolveReceiver(pair, senderId, receiverId);
        Clock clock = clockProvider.clock();
        applyMovement(sender, receiver, amount, direction, clock);
        // 7.2: keep the saved aggregates — they carry the bumped @Version.
        Account savedSender = saveAccountPort.save(sender);
        Account savedReceiver = saveAccountPort.save(receiver);
        writeLedgerEntries(senderId, receiverId, amount, direction, clock, savedSender, savedReceiver);
        publishAndAudit(sender, receiver, savedSender, savedReceiver, amount, direction);
        return new AccountAdjustmentResult(senderId, receiverId,
                savedSender.getBalance(), savedReceiver.getBalance());
    }

    private static void applyMovement(Account sender, Account receiver, Money amount,
            MovementDirection direction, Clock clock) {
        if (direction.isOutflow()) {
            sender.debit(amount, clock);
            receiver.credit(amount, clock);
        } else {
            sender.credit(amount, clock);
            receiver.debit(amount, clock);
        }
    }

    private void writeLedgerEntries(Long senderId, Long receiverId, Money amount,
            MovementDirection direction, Clock clock, Account savedSender, Account savedReceiver) {
        // Double-entry journal: both legs share one ref and join this
        // transaction, so they net to zero or the money rolls back with them.
        TransactionRef operationRef = LedgerEntry.newTransactionRef();
        if (direction.isOutflow()) {
            ledgerPort.save(LedgerEntry.debit(senderId, amount, savedSender.getBalance(),
                    operationRef, clock));
            ledgerPort.save(LedgerEntry.credit(receiverId, amount, savedReceiver.getBalance(),
                    operationRef, clock));
        } else {
            ledgerPort.save(LedgerEntry.credit(senderId, amount, savedSender.getBalance(),
                    operationRef, clock));
            ledgerPort.save(LedgerEntry.debit(receiverId, amount, savedReceiver.getBalance(),
                    operationRef, clock));
        }
    }

    private void publishAndAudit(Account sender, Account receiver,
            Account savedSender, Account savedReceiver, Money amount, MovementDirection direction) {
        domainEventPublisherService.publishEvents(sender);
        domainEventPublisherService.publishEvents(receiver);
        if (direction.isOutflow()) {
            auditMovement(true, savedSender, amount);
            auditMovement(false, savedReceiver, amount);
        } else {
            auditMovement(false, savedSender, amount);
            auditMovement(true, savedReceiver, amount);
        }
    }

    /**
     * One audit row per balance leg, in the same transaction as the mutation:
     * a failed audit write rolls the money movement back. Legs are attributed
     * to "system" (see {@link AuditEvent} default); the user-facing row that
     * carries the acting username is written by the calling transfer use case
     * ({@code TRANSFER_EXECUTED} / {@code TRANSFER_CANCELLED}).
     */
    // S6: the saved aggregate travels as one object instead of three
    // loosely-related primitives (id + balance + implicit pairing).
    private void auditMovement(boolean debited, Account account, Money amount) {
        String action = debited ? AuditEvent.ACCOUNT_DEBITED : AuditEvent.ACCOUNT_CREDITED;
        String verb = debited ? "Debited" : "Credited";
        auditEventPort.publish(new AuditEvent(action,
                String.format("%s %s account ID %d (balance after: %s)",
                        verb, amount, account.getId(), account.getBalance()),
                LocalDateTime.now(clockProvider.clock()),
                "system", account.getUserId().value()));
    }

    private static void requireDistinctAccounts(Long senderId, Long receiverId) {
        if (Objects.equals(senderId, receiverId)) {
            throw new DistinctAccountsRequiredException(senderId);
        }
    }

    private OrderedPair<Account> loadOrderedPair(Long id1, Long id2) {
        Objects.requireNonNull(id1, "id1 must not be null");
        Objects.requireNonNull(id2, "id2 must not be null");
        // Lock accounts in stable ID order so concurrent transfers between the same pair
        // always acquire pessimistic locks in the same sequence (deadlock prevention).
        // T-13: this ordered lock is the PRIMARY guard; the versioned bulk UPDATE
        // in AccountPersistenceAdapter.save is the cross-check (see
        // docs/decisions/account-locking.md — drop neither side alone).
        return OrderedPair.from(
                id1, () -> loadAccountPort.findByIdForUpdate(id1)
                        .orElseThrow(() -> new AccountNotFoundException(id1)),
                id2, () -> loadAccountPort.findByIdForUpdate(id2)
                        .orElseThrow(() -> new AccountNotFoundException(id2)));
    }

    private static Account resolveSender(OrderedPair<Account> pair, Long senderId, Long receiverId) {
        return senderId < receiverId ? pair.lowerIdItem() : pair.higherIdItem();
    }

    private static Account resolveReceiver(OrderedPair<Account> pair, Long senderId, Long receiverId) {
        return senderId < receiverId ? pair.higherIdItem() : pair.lowerIdItem();
    }

}
