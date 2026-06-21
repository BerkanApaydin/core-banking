package com.bank.app.account.infrastructure.web;

import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.account.infrastructure.persistence.AccountJpaRepository;
import com.bank.app.account.ModuleIntegrationTestConfig;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.Money;
import com.bank.app.common.security.JwtTokenProvider;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import com.bank.app.user.infrastructure.persistence.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@SpringBootTest(classes = {com.bank.app.account.TestApplication.class, ModuleIntegrationTestConfig.class})
@SuppressWarnings("null")
class AccountControllerIntegrationTest extends AbstractSpringBootIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private AccountJpaRepository accountRepo;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        private Long testUserId;

        private String jwtToken;

        @BeforeEach
        void setUp() {
                LocaleContextHolder.resetLocaleContext();
                Locale.setDefault(Locale.of("tr", "TR"));
                UserJpaEntity u = userRepository.save(
                                new UserJpaEntity(null, "test_user", "pass", "ROLE_USER"));
                testUserId = u.getId();
                jwtToken = jwtTokenProvider.generateToken(u.getId(), "test_user");
        }

        @Test
        void shouldCreateAccountSuccessfully() throws Exception {
                CreateAccountRequest request = new CreateAccountRequest(
                                testUserId,
                                "TR290006200000000000000999",
                                "Fatma Demir",
                                new BigDecimal("1500.00"),
                                Money.Currency.TRY);

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
                                .andExpect(jsonPath("$.active", is(true)));
        }

        @Test
        void shouldReturnBadRequestWhenCreatingAccountWithDuplicateIban() throws Exception {
                accountRepo.save(new AccountJpaEntity(null, testUserId, "TR290006200000000000000999", "Eski Sahip",
                                new BigDecimal("100.00"), "TRY", true));

                CreateAccountRequest request = new CreateAccountRequest(
                                testUserId,
                                "TR290006200000000000000999",
                                "Fatma Demir",
                                new BigDecimal("1500.00"),
                                Money.Currency.TRY);

                mockMvc.perform(post("/api/v1/accounts")
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.status", is(409)))
                                .andExpect(jsonPath("$.code", is("DUPLICATE_IBAN")));
        }

        @Test
        void shouldGetAccountByIdSuccessfully() throws Exception {
                AccountJpaEntity saved = accountRepo
                                .save(new AccountJpaEntity(null, testUserId, "TR290006200000000000000888",
                                                "Fatma Demir", new BigDecimal("2000.00"), "TRY", true));

                mockMvc.perform(get("/api/v1/accounts/" + saved.getId())
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(saved.getId().intValue())))
                                .andExpect(jsonPath("$.iban", is("TR290006200000000000000888")))
                                .andExpect(jsonPath("$.ownerName", is("Fatma Demir")))
                                .andExpect(jsonPath("$.balance", is(2000.00)));
        }

        @Test
        void shouldReturnNotFoundWhenAccountByIdDoesNotExist() throws Exception {
                mockMvc.perform(get("/api/v1/accounts/9999")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status", is(404)))
                                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND")));
        }

        @Test
        void shouldGetAccountByIbanSuccessfully() throws Exception {
                accountRepo.save(new AccountJpaEntity(null, testUserId, "TR290006200000000000000888", "Fatma Demir",
                                new BigDecimal("2000.00"), "TRY", true));

                mockMvc.perform(get("/api/v1/accounts/iban/TR290006200000000000000888")
                                .header("Authorization", "Bearer " + jwtToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.iban", is("TR290006200000000000000888")))
                                .andExpect(jsonPath("$.ownerName", is("Fatma Demir")));
        }

    @Test
    void shouldListAccountsSuccessfully() throws Exception {
        accountRepo.save(new AccountJpaEntity(null, testUserId, "TR290006200000000000000888", "Fatma Demir",
                new BigDecimal("2000.00"), "TRY", true));

        mockMvc.perform(get("/api/v1/accounts")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].iban", is("TR290006200000000000000888")))
                .andExpect(jsonPath("$[0].ownerName", is("Fatma Demir")));
    }

    @Test
    void shouldReturnNotFoundWhenAccountByIbanNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/iban/TR290006200000000000099999")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_FOUND")));
    }

    @Test
    void shouldReturnBadRequestWhenCreatingAccountWithNegativeBalance() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(
                testUserId,
                "TR290006200000000000000777",
                "Veli",
                new BigDecimal("-100.00"),
                Money.Currency.TRY);

        mockMvc.perform(post("/api/v1/accounts")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.initialBalance").exists());
    }

    @Test
    void shouldReturnForbiddenWhenAccessingOtherUsersAccount() throws Exception {
        UserJpaEntity otherUser = userRepository.save(
                new UserJpaEntity(null, "other_user", "pass", "ROLE_USER"));
        AccountJpaEntity otherAccount = accountRepo.save(
                new AccountJpaEntity(null, otherUser.getId(), "TR290006200000000000000555",
                        "Other Owner", new BigDecimal("500.00"), "TRY", true));

        mockMvc.perform(get("/api/v1/accounts/" + otherAccount.getId())
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    void shouldReturnForbiddenWhenAccessingOtherUsersAccountByIban() throws Exception {
        UserJpaEntity otherUser = userRepository.save(
                new UserJpaEntity(null, "other_user2", "pass", "ROLE_USER"));
        accountRepo.save(
                new AccountJpaEntity(null, otherUser.getId(), "TR290006200000000000000666",
                        "Other Owner", new BigDecimal("500.00"), "TRY", true));

        mockMvc.perform(get("/api/v1/accounts/iban/TR290006200000000000000666")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    void shouldReturnForbiddenWhenCreatingAccountForOtherUser() throws Exception {
        UserJpaEntity otherUser = userRepository.save(
                new UserJpaEntity(null, "other_user3", "pass", "ROLE_USER"));

        CreateAccountRequest request = new CreateAccountRequest(
                otherUser.getId(),
                "TR290006200000000000000444",
                "Victim",
                new BigDecimal("100.00"),
                Money.Currency.TRY);

        mockMvc.perform(post("/api/v1/accounts")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }
}
