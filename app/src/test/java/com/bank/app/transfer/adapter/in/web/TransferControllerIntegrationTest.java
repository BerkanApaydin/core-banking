package com.bank.app.transfer.adapter.in.web;




import com.bank.app.user.domain.Role;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.account.adapter.out.persistence.AccountJpaEntity;
import com.bank.app.account.adapter.out.persistence.AccountJpaRepository;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.common.domain.Currency;
import com.bank.app.infrastructure.adapter.out.persistence.IdempotencyKeyJpaEntity;
import com.bank.app.infrastructure.adapter.out.persistence.IdempotencyKeyJpaRepository;
import com.bank.app.infrastructure.adapter.in.idempotency.IdempotencyFingerprint;
import com.bank.app.audit.adapter.out.persistence.AuditLogJpaRepository;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.transfer.adapter.in.web.dto.TransferWebRequest;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.transfer.adapter.out.persistence.TransferJpaRepository;
import com.bank.app.transfer.adapter.out.persistence.TransferJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.i18n.LocaleContextHolder;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bank.app.common.application.port.out.EventPublisherPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SuppressWarnings("null")
@AutoConfigureMockMvc
@SpringBootTest(classes = com.bank.app.BankApplication.class)
@DisplayName("TransferController Integration")
class TransferControllerIntegrationTest extends AbstractSpringBootIntegrationTest {

        private final MockMvc mockMvc;

        private final AccountJpaRepository accountRepo;

        private final UserJpaRepository userRepository;

        private final ObjectMapper objectMapper;

        @MockitoBean
        private EventPublisherPort eventPublisherPort;

        private final IdempotencyKeyJpaRepository idempotencyKeyRepo;

        private final AuditLogJpaRepository auditRepo;

        private final PlatformTransactionManager transactionManager;

        private final JwtTokenProvider jwtTokenProvider;

        private final EntityManager entityManager;

        private final TransferJpaRepository transferRepo;

        @Autowired
        TransferControllerIntegrationTest(MockMvc mockMvc, AccountJpaRepository accountRepo,
                        UserJpaRepository userRepository, ObjectMapper objectMapper,
                        IdempotencyKeyJpaRepository idempotencyKeyRepo, AuditLogJpaRepository auditRepo,
                        PlatformTransactionManager transactionManager, JwtTokenProvider jwtTokenProvider,
                        EntityManager entityManager, TransferJpaRepository transferRepo,
                        ObjectProvider<CacheManager> cacheManagers) {
                super(cacheManagers);
                this.mockMvc = mockMvc;
                this.accountRepo = accountRepo;
                this.userRepository = userRepository;
                this.objectMapper = objectMapper;
                this.idempotencyKeyRepo = idempotencyKeyRepo;
                this.auditRepo = auditRepo;
                this.transactionManager = transactionManager;
                this.jwtTokenProvider = jwtTokenProvider;
                this.entityManager = entityManager;
                this.transferRepo = transferRepo;
        }

        private String jwtToken;
        private Long u3Id;

        private static String newIdempotencyKey() {
                return "it-" + UUID.randomUUID();
        }

        void saveIdempotencyKeyInNewTransaction(IdempotencyKeyJpaEntity entity) {
                var template = new TransactionTemplate(transactionManager);
                template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                template.execute(status -> {
                        idempotencyKeyRepo.save(entity);
                        return null;
                });
        }

        @BeforeEach
        void setUp() {
                SecurityContextHolder.clearContext();
                LocaleContextHolder.setLocale(Locale.of("tr", "TR"), true);

                var template = new TransactionTemplate(transactionManager);
                template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

                Long[] userIds = template.execute(status -> {
                        UserJpaEntity u1 = userRepository.save(
                                        new UserJpaEntity(null, "u1", "pass", Role.ROLE_USER, null, null, null));
                        UserJpaEntity u2 = userRepository.save(
                                        new UserJpaEntity(null, "u2", "pass", Role.ROLE_USER, null, null, null));
                        UserJpaEntity u3 = userRepository.save(
                                        new UserJpaEntity(null, "u3", "pass", Role.ROLE_USER, null, null, null));

                        accountRepo.save(new AccountJpaEntity(null, u1.getId(), "TR770006200000000000000111", "Ahmet",
                                        new BigDecimal("1000.00"), Currency.TRY, AccountStatus.ACTIVE, null));
                        accountRepo.save(new AccountJpaEntity(null, u2.getId(), "TR870006200000000000000222", "Mehmet",
                                        new BigDecimal("500.00"), Currency.TRY, AccountStatus.ACTIVE, null));
                        accountRepo.save(new AccountJpaEntity(null, u3.getId(), "TR970006200000000000000333", "Pasif",
                                        new BigDecimal("500.00"), Currency.TRY, AccountStatus.SUSPENDED, null));
                        u3Id = u3.getId();
                        return new Long[] { u1.getId(), u2.getId(), u3.getId() };
                });

                jwtToken = jwtTokenProvider.generateToken(userIds[0], "u1");
        }

        @AfterEach
        void tearDown() {
                var template = new TransactionTemplate(transactionManager);
                template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                template.execute(status -> {
                        entityManager.createQuery("delete from OutboxJpaEntity").executeUpdate();
                        entityManager.createQuery("delete from TransferJpaEntity").executeUpdate();
                        idempotencyKeyRepo.deleteAll();
                        auditRepo.deleteAll();
                        // Ledger legs reference accounts (V29 FK): child-first,
                        // so cleanup never depends on test execution order.
                        entityManager.createNativeQuery("DELETE FROM ledger_entries").executeUpdate();
                        accountRepo.deleteAll();
                        userRepository.deleteAll();
                        return null;
                });
                SecurityContextHolder.clearContext();
        }

        @Test
        void shouldPerformTransferSuccessfully() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR770006200000000000000111",
                                "TR870006200000000000000222",
                                new BigDecimal("200.00"),
                                Currency.TRY);

                var result = mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andReturn();

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id", notNullValue()))
                                .andExpect(jsonPath("$.status", is("COMPLETED")))
                                .andExpect(jsonPath("$.amount", is(200.00)))
                                .andExpect(jsonPath("$.currency", is("TRY")))
                                .andExpect(jsonPath("$.senderIban", is("TR770006200000000000000111")))
                                .andExpect(jsonPath("$.receiverIban", is("TR870006200000000000000222")))
                                .andExpect(jsonPath("$.senderAccountId", notNullValue()))
                                .andExpect(jsonPath("$.receiverAccountId", notNullValue()));
        }

        @Test
        void shouldReturnBadRequestWhenBalanceIsInsufficient() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR770006200000000000000111",
                                "TR870006200000000000000222",
                                new BigDecimal("2000.00"),
                                Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status", is(400)))
                                .andExpect(jsonPath("$.message", notNullValue()));
        }

        @Test
        void shouldReturnBadRequestWhenAccountIsPassive() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR970006200000000000000333",
                                "TR870006200000000000000222",
                                new BigDecimal("100.00"),
                                Currency.TRY);

                String u3Token = jwtTokenProvider.generateToken(u3Id, "u3");
                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + u3Token)
                                .header("Idempotency-Key", newIdempotencyKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status", is(400)))
                                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_ACTIVE")));
        }

        @Test
        void shouldPerformTransferAndCancelSuccessfully() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR770006200000000000000111",
                                "TR870006200000000000000222",
                                new BigDecimal("200.00"),
                                Currency.TRY);

                String responseJson = mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer transferId = objectMapper.readTree(responseJson).get("id").asInt();

                BigDecimal balanceSender = accountRepo.findByIban("TR770006200000000000000111").get().getBalance();
                BigDecimal balanceReceiver = accountRepo.findByIban("TR870006200000000000000222").get().getBalance();
                assertEquals(new BigDecimal("800.00"), balanceSender);
                assertEquals(new BigDecimal("700.00"), balanceReceiver);

                mockMvc.perform(post("/api/v1/transfers/" + transferId + "/cancel")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey()))
                                .andExpect(status().isNoContent());

                balanceSender = accountRepo.findByIban("TR770006200000000000000111").get().getBalance();
                balanceReceiver = accountRepo.findByIban("TR870006200000000000000222").get().getBalance();
                assertEquals(new BigDecimal("1000.00"), balanceSender);
                assertEquals(new BigDecimal("500.00"), balanceReceiver);
        }

        @Test
        void shouldReturnBadRequestWhenCancellingNonExistentTransfer() throws Exception {
                mockMvc.perform(post("/api/v1/transfers/99999/cancel")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey()))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status", is(404)))
                                .andExpect(jsonPath("$.code", is("TRANSFER_NOT_FOUND")));
        }

        @Test
        void shouldReturnBadRequestWhenRequestHasValidationErrors() throws Exception {
                TransferRequest request = new TransferRequest(
                                "",
                                "TR870006200000000000000222",
                                new BigDecimal("-50.00"),
                                Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.senderIban", notNullValue()))
                                .andExpect(jsonPath("$.errors.amount", notNullValue()));
        }

        @Test
        void shouldGenerateReportSuccessfully() throws Exception {
                TransferRequest req1 = new TransferRequest(
                                "TR770006200000000000000111",
                                "TR870006200000000000000222",
                                new BigDecimal("100.00"),
                                Currency.TRY);
                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req1)))
                                .andExpect(status().isCreated());

                TransferRequest req2 = new TransferRequest(
                                "TR770006200000000000000111",
                                "TR870006200000000000000222",
                                new BigDecimal("150.00"),
                                Currency.TRY);
                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req2)))
                                .andExpect(status().isCreated());

                Long accountId = accountRepo.findByIban("TR770006200000000000000111").get().getId();

                // UTC frame, not the machine zone: the application stamps
                // business time from Clock.systemUTC(), so a local "now" window
                // would silently exclude fresh transfers on off-UTC machines.
                LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
                LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);

                mockMvc.perform(get("/api/v1/transfers/report")
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("accountId", accountId.toString())
                                .param("startDate", start.toString())
                                .param("endDate", end.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accountId", is(accountId.intValue())))
.andExpect(jsonPath("$.pageTransferCount", is(2)))
                                 .andExpect(jsonPath("$.pageVolume", is(250.00)))
                                .andExpect(jsonPath("$.currency", is("TRY")))
                                .andExpect(jsonPath("$.transfers", notNullValue()))
                                .andExpect(jsonPath("$.transfers[0].senderIban", is("TR770006200000000000000111")))
                                .andExpect(jsonPath("$.transfers[0].receiverIban", is("TR870006200000000000000222")))
                                .andExpect(jsonPath("$.transfers[0].senderAccountId", notNullValue()))
                                .andExpect(jsonPath("$.transfers[0].receiverAccountId", notNullValue()));
        }

        @Test
        void shouldNotDoubleExecuteWithSameIdempotencyKey() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR770006200000000000000111",
                                "TR870006200000000000000222",
                                new BigDecimal("100.00"),
                                Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", "unique-key-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.amount", is(100.00)));

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", "unique-key-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.amount", is(100.00)));
        }

        @Test
        void shouldRejectSameKeyWithDifferentTransferAmount() throws Exception {
                TransferRequest first = new TransferRequest(
                                "TR770006200000000000000111", "TR870006200000000000000222",
                                new BigDecimal("100.00"), Currency.TRY);
                TransferRequest changed = new TransferRequest(
                                "TR770006200000000000000111", "TR870006200000000000000222",
                                new BigDecimal("200.00"), Currency.TRY);
                String key = newIdempotencyKey();

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(first)))
                                .andExpect(status().isCreated());

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(changed)))
                                .andExpect(status().isConflict());

                assertEquals(1, transferRepo.count());
        }

        @Test
        void shouldReserveFailedKeyAgainAndReplaySuccessfulTransfer() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR770006200000000000000111", "TR870006200000000000000222",
                                new BigDecimal("2000.00"), Currency.TRY);
                String key = newIdempotencyKey();
                String json = objectMapper.writeValueAsString(request);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON).content(json))
                                .andExpect(status().isBadRequest());

                new TransactionTemplate(transactionManager).execute(status -> {
                        AccountJpaEntity sender = accountRepo.findByIban(
                                "TR770006200000000000000111").orElseThrow();
                        sender.setBalance(new BigDecimal("3000.00"));
                        accountRepo.save(sender);
                        return null;
                });

                for (int i = 0; i < 2; i++) {
                        mockMvc.perform(post("/api/v1/transfers")
                                        .header("Authorization", "Bearer " + jwtToken)
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON).content(json))
                                        .andExpect(status().isCreated());
                }

                assertEquals(1, transferRepo.count());
                assertEquals(0, accountRepo.findByIban("TR770006200000000000000111")
                                .orElseThrow().getBalance().compareTo(new BigDecimal("1000.00")));
                assertEquals(1, auditRepo.findAll().stream()
                                .filter(log -> AuditAction.TRANSFER_EXECUTED.equals(log.getAction())).count());
        }

        @Test
        void shouldRejectHistorySizeOver100() throws Exception {
                Long accountId = accountRepo.findByIban("TR770006200000000000000111").get().getId();

                mockMvc.perform(get("/api/v1/transfers/history/" + accountId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("page", "0")
                                .param("size", "200"))
                                .andExpect(status().isBadRequest());

                mockMvc.perform(get("/api/v1/transfers/history/" + accountId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("page", "0")
                                .param("size", "100"))
                                .andExpect(status().isOk());
        }

        @Test
        void shouldRequireIdempotencyKeyWhenKeyIsBlank() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR770006200000000000000111",
                                "TR870006200000000000000222",
                                new BigDecimal("200.00"),
                                Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", "   ")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_KEY_REQUIRED")));
        }

        @Test
        void shouldFailRequestAndCleanIdempotencyKeyOnException() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR770006200000000000000111",
                                "TR870006200000000000000222",
                                new BigDecimal("2000.00"),
                                Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", "fail-key-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", "fail-key-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnConflictWhenIdempotencyKeyIsPending() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR770006200000000000000111",
                                "TR870006200000000000000222",
                                new BigDecimal("100.00"),
                                Currency.TRY);

                String key = IdempotencyFingerprint.operationKey("user",
                                String.valueOf(userRepository.findByUsername("u1").orElseThrow().getId()),
                                "POST", "/api/v1/transfers", "pending-key");
                byte[] args = objectMapper.writeValueAsBytes(new Object[] {
                                new TransferWebRequest(request.senderIban(), request.receiverIban(),
                                                request.amount(), request.currency()) });
                String hash = IdempotencyFingerprint.requestHash("POST", "/api/v1/transfers", null, args);
                var pending = new IdempotencyKeyJpaEntity(key, "PENDING", null, LocalDateTime.now());
                pending.setRequestHash(hash);
                saveIdempotencyKeyInNewTransaction(pending);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", "pending-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code", is("CONCURRENT_REQUEST")));
        }

        @Test
        void shouldGetTransferDetailSuccessfully() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR770006200000000000000111",
                                "TR870006200000000000000222",
                                new BigDecimal("200.00"),
                                Currency.TRY);

                String responseJson = mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer transferId = objectMapper.readTree(responseJson).get("id").asInt();

                mockMvc.perform(get("/api/v1/transfers/" + transferId)
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(transferId)))
                                .andExpect(jsonPath("$.amount", is(200.00)))
                                .andExpect(jsonPath("$.currency", is("TRY")))
                                .andExpect(jsonPath("$.senderAccountId", notNullValue()))
                                .andExpect(jsonPath("$.receiverAccountId", notNullValue()))
                                .andExpect(jsonPath("$.status", notNullValue()))
                                .andExpect(jsonPath("$.createdAt", notNullValue()));
        }

        @Test
        void shouldReturnNotFoundWhenTransferDetailNotFound() throws Exception {
                mockMvc.perform(get("/api/v1/transfers/99999")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code", is("TRANSFER_NOT_FOUND")));
        }

        @Test
        void shouldReturnConflictWhenCancellingAlreadyCancelledTransfer() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR770006200000000000000111",
                                "TR870006200000000000000222",
                                new BigDecimal("100.00"),
                                Currency.TRY);

                String responseJson = mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer transferId = objectMapper.readTree(responseJson).get("id").asInt();

                mockMvc.perform(post("/api/v1/transfers/" + transferId + "/cancel")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey()))
                                .andExpect(status().isNoContent());

                mockMvc.perform(post("/api/v1/transfers/" + transferId + "/cancel")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", newIdempotencyKey()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code", is("TRANSFER_ALREADY_CANCELLED")));
        }

        @Test
        void reportPaginationMarksTheLastPageWithoutSkippingRows() throws Exception {
                Long senderId = accountRepo.findByIban("TR770006200000000000000111").orElseThrow().getId();
                Long receiverId = accountRepo.findByIban("TR870006200000000000000222").orElseThrow().getId();
                LocalDateTime now = LocalDateTime.now();
                for (int amount = 1; amount <= 3; amount++) {
                        TransferJpaEntity transfer = new TransferJpaEntity(null, senderId, receiverId,
                                        BigDecimal.valueOf(amount), Currency.TRY, TransferStatus.COMPLETED, null);
                        transfer.setBusinessCreatedAt(now.minusMinutes(amount));
                        transferRepo.saveAndFlush(transfer);
                }

                for (int page = 0; page < 2; page++) {
                        mockMvc.perform(get("/api/v1/transfers/report")
                                        .header("Authorization", "Bearer " + jwtToken)
                                        .param("accountId", senderId.toString())
                                        .param("startDate", now.minusHours(1).toString())
                                        .param("endDate", now.plusHours(1).toString())
                                        .param("page", String.valueOf(page))
                                        .param("size", "2"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.pageTransferCount", is(page == 0 ? 2 : 1)))
                                        .andExpect(jsonPath("$.hasNext", is(page == 0)));
                }
        }

        @Test
        void shouldReturnEmptyReportWhenNoTransfersInDateRange() throws Exception {
                Long accountId = accountRepo.findByIban("TR770006200000000000000111").get().getId();
                LocalDateTime start = LocalDateTime.now().minusDays(30);
                LocalDateTime end = LocalDateTime.now().minusDays(29);

                mockMvc.perform(get("/api/v1/transfers/report")
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("accountId", accountId.toString())
                                .param("startDate", start.toString())
                                .param("endDate", end.toString()))
                                .andExpect(status().isOk())
.andExpect(jsonPath("$.pageTransferCount", is(0)))
                                 .andExpect(jsonPath("$.pageVolume", is(0)));
        }
}
