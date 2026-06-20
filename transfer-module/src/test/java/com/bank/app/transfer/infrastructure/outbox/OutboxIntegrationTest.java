package com.bank.app.transfer.infrastructure.outbox;

import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.account.infrastructure.persistence.AccountJpaRepository;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.Money;
import com.bank.app.common.adapter.SecurityContextAdapter;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.port.in.PlaceTransferPort;
import com.bank.app.transfer.infrastructure.persistence.TransferJpaRepository;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import com.bank.app.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import org.springframework.boot.test.context.SpringBootTest;
import com.bank.app.transfer.ModuleIntegrationTestConfig;

@SpringBootTest(classes = {com.bank.app.transfer.TestApplication.class, ModuleIntegrationTestConfig.class})
@Transactional
@SuppressWarnings("null")
class OutboxIntegrationTest extends AbstractSpringBootIntegrationTest {

    @Autowired
    private PlaceTransferPort placeTransferPort;

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

    @Autowired
    private ObjectMapper objectMapper;

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

        placeTransferPort.execute(request);

        List<OutboxEventJpaEntity> outboxEvents = outboxRepo.findAll();
        assertEquals(1, outboxEvents.size());
        OutboxEventJpaEntity event = outboxEvents.get(0);
        assertEquals("Transfer", event.getAggregateType());
        assertEquals("TransferCompletedEvent", event.getEventType());
        assertFalse(event.isProcessed());
        assertNull(event.getProcessedAt());

        outboxPoller.pollAndProcessEvents();

        OutboxEventJpaEntity processedEvent = outboxRepo.findById(event.getId()).orElseThrow();
        assertTrue(processedEvent.isProcessed());
        assertNotNull(processedEvent.getProcessedAt());
    }

    @Test
    void shouldIncrementRetryCountOnFailedProcessing() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                UUID.randomUUID().toString(),
                "Transfer", "123", "TransferCompletedEvent",
                "invalid json", LocalDateTime.now(), false, null,
                0, false, null);
        outboxRepo.save(entity);

        outboxPoller.pollAndProcessEvents();

        OutboxEventJpaEntity failedEvent = outboxRepo.findById(entity.getId()).orElseThrow();
        assertEquals(1, failedEvent.getRetryCount());
        assertFalse(failedEvent.isProcessed());
        assertFalse(failedEvent.isDeadLetter());
        assertNotNull(failedEvent.getLastError());
    }

    @Test
    void shouldMoveToDeadLetterAfterMaxRetries() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                UUID.randomUUID().toString(),
                "Transfer", "123", "TransferCompletedEvent",
                "invalid json", LocalDateTime.now(), false, null,
                4, false, "previous error");
        outboxRepo.save(entity);

        outboxPoller.pollAndProcessEvents();

        OutboxEventJpaEntity deadEvent = outboxRepo.findById(entity.getId()).orElseThrow();
        assertEquals(5, deadEvent.getRetryCount());
        assertTrue(deadEvent.isDeadLetter());
        assertFalse(deadEvent.isProcessed());
        assertNotNull(deadEvent.getLastError());
    }

    @Test
    void shouldProcessMultipleEventsInOnePoll() throws Exception {
        for (int i = 0; i < 3; i++) {
            OutboxEventListener.TransferEventPayload payload =
                    new OutboxEventListener.TransferEventPayload(
                            100L + i, 1L, 2L, new BigDecimal("10.00"), "TRY");
            OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                    UUID.randomUUID().toString(),
                    "Transfer", String.valueOf(100L + i), "TransferCompletedEvent",
                    objectMapper.writeValueAsString(payload),
                    LocalDateTime.now(), false, null);
            outboxRepo.save(entity);
        }

        assertEquals(3, outboxRepo.findAll().stream().filter(e -> !e.isProcessed()).count());

        outboxPoller.pollAndProcessEvents();

        List<OutboxEventJpaEntity> processed = outboxRepo.findAll().stream()
                .filter(OutboxEventJpaEntity::isProcessed)
                .toList();
        assertEquals(3, processed.size());
        for (OutboxEventJpaEntity event : processed) {
            assertNull(event.getLastError());
            assertFalse(event.isDeadLetter());
            assertNotNull(event.getProcessedAt());
        }
    }
}
