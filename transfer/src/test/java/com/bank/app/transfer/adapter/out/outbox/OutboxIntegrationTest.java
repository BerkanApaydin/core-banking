package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.account.adapter.out.persistence.AccountJpaEntity;
import com.bank.app.account.adapter.out.persistence.AccountJpaRepository;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.common.domain.Currency;
import com.bank.app.infrastructure.adapter.in.outbox.OutboxProcessor;
import com.bank.app.infrastructure.adapter.out.persistence.OutboxJpaEntity;
import com.bank.app.infrastructure.adapter.out.persistence.OutboxJpaRepository;
import com.bank.app.infrastructure.adapter.out.security.SecurityContextAdapter;
import com.bank.app.transfer.ModuleIntegrationTestConfig;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { com.bank.app.transfer.TestApplication.class, ModuleIntegrationTestConfig.class,
        OutboxIntegrationTest.TestConfig.class })
@SuppressWarnings("null")
class OutboxIntegrationTest extends AbstractSpringBootIntegrationTest {

    @Autowired
    private PlaceTransferUseCase placeTransferPort;

    @Autowired
    private OutboxJpaRepository outboxRepo;

    @Autowired
    private AccountJpaRepository accountRepo;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxProcessor outboxProcessor;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private SecurityContextAdapter securityUtils;

    private UserJpaEntity user;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        EventPublisherPort domainEventOutboxAdapter(ObjectMapper objectMapper,
                OutboxJpaRepository outboxRepo) {
            return event -> {
                try {
                    String payload = objectMapper.writeValueAsString(event);
                    String eventType = event.getClass().getSimpleName();
                    OutboxJpaEntity entity = new OutboxJpaEntity(
                            UUID.randomUUID().toString(),
                            "Transfer", "unknown", eventType,
                            payload, LocalDateTime.now(), false, 0, false, null, 0);
                    outboxRepo.save(entity);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to serialize event", e);
                }
            };
        }
    }

    private static EventEntry toEventEntry(OutboxJpaEntity e) {
        return new EventEntry(e.getId(), e.getAggregateType(), e.getAggregateId(),
                e.getEventType(), e.getPayload(), e.getRetryCount(),
                e.isProcessed(), e.isDeadLetter(), e.getLastError(),
                e.getPartition(), e.getCreatedAt(), e.getProcessedAt());
    }

    @BeforeEach
    void setUp() {
        user = userRepository.save(new UserJpaEntity(null, "user1", "password", "ROLE_USER", null, null, null));

        accountRepo.save(new AccountJpaEntity(null, user.getId(), "TR290006200000000000000111", "Sender",
                new BigDecimal("1000.00"), "TRY", "ACTIVE", null));
        accountRepo.save(new AccountJpaEntity(null, user.getId(), "TR290006200000000000000222", "Receiver",
                new BigDecimal("1000.00"), "TRY", "ACTIVE", null));

        when(securityUtils.getCurrentUserId()).thenReturn(Optional.of(user.getId()));
        when(securityUtils.getCurrentUsername()).thenReturn(Optional.of("user1"));
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(txManager).execute(status -> {
            entityManager.createQuery("delete from OutboxJpaEntity").executeUpdate();
            entityManager.createQuery("delete from TransferJpaEntity").executeUpdate();
            entityManager.createQuery("delete from AccountJpaEntity").executeUpdate();
            entityManager.createQuery("delete from UserJpaEntity").executeUpdate();
            return null;
        });
    }

    @Test
    void shouldCreateOutboxEventAndProcessItSuccessfully() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("50.00"),
                Currency.TRY);

        new TransactionTemplate(txManager).execute(status -> {
            placeTransferPort.execute(request);
            return null;
        });

        List<OutboxJpaEntity> allOutboxEvents = outboxRepo.findAll();
        assertEquals(3, allOutboxEvents.size());

        OutboxJpaEntity event = allOutboxEvents.stream()
                .filter(e -> "TransferCompletedEvent".equals(e.getEventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("TransferCompletedEvent not found"));
        assertFalse(event.isProcessed());
        assertNull(event.getProcessedAt());

        outboxProcessor.processEvent(toEventEntry(event));

        OutboxJpaEntity processedEvent = outboxRepo.findById(event.getId()).orElseThrow();
        assertTrue(processedEvent.isProcessed());
        assertNotNull(processedEvent.getProcessedAt());
    }

    @Test
    void shouldIncrementRetryCountOnFailedProcessing() {
        OutboxJpaEntity entity = new OutboxJpaEntity(
                UUID.randomUUID().toString(),
                "Transfer", "123", "TransferCompletedEvent",
                "invalid json", LocalDateTime.now(), false, 0, false, null, 0);
        outboxRepo.save(entity);

        assertThrows(RuntimeException.class, () -> outboxProcessor.processEvent(toEventEntry(entity)));
        outboxProcessor.recordFailure(toEventEntry(entity), new RuntimeException("processing failed"), 5);

        OutboxJpaEntity failedEvent = outboxRepo.findById(entity.getId()).orElseThrow();
        assertEquals(1, failedEvent.getRetryCount());
        assertFalse(failedEvent.isProcessed());
        assertFalse(failedEvent.isDeadLetter());
        assertNotNull(failedEvent.getLastError());
    }

    @Test
    void shouldMoveToDeadLetterAfterMaxRetries() {
        OutboxJpaEntity entity = new OutboxJpaEntity(
                UUID.randomUUID().toString(),
                "Transfer", "123", "TransferCompletedEvent",
                "invalid json", LocalDateTime.now(), false, 4, false, "previous error", 0);
        outboxRepo.save(entity);

        assertThrows(RuntimeException.class, () -> outboxProcessor.processEvent(toEventEntry(entity)));
        outboxProcessor.recordFailure(toEventEntry(entity), new RuntimeException("processing failed"), 5);

        OutboxJpaEntity deadEvent = outboxRepo.findById(entity.getId()).orElseThrow();
        assertEquals(5, deadEvent.getRetryCount());
        assertTrue(deadEvent.isDeadLetter());
        assertFalse(deadEvent.isProcessed());
        assertNotNull(deadEvent.getLastError());
    }

    @Test
    void shouldProcessMultipleEventsInOnePoll() throws Exception {
        for (int i = 0; i < 3; i++) {
            com.bank.app.transfer.domain.TransferCompletedEvent payload =
                    new com.bank.app.transfer.domain.TransferCompletedEvent(
                            100L + i, 1L, 2L,
                            com.bank.app.common.domain.Money.of(
                                    new BigDecimal("10.00"),
                                    com.bank.app.common.domain.Currency.TRY),
                            com.bank.app.transfer.domain.TransferStatus.COMPLETED,
                            LocalDateTime.now());
            OutboxJpaEntity entity = new OutboxJpaEntity(
                    UUID.randomUUID().toString(),
                    "Transfer", String.valueOf(100L + i), "TransferCompletedEvent",
                    objectMapper.writeValueAsString(payload),
                    LocalDateTime.now(), false, 0, false, null, 0);
            outboxRepo.save(entity);
        }

        assertEquals(3, outboxRepo.findAll().stream().filter(e -> !e.isProcessed()).count());

        List<OutboxJpaEntity> events = outboxRepo.findAll();
        for (OutboxJpaEntity event : events) {
            outboxProcessor.processEvent(toEventEntry(event));
        }

        List<OutboxJpaEntity> processed = outboxRepo.findAll().stream()
                .filter(OutboxJpaEntity::isProcessed)
                .toList();
        assertEquals(3, processed.size());
        for (OutboxJpaEntity event : processed) {
            assertNull(event.getLastError());
            assertFalse(event.isDeadLetter());
            assertNotNull(event.getProcessedAt());
        }
    }
}
