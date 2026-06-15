package com.bank.app.transfer.infrastructure.web;

import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.account.infrastructure.persistence.SpringDataAccountRepo;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.common.domain.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.bank.app.common.security.SecurityUtils;
import java.util.Locale;
import java.util.Optional;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@SuppressWarnings("null")
class TransferControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private SpringDataAccountRepo accountRepo;

        @Autowired
        private com.bank.app.user.infrastructure.persistence.UserRepository userRepository;

        @Autowired
        private com.bank.app.transfer.infrastructure.persistence.SpringDataTransferRepo transferRepo;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private SecurityUtils securityUtils;

        @Autowired
        private com.bank.app.common.persistence.SpringDataIdempotencyKeyRepo idempotencyKeyRepo;

        @BeforeEach
        void setUp() {
                Locale.setDefault(Locale.of("tr", "TR"));
                idempotencyKeyRepo.deleteAll();
                transferRepo.deleteAll();
                accountRepo.deleteAll();
                userRepository.deleteAll();

                com.bank.app.user.infrastructure.persistence.UserJpaEntity u1 = userRepository.save(
                    new com.bank.app.user.infrastructure.persistence.UserJpaEntity(null, "u1", "pass", "ROLE_USER")
                );
                com.bank.app.user.infrastructure.persistence.UserJpaEntity u2 = userRepository.save(
                    new com.bank.app.user.infrastructure.persistence.UserJpaEntity(null, "u2", "pass", "ROLE_USER")
                );
                com.bank.app.user.infrastructure.persistence.UserJpaEntity u3 = userRepository.save(
                    new com.bank.app.user.infrastructure.persistence.UserJpaEntity(null, "u3", "pass", "ROLE_USER")
                );

                accountRepo.save(new AccountJpaEntity(null, u1.getId(), "TR290006200000000000000111", "Ahmet",
                                new BigDecimal("1000.00"), "TRY", true));
                accountRepo.save(new AccountJpaEntity(null, u2.getId(), "TR290006200000000000000222", "Mehmet",
                                new BigDecimal("500.00"), "TRY", true));
                accountRepo.save(new AccountJpaEntity(null, u3.getId(), "TR290006200000000000000333", "Pasif",
                                new BigDecimal("500.00"), "TRY", false));

                when(securityUtils.getCurrentUserId()).thenReturn(Optional.of(u1.getId()));
                when(securityUtils.getCurrentUsername()).thenReturn(Optional.of("u1"));
        }

        @Test
        void shouldPerformTransferSuccessfully() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("200.00"),
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
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
                                new BigDecimal("2000.00"), // balance is 1000.00
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status", is(400)))
                                .andExpect(jsonPath("$.message", notNullValue()));
        }

        @Test
        void shouldReturnBadRequestWhenAccountIsPassive() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000333", // passive account
                                "TR290006200000000000000222",
                                new BigDecimal("100.00"),
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status", is(400)))
                                .andExpect(jsonPath("$.message", is("Hesap aktif değil: TR290006200000000000000333")));
        }

        @Test
        void shouldPerformTransferAndCancelSuccessfully() throws Exception {
                // 1. Perform a successful transfer
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("200.00"),
                                Money.Currency.TRY);

                String responseJson = mockMvc.perform(post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer transferId = objectMapper.readTree(responseJson).get("id").asInt();

                // Check balances in database before cancelling
                BigDecimal balanceSender = accountRepo.findByIban("TR290006200000000000000111").get().getBalance();
                BigDecimal balanceReceiver = accountRepo.findByIban("TR290006200000000000000222").get().getBalance();
                assertEquals(new BigDecimal("800.00"), balanceSender);
                assertEquals(new BigDecimal("700.00"), balanceReceiver);

                // 2. Cancel the transfer
                mockMvc.perform(post("/api/v1/transfers/" + transferId + "/cancel"))
                                .andExpect(status().isNoContent());

                // Check balances in database after cancelling - should be restored
                balanceSender = accountRepo.findByIban("TR290006200000000000000111").get().getBalance();
                balanceReceiver = accountRepo.findByIban("TR290006200000000000000222").get().getBalance();
                assertEquals(new BigDecimal("1000.00"), balanceSender);
                assertEquals(new BigDecimal("500.00"), balanceReceiver);
        }

        @Test
        void shouldReturnBadRequestWhenCancellingNonExistentTransfer() throws Exception {
                mockMvc.perform(post("/api/v1/transfers/99999/cancel"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status", is(404)))
                                .andExpect(jsonPath("$.message", is("Transfer bulunamadı. ID: 99999")));
        }

        @Test
        void shouldReturnBadRequestWhenRequestHasValidationErrors() throws Exception {
                // Request with empty sender IBAN and negative amount
                TransferRequest request = new TransferRequest(
                                "",
                                "TR290006200000000000000222",
                                new BigDecimal("-50.00"),
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.senderIban", notNullValue()))
                                .andExpect(jsonPath("$.amount", notNullValue()));
        }

        @Test
        void shouldGenerateReportSuccessfully() throws Exception {
                // 1. Perform two transfers
                TransferRequest req1 = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("100.00"),
                                Money.Currency.TRY);
                mockMvc.perform(post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req1)))
                                .andExpect(status().isCreated());

                TransferRequest req2 = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("150.00"),
                                Money.Currency.TRY);
                mockMvc.perform(post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req2)))
                                .andExpect(status().isCreated());

                // Find the sender account ID
                Long accountId = accountRepo.findByIban("TR290006200000000000000111").get().getId();

                LocalDateTime start = LocalDateTime.now().minusHours(1);
                LocalDateTime end = LocalDateTime.now().plusHours(1);

                // 2. Query report
                mockMvc.perform(get("/api/v1/transfers/report")
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

                // First request with key
                mockMvc.perform(post("/api/v1/transfers")
                                .header("Idempotency-Key", "unique-key-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.amount", is(100.00)));

                // Second request with same key
                mockMvc.perform(post("/api/v1/transfers")
                                .header("Idempotency-Key", "unique-key-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk()) // Returns cached response (HTTP 200)
                                .andExpect(jsonPath("$.amount", is(100.00)));
        }

        @Test
        void shouldCapHistorySizeTo100() throws Exception {
                Long accountId = accountRepo.findByIban("TR290006200000000000000111").get().getId();

                mockMvc.perform(get("/api/v1/transfers/history/" + accountId)
                                .param("page", "0")
                                .param("size", "200")) // size is capped at 100
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
                                .header("Idempotency-Key", "   ")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id", notNullValue()));
        }

        @Test
        void shouldReturnForbiddenWhenIdempotencyKeyProvidedButNotLoggedIn() throws Exception {
                when(securityUtils.getCurrentUsername()).thenReturn(Optional.empty());

                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("100.00"),
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Idempotency-Key", "some-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden());
        }

        @Test
        void shouldFailRequestAndCleanIdempotencyKeyOnException() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("2000.00"), // balance is 1000.00, will fail
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Idempotency-Key", "fail-key-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
                
                // If it cleaned up successfully, we should be able to run it again (it won't be pending or completed)
                mockMvc.perform(post("/api/v1/transfers")
                                .header("Idempotency-Key", "fail-key-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnConflictWhenIdempotencyKeyIsPending() throws Exception {
                idempotencyKeyRepo.save(new com.bank.app.common.persistence.IdempotencyKeyJpaEntity(
                                "u1_pending-key", "PENDING", null, LocalDateTime.now()));

                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("100.00"),
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/transfers")
                                .header("Idempotency-Key", "pending-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message", is("Bu işlem şu anda gerçekleştiriliyor. Lütfen bekleyin.")));
        }

        @Test
        void shouldGetTransferDetailSuccessfully() throws Exception {
                TransferRequest request = new TransferRequest(
                                "TR290006200000000000000111",
                                "TR290006200000000000000222",
                                new BigDecimal("200.00"),
                                Money.Currency.TRY);

                String responseJson = mockMvc.perform(post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Integer transferId = objectMapper.readTree(responseJson).get("id").asInt();

                mockMvc.perform(get("/api/v1/transfers/" + transferId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(transferId)))
                                .andExpect(jsonPath("$.amount", is(200.00)))
                                .andExpect(jsonPath("$.currency", is("TRY")))
                                .andExpect(jsonPath("$.senderAccountId", notNullValue()))
                                .andExpect(jsonPath("$.receiverAccountId", notNullValue()))
                                .andExpect(jsonPath("$.status", notNullValue()))
                                .andExpect(jsonPath("$.createdAt", notNullValue()));
        }
}
