package com.bank.app.account.infrastructure.web;

import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.account.infrastructure.persistence.SpringDataAccountRepo;
import com.bank.app.common.domain.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

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
class AccountControllerIntegrationTest {

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

    private Long testUserId;

    @BeforeEach
    void setUp() {
        Locale.setDefault(Locale.of("tr", "TR"));
        transferRepo.deleteAll();
        accountRepo.deleteAll();
        userRepository.deleteAll();

        com.bank.app.user.infrastructure.persistence.UserJpaEntity u = userRepository.save(
            new com.bank.app.user.infrastructure.persistence.UserJpaEntity(null, "test_user", "pass", "ROLE_USER")
        );
        testUserId = u.getId();
        when(securityUtils.getCurrentUserId()).thenReturn(Optional.of(testUserId));
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message",
                        is("Bu IBAN ile kayıtlı bir hesap zaten mevcut: TR290006200000000000000999")));
    }

    @Test
    void shouldGetAccountByIdSuccessfully() throws Exception {
        AccountJpaEntity saved = accountRepo.save(new AccountJpaEntity(null, testUserId, "TR290006200000000000000888",
                "Fatma Demir", new BigDecimal("2000.00"), "TRY", true));

        mockMvc.perform(get("/api/v1/accounts/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(saved.getId().intValue())))
                .andExpect(jsonPath("$.iban", is("TR290006200000000000000888")))
                .andExpect(jsonPath("$.ownerName", is("Fatma Demir")))
                .andExpect(jsonPath("$.balance", is(2000.00)));
    }

    @Test
    void shouldReturnNotFoundWhenAccountByIdDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Hesap bulunamadı. ID: 9999")));
    }

    @Test
    void shouldGetAccountByIbanSuccessfully() throws Exception {
        accountRepo.save(new AccountJpaEntity(null, testUserId, "TR290006200000000000000888", "Fatma Demir",
                new BigDecimal("2000.00"), "TRY", true));

        mockMvc.perform(get("/api/v1/accounts/iban/TR290006200000000000000888"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban", is("TR290006200000000000000888")))
                .andExpect(jsonPath("$.ownerName", is("Fatma Demir")));
    }

    @Test
    void shouldListAccountsSuccessfully() throws Exception {
        accountRepo.save(new AccountJpaEntity(null, testUserId, "TR290006200000000000000888", "Fatma Demir",
                new BigDecimal("2000.00"), "TRY", true));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].iban", is("TR290006200000000000000888")))
                .andExpect(jsonPath("$[0].ownerName", is("Fatma Demir")));
    }
}
