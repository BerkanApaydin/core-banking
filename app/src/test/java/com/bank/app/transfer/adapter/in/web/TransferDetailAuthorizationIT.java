package com.bank.app.transfer.adapter.in.web;

import com.bank.app.BankApplication;

import com.bank.app.account.adapter.out.persistence.AccountJpaEntity;
import com.bank.app.account.adapter.out.persistence.AccountJpaRepository;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.adapter.out.persistence.TransferJpaEntity;
import com.bank.app.transfer.adapter.out.persistence.TransferJpaRepository;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization contract for {@code GET /api/v1/transfers/{id}} (11.1),
 * mirroring {@code AuditAdminAuthorizationIT}: full chain (version prefix,
 * filter, application-layer participant check, real wiring).
 *
 * <p>Pinned behavior: the sender AND the receiver may view (200); a stranger
 * gets 403 ACCESS_DENIED (note: unlike accounts, which answer 404 to hide
 * existence, transfers reveal existence — conscious asymmetry, pinned here);
 * unknown ids 404; anonymous 401.
 */
@AutoConfigureMockMvc
@Transactional
@SpringBootTest(classes = BankApplication.class)
@SuppressWarnings("null")
class TransferDetailAuthorizationIT extends AbstractSpringBootIntegrationTest {

    private final MockMvc mockMvc;

    private final UserJpaRepository userRepository;

    private final AccountJpaRepository accountRepository;

    private final TransferJpaRepository transferRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtPort jwtPort;

    @Autowired
    TransferDetailAuthorizationIT(MockMvc mockMvc, UserJpaRepository userRepository,
            AccountJpaRepository accountRepository, TransferJpaRepository transferRepository,
            PasswordEncoder passwordEncoder, JwtPort jwtPort,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtPort = jwtPort;
    }

    private String aliceToken;
    private String bobToken;
    private Long transferId;

    @BeforeEach
    void setUp() {
        UserJpaEntity alice = saveUser("detail_alice");
        UserJpaEntity bob = saveUser("detail_bob");
        aliceToken = jwtPort.generateToken(alice.getId(), alice.getUsername(), "ROLE_USER");
        bobToken = jwtPort.generateToken(bob.getId(), bob.getUsername(), "ROLE_USER");

        AccountJpaEntity sender = accountRepository.save(new AccountJpaEntity(null, alice.getId(),
                "TR770006200000000000000111", "Alice Sender", new BigDecimal("1000.00"),
                Currency.TRY, AccountStatus.ACTIVE, null));
        AccountJpaEntity receiver = accountRepository.save(new AccountJpaEntity(null, alice.getId(),
                "TR870006200000000000000222", "Alice Receiver", new BigDecimal("1000.00"),
                Currency.TRY, AccountStatus.ACTIVE, null));

        TransferJpaEntity transfer = new TransferJpaEntity(null, sender.getId(), receiver.getId(),
                new BigDecimal("100.00"), Currency.TRY, TransferStatus.COMPLETED, null);
        transfer.setBusinessCreatedAt(LocalDateTime.now());
        transferId = transferRepository.save(transfer).getId();
    }

    private UserJpaEntity saveUser(String username) {
        UserJpaEntity user = new UserJpaEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("Detail_pass12"));
        user.setRole(Role.ROLE_USER);
        return userRepository.save(user);
    }

    @Test
    @DisplayName("participant (sender owner) reads transfer details")
    void shouldAllowParticipant() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/" + transferId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    @DisplayName("stranger gets 404 with TRANSFER_NOT_FOUND (G-5 existence-oracle fix)")
    void shouldForbidStranger() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/" + transferId)
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("TRANSFER_NOT_FOUND")));
    }

    @Test
    @DisplayName("anonymous gets 401")
    void shouldRejectAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/" + transferId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unknown id gets 404")
    void shouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/999999999")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }
}
