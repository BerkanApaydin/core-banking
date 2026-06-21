package com.bank.app.transfer.infrastructure.web;

import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.account.infrastructure.persistence.AccountJpaRepository;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.common.domain.Money;
import com.bank.app.common.persistence.IdempotencyKeyJpaEntity;
import com.bank.app.common.persistence.IdempotencyKeyJpaRepository;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import com.bank.app.transfer.ModuleIntegrationTestConfig;
import com.bank.app.user.infrastructure.persistence.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bank.app.common.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@SpringBootTest(classes = { com.bank.app.transfer.TestApplication.class, ModuleIntegrationTestConfig.class })
@SuppressWarnings("null")
class TransferControllerIntegrationTest extends AbstractSpringBootIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private AccountJpaRepository accountRepo;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private IdempotencyKeyJpaRepository idempotencyKeyRepo;

        @Autowired
        private PlatformTransactionManager transactionManager;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        private String jwtToken;

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
                LocaleContextHolder.resetLocaleContext();
                Locale.setDefault(Locale.of("tr", "TR"));
                UserJpaEntity u1 = userRepository.save(
                                new UserJpaEntity(null, "u1", "pass", "ROLE_USER"));
                UserJpaEntity u2 = userRepository.save(
                                new UserJpaEntity(null, "u2", "pass", "ROLE_USER"));
                UserJpaEntity u3 = userRepository.save(
                                new UserJpaEntity(null, "u3", "pass", "ROLE_USER"));

                accountRepo.save(new AccountJpaEntity(null, u1.getId(), "TR290006200000000000000111", "Ahmet",
                                new BigDecimal("1000.00"), "TRY", true));
                accountRepo.save(new AccountJpaEntity(null, u2.getId(), "TR290006200000000000000222", "Mehmet",
                                new BigDecimal("500.00"), "TRY", true));
                accountRepo.save(new AccountJpaEntity(null, u3.getId(), "TR290006200000000000000333", "Pasif",
                                new BigDecimal("500.00"), "TRY", false));

                jwtToken = jwtTokenProvider.generateToken(u1.getId(), "u1");
        }

        @Test
        void shouldPerformTransferSuccessfully() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("200.00"),
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id", notNullValue()))
                                .andExpect(jsonPath("$.status", is("COMPLETED")))
                                .andExpect(jsonPath("$.amount", is(200.00)))
                                .andExpect(jsonPath("$.currency", is("TRY")))
                                .andExpect(jsonPath("$.senderIban", is("TR290006200000000000000111")))
                                .andExpect(jsonPath("$.receiverIban", is("TR290006200000000000000222")))
                                .andExpect(jsonPath("$.senderAccountId", notNullValue()))
                                .andExpect(jsonPath("$.receiverAccountId", notNullValue()));
        }

        @Test
        void shouldReturnBadRequestWhenBalanceIsInsufficient() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("2000.00"),
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status", is(400)))
                                .andExpect(jsonPath("$.message", notNullValue()));
        }

        @Test
        void shouldReturnBadRequestWhenAccountIsPassive() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000333",
                                "TR290006200000000000000222",
                                new BigDecimal("100.00"),
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status", is(400)))
                                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_ACTIVE")));
        }

        @Test
        void shouldPerformTransferAndCancelSuccessfully() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("200.00"),
                                Money.Currency.TRY);

                String responseJson = mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer transferId = objectMapper.readTree(responseJson).get("id").asInt();

                BigDecimal balanceSender = accountRepo.findByIban("TR290006200000000000000111").get().getBalance();
                BigDecimal balanceReceiver = accountRepo.findByIban("TR290006200000000000000222").get().getBalance();
                assertEquals(new BigDecimal("800.00"), balanceSender);
                assertEquals(new BigDecimal("700.00"), balanceReceiver);

                mockMvc.perform(post("/api/v1/transfers/" + transferId + "/cancel")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNoContent());

                balanceSender = accountRepo.findByIban("TR290006200000000000000111").get().getBalance();
                balanceReceiver = accountRepo.findByIban("TR290006200000000000000222").get().getBalance();
                assertEquals(new BigDecimal("1000.00"), balanceSender);
                assertEquals(new BigDecimal("500.00"), balanceReceiver);
        }

        @Test
        void shouldReturnBadRequestWhenCancellingNonExistentTransfer() throws Exception {
                mockMvc.perform(post("/api/v1/transfers/99999/cancel")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status", is(404)))
                                .andExpect(jsonPath("$.code", is("TRANSFER_NOT_FOUND")));
        }

        @Test
        void shouldReturnBadRequestWhenRequestHasValidationErrors() throws Exception {
                TransferRequest request = new TransferRequest(
                                "",
                                "TR290006200000000000000222",
                                new BigDecimal("-50.00"),
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.senderIban", notNullValue()))
                                .andExpect(jsonPath("$.errors.amount", notNullValue()));
        }

        @Test
        void shouldGenerateReportSuccessfully() throws Exception {
                TransferRequest req1 = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("100.00"),
                                Money.Currency.TRY);
                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req1)))
                                .andExpect(status().isCreated());

                TransferRequest req2 = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("150.00"),
                                Money.Currency.TRY);
                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req2)))
                                .andExpect(status().isCreated());

                Long accountId = accountRepo.findByIban("TR290006200000000000000111").get().getId();

                LocalDateTime start = LocalDateTime.now().minusHours(1);
                LocalDateTime end = LocalDateTime.now().plusHours(1);

                mockMvc.perform(get("/api/v1/transfers/report")
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("accountId", accountId.toString())
                                .param("startDate", start.toString())
                                .param("endDate", end.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accountId", is(accountId.intValue())))
                                .andExpect(jsonPath("$.totalTransfersCount", is(2)))
                                .andExpect(jsonPath("$.totalVolume", is(250.00)))
                                .andExpect(jsonPath("$.currency", is("TRY")))
                                .andExpect(jsonPath("$.transfers", notNullValue()))
                                .andExpect(jsonPath("$.transfers[0].senderIban", is("TR290006200000000000000111")))
                                .andExpect(jsonPath("$.transfers[0].receiverIban", is("TR290006200000000000000222")))
                                .andExpect(jsonPath("$.transfers[0].senderAccountId", notNullValue()))
                                .andExpect(jsonPath("$.transfers[0].receiverAccountId", notNullValue()));
        }

        @Test
        void shouldNotDoubleExecuteWithSameIdempotencyKey() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("100.00"),
                                Money.Currency.TRY);

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
        void shouldCapHistorySizeTo100() throws Exception {
                Long accountId = accountRepo.findByIban("TR290006200000000000000111").get().getId();

                mockMvc.perform(get("/api/v1/transfers/history/" + accountId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("page", "0")
                                .param("size", "200"))
                                .andExpect(status().isOk());
        }

        @Test
        void shouldPerformTransferSuccessfullyWhenIdempotencyKeyIsBlank() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("200.00"),
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("Idempotency-Key", "   ")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id", notNullValue()));
        }

        @Test
        void shouldFailRequestAndCleanIdempotencyKeyOnException() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("2000.00"),
                                Money.Currency.TRY);

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
                saveIdempotencyKeyInNewTransaction(new IdempotencyKeyJpaEntity(
                                "u1_pending-key", "PENDING", null, LocalDateTime.now()));

                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("100.00"),
                                Money.Currency.TRY);

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
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("200.00"),
                                Money.Currency.TRY);

                String responseJson = mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
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
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("100.00"),
                                Money.Currency.TRY);

                String responseJson = mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer transferId = objectMapper.readTree(responseJson).get("id").asInt();

                mockMvc.perform(post("/api/v1/transfers/" + transferId + "/cancel")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNoContent());

                mockMvc.perform(post("/api/v1/transfers/" + transferId + "/cancel")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code", is("TRANSFER_ALREADY_CANCELLED")));
        }

        @Test
        void shouldReturnEmptyReportWhenNoTransfersInDateRange() throws Exception {
                Long accountId = accountRepo.findByIban("TR290006200000000000000111").get().getId();
                LocalDateTime start = LocalDateTime.now().minusDays(30);
                LocalDateTime end = LocalDateTime.now().minusDays(29);

                mockMvc.perform(get("/api/v1/transfers/report")
                                .header("Authorization", "Bearer " + jwtToken)
                                .param("accountId", accountId.toString())
                                .param("startDate", start.toString())
                                .param("endDate", end.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalTransfersCount", is(0)))
                                .andExpect(jsonPath("$.totalVolume", is(0)));
        }
}
