package com.bank.app.transfer.infrastructure.outbox;

import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.account.infrastructure.persistence.AccountJpaRepository;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.Money;
import com.bank.app.common.adapter.SecurityContextAdapter;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.usecase.PlaceTransferUseCase;
import com.bank.app.transfer.infrastructure.persistence.TransferJpaRepository;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import com.bank.app.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Transactional
@SuppressWarnings("null")
class OutboxIntegrationTest extends AbstractSpringBootIntegrationTest {

    @Autowired
    private PlaceTransferUseCase placeTransferUseCase;

    @Autowired
    private OutboxEventJpaRepository outboxRepo;

    @Autowired
    private AccountJpaRepository accountRepo;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransferJpaRepository transferRepo;

    @Autowired
    private OutboxPoller outboxPoller;

    @MockitoBean
    private SecurityContextAdapter securityUtils;

    private UserJpaEntity user;

    @BeforeEach
    void setUp() {

        user = userRepository.save(new UserJpaEntity(null, "user1", "password", "ROLE_USER"));

        accountRepo.save(new AccountJpaEntity(null, user.getId(), "TR290006200000000000000111", "Sender",
                new BigDecimal("1000.00"), "TRY", true));
        accountRepo.save(new AccountJpaEntity(null, user.getId(), "TR290006200000000000000222", "Receiver",
                new BigDecimal("1000.00"), "TRY", true));

        when(securityUtils.getCurrentUserId()).thenReturn(Optional.of(user.getId()));
        when(securityUtils.getCurrentUsername()).thenReturn(Optional.of("user1"));
    }

    @Test
    void shouldCreateOutboxEventAndProcessItSuccessfully() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("50.00"),
                Money.Currency.TRY
        );

        // 1. Execute transfer
        placeTransferUseCase.execute(request);

        // 2. Assert outbox event was created in PENDING/unprocessed status
        List<OutboxEventJpaEntity> outboxEvents = outboxRepo.findAll();
        assertEquals(1, outboxEvents.size());
        OutboxEventJpaEntity event = outboxEvents.get(0);
        assertEquals("Transfer", event.getAggregateType());
        assertEquals("TransferCompletedEvent", event.getEventType());
        assertFalse(event.isProcessed());
        assertNull(event.getProcessedAt());

        // 3. Manually run the poller to process the event
        outboxPoller.pollAndProcessEvents();

        // 4. Assert outbox event is now marked as processed
        OutboxEventJpaEntity processedEvent = outboxRepo.findById(event.getId()).orElseThrow();
        assertTrue(processedEvent.isProcessed());
        assertNotNull(processedEvent.getProcessedAt());
    }
}
