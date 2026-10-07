package com.bank.app.account.adapter.in.web;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.SuspendAccountUseCase;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.infrastructure.adapter.in.api.ApiVersionConfig;
import com.bank.app.infrastructure.adapter.in.handler.GlobalExceptionHandler;
import com.bank.app.infrastructure.adapter.in.handler.BusinessProblemHandler;
import com.bank.app.infrastructure.adapter.in.handler.SecurityProblemHandler;
import com.bank.app.infrastructure.adapter.in.handler.RequestProblemHandler;
import com.bank.app.infrastructure.adapter.in.handler.ProblemMessageResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountAdminController.class)
@Import({ ApiVersionConfig.class, GlobalExceptionHandler.class, BusinessProblemHandler.class, SecurityProblemHandler.class, RequestProblemHandler.class, ProblemMessageResolver.class })
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AccountAdminController Web MVC")
@SuppressWarnings("null")
class AccountAdminControllerWebMvcTest {

    private final MockMvc mockMvc;

    @Autowired
    AccountAdminControllerWebMvcTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @MockitoBean
    private SuspendAccountUseCase suspendAccountUseCase;

    @Nested
    @DisplayName("POST /api/v1/admin/accounts/{id}/suspend")
    class SuspendAccount {

        @Test
        @DisplayName("should return 200 with the suspended status")
        void shouldReturn200() throws Exception {
            when(suspendAccountUseCase.suspend(1L)).thenReturn(new AccountInfo(1L, 10L, "TRY", "SUSPENDED"));

            mockMvc.perform(post("/api/v1/admin/accounts/1/suspend"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(1L))
                    .andExpect(jsonPath("$.status").value("SUSPENDED"));
        }

        @Test
        @DisplayName("should return 404 for unknown accounts")
        void shouldReturn404() throws Exception {
            when(suspendAccountUseCase.suspend(anyLong()))
                    .thenThrow(new AccountNotFoundException(42L));

            mockMvc.perform(post("/api/v1/admin/accounts/42/suspend"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND_ID"));
        }

        @Test
        @DisplayName("should return 403 for non-admin callers")
        void shouldReturn403() throws Exception {
            when(suspendAccountUseCase.suspend(anyLong()))
                    .thenThrow(new AuthorizationException("Admin role required."));

            mockMvc.perform(post("/api/v1/admin/accounts/1/suspend"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }
}
