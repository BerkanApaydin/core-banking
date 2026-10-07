package com.bank.app.transfer.adapter.in.web;

import com.bank.app.account.adapter.out.persistence.AccountJpaEntity;
import com.bank.app.account.adapter.out.persistence.AccountJpaRepository;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.Currency;
import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.bank.app.user.domain.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cross-account authorization matrix: every read/mutation scoped to the
// authenticated owner. Account lookups mask other-owned IDs as 404 (no
// existence leak); transfer-scoped reads/writes fail closed with 403.
@AutoConfigureMockMvc
@Transactional
@SpringBootTest(classes = com.bank.app.BankApplication.class)
@SuppressWarnings("null")
class TransferAccountAuthorizationIT extends AbstractSpringBootIntegrationTest {

    private static final String ALICE_IBAN = "TR770006200000000000000111";
    private static final String BOB_IBAN = "TR870006200000000000000222";

    private final MockMvc mockMvc;

    private final UserJpaRepository userRepository;

    private final AccountJpaRepository accountRepository;

    private final JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper;

    @Autowired
    TransferAccountAuthorizationIT(MockMvc mockMvc, UserJpaRepository userRepository,
            AccountJpaRepository accountRepository, JwtTokenProvider jwtTokenProvider,
            ObjectMapper objectMapper, ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    private String aliceToken;
    private String bobToken;
    private String charlieToken;
    private Long bobAccountId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UserJpaEntity alice = userRepository.save(
                new UserJpaEntity(null, "matrix_alice_" + suffix, "pass", Role.ROLE_USER, null, null, null));
        UserJpaEntity bob = userRepository.save(
                new UserJpaEntity(null, "matrix_bob_" + suffix, "pass", Role.ROLE_USER, null, null, null));
        UserJpaEntity charlie = userRepository.save(
                new UserJpaEntity(null, "matrix_charlie_" + suffix, "pass", Role.ROLE_USER, null, null, null));

        accountRepository.save(new AccountJpaEntity(null, alice.getId(), ALICE_IBAN, "Alice",
                new BigDecimal("1000.00"), Currency.TRY, AccountStatus.ACTIVE, null));
        AccountJpaEntity bobAccount = accountRepository.save(new AccountJpaEntity(null, bob.getId(), BOB_IBAN, "Bob",
                new BigDecimal("1000.00"), Currency.TRY, AccountStatus.ACTIVE, null));
        bobAccountId = bobAccount.getId();

        aliceToken = jwtTokenProvider.generateToken(alice.getId(), alice.getUsername());
        bobToken = jwtTokenProvider.generateToken(bob.getId(), bob.getUsername());
        charlieToken = jwtTokenProvider.generateToken(charlie.getId(), charlie.getUsername());
    }

    private long placeBobToAliceTransfer() throws Exception {
        Map<String, Object> body = Map.of(
                "senderIban", BOB_IBAN,
                "receiverIban", ALICE_IBAN,
                "amount", new BigDecimal("10.00"),
                "currency", "TRY");

        String created = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + bobToken)
                        .header("Idempotency-Key", "matrix-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    @Test
    @DisplayName("cross-user account lookup by id returns 404 (no existence leak)")
    void shouldMaskForeignAccountId() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + bobAccountId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND_ID")));
    }

    @Test
    @DisplayName("cross-user account lookup by IBAN returns 404 (no existence leak)")
    void shouldMaskForeignAccountIban() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/iban/" + BOB_IBAN)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND_IBAN")));
    }

    @Test
    @DisplayName("cross-user transfer history returns 403")
    void shouldForbidForeignHistory() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/history/" + bobAccountId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("cross-user transfer report returns 403")
    void shouldForbidForeignReport() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/report")
                        .header("Authorization", "Bearer " + aliceToken)
                        .param("accountId", String.valueOf(bobAccountId))
                        .param("startDate", "2026-01-01T00:00:00")
                        .param("endDate", "2026-12-31T23:59:59"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("transfer from another user's account returns 403")
    void shouldForbidTransferFromForeignAccount() throws Exception {
        Map<String, Object> body = Map.of(
                "senderIban", BOB_IBAN,
                "receiverIban", ALICE_IBAN,
                "amount", new BigDecimal("10.00"),
                "currency", "TRY");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", "matrix-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("non-sender cannot cancel a transfer (sender-only cancel)")
    void shouldForbidCancelByNonSender() throws Exception {
        long transferId = placeBobToAliceTransfer();

        // Alice is the receiver but not the sender: cancel must fail closed.
        mockMvc.perform(post("/api/v1/transfers/" + transferId + "/cancel")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", "matrix-" + UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("third party cannot read transfer details")
    void shouldForbidDetailForThirdParty() throws Exception {
        long transferId = placeBobToAliceTransfer();

        mockMvc.perform(get("/api/v1/transfers/" + transferId)
                        .header("Authorization", "Bearer " + charlieToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("transfer party can read transfer details")
    void shouldAllowDetailForParty() throws Exception {
        long transferId = placeBobToAliceTransfer();

        mockMvc.perform(get("/api/v1/transfers/" + transferId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) transferId)));
    }

    @Test
    @DisplayName("owner reads own account")
    void shouldAllowOwnerAccountRead() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + bobAccountId)
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban", is(BOB_IBAN)));
    }

    @Test
    @DisplayName("anonymous account read returns 401")
    void shouldRejectAnonymousAccountRead() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + bobAccountId))
                .andExpect(status().isUnauthorized());
    }
}
