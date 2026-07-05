package com.bank.app.account.application.usecase;

import com.bank.app.account.application.port.in.ExecuteTransferUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.domain.Account;
import com.bank.app.common.application.port.in.TransactionalUseCase;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.OrderedPair;
import com.bank.app.common.domain.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;

@TransactionalUseCase
public class ExecuteTransferUseCaseImpl implements ExecuteTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExecuteTransferUseCaseImpl.class);
    private final LoadAccountPort loadAccountPort;
    private final SaveAccountPort saveAccountPort;
    private final AccountAuthorizationService accountAuthorizationService;
    private final DomainEventPublisherService domainEventPublisherService;
    private final AuditEventPort auditEventPort;

    public ExecuteTransferUseCaseImpl(LoadAccountPort loadAccountPort, SaveAccountPort saveAccountPort,
            AccountAuthorizationService accountAuthorizationService, DomainEventPublisherService domainEventPublisherService,
            AuditEventPort auditEventPort) {
        this.loadAccountPort = loadAccountPort;
        this.saveAccountPort = saveAccountPort;
        this.accountAuthorizationService = accountAuthorizationService;
        this.domainEventPublisherService = domainEventPublisherService;
        this.auditEventPort = auditEventPort;
    }

    @Override
    public void execute(Long senderId, Long receiverId, Money amount) {
        OrderedPair<Account> pair = TransferAccountHelper.loadOrderedPair(senderId, receiverId, loadAccountPort);
        Account sender = TransferAccountHelper.resolveSender(pair, senderId, receiverId);
        Account receiver = TransferAccountHelper.resolveReceiver(pair, senderId, receiverId);

        accountAuthorizationService.authorizeAccountOwner(sender,
                "You are not authorized to transfer from this account.");
        sender.debit(amount);
        receiver.credit(amount);

        TransferAccountHelper.saveAndPublishEvents(sender, receiver, saveAccountPort, domainEventPublisherService);
        auditEventPort.publish(new AuditEvent("TRANSFER_EXECUTED",
            String.format("Transfer completed. Sender: %d, Receiver: %d",
                senderId, receiverId),
            LocalDateTime.now()));

        log.info("Transfer executed: senderId={}, receiverId={}",
            senderId, receiverId);
    }
}
