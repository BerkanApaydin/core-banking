package com.bank.app.account.adapter.in.web;

import com.bank.app.account.adapter.in.web.dto.CreateAccountWebRequest;
import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.in.GetAccountByIbanQuery;
import com.bank.app.account.application.port.in.GetAccountByIdQuery;
import com.bank.app.account.application.port.in.GetAccountsByUserQuery;
import com.bank.app.account.application.service.AccountAuthorizationService;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.common.domain.Currency;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * In-module slice for the account web adapter.
 *
 * <p>PIT reported every controller method as NO_COVERAGE because the MockMvc
 * tests live in the {@code app} module, outside this module's
 * {@code targetTests}. These standalone MockMvc tests kill the NullReturn
 * mutants locally and keep the adapter fast without a web slice.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock private CreateAccountUseCase createAccountUseCase;
    @Mock private GetAccountByIdQuery getAccountByIdQuery;
    @Mock private GetAccountByIbanQuery getAccountByIbanQuery;
    @Mock private GetAccountsByUserQuery getAccountsByUserQuery;
    @Mock private AccountAuthorizationService accountAuthorizationService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static AccountResponse response(long id) {
        return new AccountResponse(id, 100L, "TR770006200000000000000111",
                "Ahmet", new BigDecimal("1000.00"), "TRY", AccountStatus.ACTIVE, true, null);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountController(
                createAccountUseCase, getAccountByIdQuery, getAccountByIbanQuery,
                getAccountsByUserQuery, accountAuthorizationService)).build();
    }

    @Test
    void shouldCreateAccountWithLocation() throws Exception {
        when(accountAuthorizationService.getCurrentUserId()).thenReturn(100L);
        when(createAccountUseCase.execute(any())).thenReturn(response(10L));

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAccountWebRequest(
                                "Ahmet", new BigDecimal("1000.00"), Currency.TRY))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/accounts/10"))
                .andExpect(jsonPath("$.id").value(10));

        verify(createAccountUseCase).execute(any());
    }

    @Test
    void shouldListAccounts() throws Exception {
        when(getAccountsByUserQuery.execute(0, 20))
                .thenReturn(PageResponse.of(List.of(response(1L)), 0, 20, 1L));

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void shouldReturnAccountById() throws Exception {
        when(getAccountByIdQuery.execute(5L)).thenReturn(response(5L));

        mockMvc.perform(get("/accounts/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void shouldReturnAccountByIban() throws Exception {
        when(getAccountByIbanQuery.execute("TR770006200000000000000111")).thenReturn(response(6L));

        mockMvc.perform(get("/accounts/iban/TR770006200000000000000111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(6));
    }
}
