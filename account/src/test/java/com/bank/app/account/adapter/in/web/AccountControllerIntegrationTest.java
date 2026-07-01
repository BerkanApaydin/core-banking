package com.bank.app.account.adapter.in.web;

import com.bank.app.account.ModuleIntegrationTestConfig;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.adapter.out.persistence.AccountJpaEntity;
import com.bank.app.account.adapter.out.persistence.AccountJpaRepository;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.Currency;
import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@AutoConfigureMockMvc
@Transactional
@SpringBootTest(classes = { com.bank.app.account.TestApplication.class, ModuleIntegrationTestConfig.class })
@DisplayName("AccountController Integration")
class AccountControllerIntegrationTest extends AbstractSpringBootIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private AccountJpaRepository accountRepo;

        @Autowired
        private UserJpaRepository userRepository;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        private Long testUserId;
        private String jwtToken;

        @BeforeEach
        void setUp() {
                LocaleContextHolder.setLocale(Locale.of("tr", "TR"), true);
                UserJpaEntity u = userRepository.save(
                                new UserJpaEntity(null, "test_user", "pass", "ROLE_USER", null, null, null));
                testUserId = u.getId();
                jwtToken = jwtTokenProvider.generateToken(u.getId(), "test_user");
        }

        @Nested
        @DisplayName("POST /api/v1/accounts")
        class CreateAccount {

                @Test
                @DisplayName("should create account and return 201")
                void shouldCreateSuccessfully() throws Exception {
                        CreateAccountRequest request = new CreateAccountRequest(
                                        testUserId, "TR290006200000000000000999", "Fatma Demir",
                                        new BigDecimal("1500.00"), Currency.TRY);

                        mockMvc.perform(post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + jwtToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.id", notNullValue()))
                                        .andExpect(jsonPath("$.iban", is("TR290006200000000000000999")))
                                        .andExpect(jsonPath("$.ownerName", is("Fatma Demir")))
                                        .andExpect(jsonPath("$.balance", is(1500.00)))
                                        .andExpect(jsonPath("$.currency", is("TRY")))
                                        .andExpect(jsonPath("$.status", is("ACTIVE")));
                }

                @Test
                @DisplayName("should return 409 when IBAN already exists")
                void shouldReturn409OnDuplicateIban() throws Exception {
                        accountRepo.save(new AccountJpaEntity(null, testUserId, "TR290006200000000000000999",
                                        "Eski Sahip", new BigDecimal("100.00"), "TRY", "ACTIVE", null));

                        CreateAccountRequest request = new CreateAccountRequest(
                                        testUserId, "TR290006200000000000000999", "Fatma Demir",
                                        new BigDecimal("1500.00"), Currency.TRY);

                        mockMvc.perform(post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + jwtToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.status", is(409)))
                                        .andExpect(jsonPath("$.code", is("DUPLICATE_IBAN")));
                }

                @Test
                @DisplayName("should return 400 when balance is negative")
                void shouldReturn400OnNegativeBalance() throws Exception {
                        CreateAccountRequest request = new CreateAccountRequest(
                                        testUserId, "TR290006200000000000000777", "Veli",
                                        new BigDecimal("-100.00"), Currency.TRY);

                        mockMvc.perform(post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + jwtToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.initialBalance").exists());
                }

                @Test
                @DisplayName("should return 403 when creating account for another user")
                void shouldReturn403ForOtherUser() throws Exception {
                        UserJpaEntity otherUser = userRepository.save(
                                        new UserJpaEntity(null, "other_user3", "pass", "ROLE_USER", null, null, null));

                        CreateAccountRequest request = new CreateAccountRequest(
                                        otherUser.getId(), "TR290006200000000000000444", "Victim",
                                        new BigDecimal("100.00"), Currency.TRY);

                        mockMvc.perform(post("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + jwtToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
                }
        }

        @Nested
        @DisplayName("GET /api/v1/accounts")
        class ListAccounts {

                @Test
                @DisplayName("should list all accounts for the user")
                void shouldListAccounts() throws Exception {
                        accountRepo.save(new AccountJpaEntity(null, testUserId, "TR290006200000000000000888",
                                        "Fatma Demir", new BigDecimal("2000.00"), "TRY", "ACTIVE", null));

                        mockMvc.perform(get("/api/v1/accounts")
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content[0].iban", is("TR290006200000000000000888")))
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
                                        new AccountJpaEntity(null, testUserId, "TR290006200000000000000888",
                                                        "Fatma Demir", new BigDecimal("2000.00"), "TRY",
                                                        "ACTIVE", null));

                        mockMvc.perform(get("/api/v1/accounts/" + saved.getId())
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.id", is(saved.getId().intValue())))
                                        .andExpect(jsonPath("$.iban", is("TR290006200000000000000888")))
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
                @DisplayName("should return 403 when accessing another user's account")
                void shouldReturn403ForOtherUser() throws Exception {
                        UserJpaEntity otherUser = userRepository.save(
                                        new UserJpaEntity(null, "other_user", "pass", "ROLE_USER", null, null, null));
                        AccountJpaEntity otherAccount = accountRepo.save(
                                        new AccountJpaEntity(null, otherUser.getId(), "TR290006200000000000000555",
                                                        "Other Owner", new BigDecimal("500.00"), "TRY",
                                                        "ACTIVE", null));

                        mockMvc.perform(get("/api/v1/accounts/" + otherAccount.getId())
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
                }
        }

        @Nested
        @DisplayName("GET /api/v1/accounts/iban/{iban}")
        class GetAccountByIban {

                @Test
                @DisplayName("should return 200 when account exists")
                void shouldReturn200() throws Exception {
                        accountRepo.save(new AccountJpaEntity(null, testUserId, "TR290006200000000000000888",
                                        "Fatma Demir", new BigDecimal("2000.00"), "TRY", "ACTIVE", null));

                        mockMvc.perform(get("/api/v1/accounts/iban/TR290006200000000000000888")
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.iban", is("TR290006200000000000000888")))
                                        .andExpect(jsonPath("$.ownerName", is("Fatma Demir")));
                }

                @Test
                @DisplayName("should return 404 when IBAN not found")
                void shouldReturn404() throws Exception {
                        mockMvc.perform(get("/api/v1/accounts/iban/TR290006200000000000099999")
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND_IBAN")));
                }

                @Test
                @DisplayName("should return 403 when accessing another user's account by IBAN")
                void shouldReturn403ForOtherUser() throws Exception {
                        UserJpaEntity otherUser = userRepository.save(
                                        new UserJpaEntity(null, "other_user2", "pass", "ROLE_USER", null, null, null));
                        accountRepo.save(
                                        new AccountJpaEntity(null, otherUser.getId(), "TR290006200000000000000666",
                                                        "Other Owner", new BigDecimal("500.00"), "TRY",
                                                        "ACTIVE", null));

                        mockMvc.perform(get("/api/v1/accounts/iban/TR290006200000000000000666")
                                        .header("Authorization", "Bearer " + jwtToken))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
                }
        }
}
