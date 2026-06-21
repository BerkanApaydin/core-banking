package com.bank.app.account.infrastructure.web;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.in.GetAccountQuery;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.api.ApiVersionConfig;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import(ApiVersionConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreateAccountUseCase createAccountPort;

    @MockitoBean
    private GetAccountQuery getAccountPort;

    @Test
    void shouldCreateAccountAndReturn201() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(
                100L, "TR290006200000000000000123", "Ali Veli",
                new BigDecimal("500.00"), Currency.TRY);
        AccountResponse response = new AccountResponse(1L, 100L, "TR290006200000000000000123",
                "Ali Veli", new BigDecimal("500.00"), "TRY", AccountStatus.ACTIVE, true);

        when(createAccountPort.execute(any(CreateAccountRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.iban").value("TR290006200000000000000123"))
                .andExpect(jsonPath("$.ownerName").value("Ali Veli"))
                .andExpect(jsonPath("$.balance").value(500.00))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturn400WhenCreateAccountRequestIsInvalid() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(
                null, "", "", new BigDecimal("-1.00"), null);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldListAllAccounts() throws Exception {
        AccountResponse a1 = new AccountResponse(1L, 100L, "TR290006200000000000000111",
                "Ali", new BigDecimal("1000.00"), "TRY", AccountStatus.ACTIVE, true);
        when(getAccountPort.getAll()).thenReturn(List.of(a1));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].iban").value("TR290006200000000000000111"))
                .andExpect(jsonPath("$[0].ownerName").value("Ali"));
    }

    @Test
    void shouldGetAccountById() throws Exception {
        AccountResponse response = new AccountResponse(1L, 100L, "TR290006200000000000000111",
                "Ali", new BigDecimal("1000.00"), "TRY", AccountStatus.ACTIVE, true);
        when(getAccountPort.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.iban").value("TR290006200000000000000111"));
    }

    @Test
    void shouldGetAccountByIban() throws Exception {
        AccountResponse response = new AccountResponse(1L, 100L, "TR290006200000000000000111",
                "Ali", new BigDecimal("1000.00"), "TRY", AccountStatus.ACTIVE, true);
        when(getAccountPort.getByIban("TR290006200000000000000111")).thenReturn(response);

        mockMvc.perform(get("/api/v1/accounts/iban/TR290006200000000000000111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value("TR290006200000000000000111"));
    }
}
