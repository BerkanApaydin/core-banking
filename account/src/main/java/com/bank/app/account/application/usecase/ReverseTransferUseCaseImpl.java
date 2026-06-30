package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.in.ReverseTransferUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.domain.Account;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.OrderedPair;
import com.bank.app.common.domain.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

@TransactionalUseCase
public class ReverseTransferUseCaseImpl implements ReverseTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReverseTransferUseCaseImpl.class);

    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final AccountAuthorizationService accountAuthorizationService;
    private final EventPublisherPort eventPublisherPort;

    public ReverseTransferUseCaseImpl(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort,
                                      AccountAuthorizationService accountAuthorizationService, EventPublisherPort eventPublisherPort) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.accountAuthorizationService = accountAuthorizationService;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Override
    public void execute(Long senderId, Long receiverId, Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");

        OrderedPair<Account> pair = TransferAccountHelper.loadOrderedPair(senderId, receiverId, loadAccountPort);
        Account sender = TransferAccountHelper.resolveSender(pair, senderId, receiverId);
        Account receiver = TransferAccountHelper.resolveReceiver(pair, senderId, receiverId);

        accountAuthorizationService.authorizeAccountOwner(sender, "You are not authorized for this operation.");

        sender.credit(amount);
        receiver.debit(amount);

        TransferAccountHelper.saveAndPublishEvents(sender, receiver, saveAccountPort, eventPublisherPort);
        eventPublisherPort.publish(new AuditEvent("TRANSFER_CANCELLED",
            String.format("Transfer reversed. Sender: %d, Receiver: %d, Amount: %s %s",
                senderId, receiverId, amount.amount(), amount.currency()),
            java.time.LocalDateTime.now()));

        log.info("Transfer reversed: senderId={}, receiverId={}, amount={}",
            senderId, receiverId, amount);
    }
}
