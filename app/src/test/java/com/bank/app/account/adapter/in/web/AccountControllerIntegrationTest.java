package com.bank.app.account.adapter.in.web;

import com.bank.app.BankApplication;




import com.bank.app.user.domain.Role;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Currency;
import com.bank.app.account.adapter.out.persistence.AccountJpaEntity;
import com.bank.app.account.adapter.out.persistence.AccountJpaRepository;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.bank.app.common.application.port.out.EventPublisherPort;

@SuppressWarnings("null")
@AutoConfigureMockMvc
@Transactional
@SpringBootTest(classes = BankApplication.class)
@DisplayName("AccountController Integration")
class AccountControllerIntegrationTest extends AbstractSpringBootIntegrationTest {

        @MockitoBean
        private EventPublisherPort eventPublisherPort;

        private final MockMvc mockMvc;

        private final AccountJpaRepository accountRepo;

        private final UserJpaRepository userRepository;

        private final ObjectMapper objectMapper;

        private final JwtTokenProvider jwtTokenProvider;

        @Autowired
        AccountControllerIntegrationTest(MockMvc mockMvc, AccountJpaRepository accountRepo,
                        UserJpaRepository userRepository, ObjectMapper objectMapper,
                        JwtTokenProvider jwtTokenProvider, ObjectProvider<CacheManager> cacheManagers) {
                super(cacheManagers);
                this.mockMvc = mockMvc;
                this.accountRepo = accountRepo;
                this.userRepository = userRepository;
                this.objectMapper = objectMapper;
                this.jwtTokenProvider = jwtTokenProvider;
        }

        private Long testUserId;
        private String jwtToken;

        @Test
        void logoutIsNotAnAnonymousAuthEndpoint() throws Exception {
                mockMvc.perform(post("/api/v1/auth/logout"))
                                .andExpect(status().isUnauthorized());
        }

        @BeforeEach
        void setUp() {
                LocaleContextHolder.setLocale(Locale.of("tr", "TR"), true);
                UserJpaEntity u = userRepository.save(
                                new UserJpaEntity(null, "test_user", "pass", Role.ROLE_USER, null, null, null));
                testUserId = u.getId();
                jwtToken = jwtTokenProvider.generateToken(u.getId(), "test_user");
        }

        @Nested
        @DisplayName("POST /api/v1/accounts")
        class CreateAccount {

                @Test
                @DisplayName("should create account and return 201")
                void shouldCreateSuccessfully() throws Exception {
                        Map<String, Object> request = Map.of("ownerName", "Fatma Demir",
                                        "initialBalance", new BigDecimal("1500.00"), "currency", "TRY");

                        mockMvc.perform(post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + jwtToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.id", notNullValue()))
                                        .andExpect(jsonPath("$.iban", matchesPattern("TR[0-9]{24}")))
                                        .andExpect(jsonPath("$.ownerName", is("Fatma Demir")))
                                        .andExpect(jsonPath("$.balance", is(1500.00)))
                                        .andExpect(jsonPath("$.currency", is("TRY")))
                                        .andExpect(jsonPath("$.status", is("ACTIVE")));
                }

                @Test
                @DisplayName("should ignore a client-supplied IBAN")
                void shouldIgnoreClientSuppliedIban() throws Exception {
                        accountRepo.save(new AccountJpaEntity(null, testUserId, "TR600006200000000000000999",
                                        "Eski Sahip", new BigDecimal("100.00"), Currency.TRY, AccountStatus.ACTIVE, null));

                        Map<String, Object> request = Map.of("iban", "TR600006200000000000000999",
                                        "ownerName", "Fatma Demir", "initialBalance", new BigDecimal("1500.00"),
                                        "currency", "TRY");

                        mockMvc.perform(post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + jwtToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.iban", not(is("TR600006200000000000000999"))));
                }

                @Test
                @DisplayName("should return 400 when balance is negative")
                void shouldReturn400OnNegativeBalance() throws Exception {
                        Map<String, Object> request = Map.of("ownerName", "Veli",
                                        "initialBalance", new BigDecimal("-100.00"), "currency", "TRY");

                        mockMvc.perform(post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + jwtToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.initialBalance").exists());
                }

                @Test
                @DisplayName("should ignore a forged userId and use the authenticated principal")
                void shouldIgnoreForgedUserId() throws Exception {
                        UserJpaEntity otherUser = userRepository.save(
                                        new UserJpaEntity(null, "other_user3", "pass", Role.ROLE_USER, null, null, null));

                        Map<String, Object> request = Map.of("userId", otherUser.getId(),
                                        "ownerName", "Victim", "initialBalance", new BigDecimal("100.00"),
                                        "currency", "TRY");

                        mockMvc.perform(post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + jwtToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.userId", is(testUserId.intValue())));
                }

                @Test
                @DisplayName("should ignore an invalid client-supplied IBAN")
                void shouldIgnoreInvalidClientIban() throws Exception {
                        Map<String, Object> request = Map.of("iban", "TR340006100519786457841326",
                                        "ownerName", "Fatma Demir", "initialBalance", BigDecimal.ZERO,
                                        "currency", "TRY");

                        mockMvc.perform(post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + jwtToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.iban", not(is("TR340006100519786457841326"))));
                }
        }

        @Nested
        @DisplayName("GET /api/v1/accounts")
        class ListAccounts {

                @Test
                @DisplayName("should list all accounts for the user")
                void shouldListAccounts() throws Exception {
                        accountRepo.save(new AccountJpaEntity(null, testUserId, "TR500006200000000000000888",
                                        "Fatma Demir", new BigDecimal("2000.00"), Currency.TRY, AccountStatus.ACTIVE, null));

                        mockMvc.perform(get("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content[0].iban", is("TR500006200000000000000888")))
                                        .andExpect(jsonPath("$.content[0].ownerName", is("Fatma Demir")))
                                        .andExpect(jsonPath("$.page", is(0)))
                                        .andExpect(jsonPath("$.totalElements", is(1)));
                }
        }

        @Nested
        @DisplayName("GET /api/v1/accounts/{id}")
        class GetAccountById {

                @Test
                @DisplayName("should return 200 when account exists")
                void shouldReturn200() throws Exception {
                        AccountJpaEntity saved = accountRepo.save(
                                        new AccountJpaEntity(null, testUserId, "TR500006200000000000000888",
                                                        "Fatma Demir", new BigDecimal("2000.00"), Currency.TRY,
                                                        AccountStatus.ACTIVE, null));

                        mockMvc.perform(get("/api/v1/accounts/" + saved.getId())
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.id", is(saved.getId().intValue())))
                                        .andExpect(jsonPath("$.iban", is("TR500006200000000000000888")))
                                        .andExpect(jsonPath("$.ownerName", is("Fatma Demir")))
                                        .andExpect(jsonPath("$.balance", is(2000.00)));
                }

                @Test
                @DisplayName("should return 404 when account does not exist")
                void shouldReturn404() throws Exception {
                        mockMvc.perform(get("/api/v1/accounts/9999")
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.status", is(404)))
                                        .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND_ID")));
                }

                @Test
                @DisplayName("should return 404 when accessing another user's account")
                void shouldReturn404ForOtherUser() throws Exception {
                        UserJpaEntity otherUser = userRepository.save(
                                        new UserJpaEntity(null, "other_user", "pass", Role.ROLE_USER, null, null, null));
                        AccountJpaEntity otherAccount = accountRepo.save(
                                        new AccountJpaEntity(null, otherUser.getId(), "TR200006200000000000000555",
                                                        "Other Owner", new BigDecimal("500.00"), Currency.TRY,
                                                        AccountStatus.ACTIVE, null));

                        mockMvc.perform(get("/api/v1/accounts/" + otherAccount.getId())
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND_ID")));
                }
        }

        @Nested
        @DisplayName("GET /api/v1/accounts/iban/{iban}")
        class GetAccountByIban {

                @Test
                @DisplayName("should return 200 when account exists")
                void shouldReturn200() throws Exception {
                        accountRepo.save(new AccountJpaEntity(null, testUserId, "TR500006200000000000000888",
                                        "Fatma Demir", new BigDecimal("2000.00"), Currency.TRY, AccountStatus.ACTIVE, null));

                        mockMvc.perform(get("/api/v1/accounts/iban/TR500006200000000000000888")
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.iban", is("TR500006200000000000000888")))
                                        .andExpect(jsonPath("$.ownerName", is("Fatma Demir")));
                }

                @Test
                @DisplayName("should return 404 when IBAN not found")
                void shouldReturn404() throws Exception {
                        mockMvc.perform(get("/api/v1/accounts/iban/TR890006200000000000099999")
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND_IBAN")));
                }

                @Test
                @DisplayName("should return 404 when accessing another user's account by IBAN")
                void shouldReturn404ForOtherUser() throws Exception {
                        UserJpaEntity otherUser = userRepository.save(
                                        new UserJpaEntity(null, "other_user2", "pass", Role.ROLE_USER, null, null, null));
                        accountRepo.save(
                                        new AccountJpaEntity(null, otherUser.getId(), "TR300006200000000000000666",
                                                        "Other Owner", new BigDecimal("500.00"), Currency.TRY,
                                                        AccountStatus.ACTIVE, null));

                        mockMvc.perform(get("/api/v1/accounts/iban/TR300006200000000000000666")
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND_IBAN")));
                }
        }
}
