package com.bank.app.user;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.infrastructure.adapter.in.api.ApiVersionConfig;
import com.bank.app.infrastructure.adapter.in.web.ClientIpResolver;
import com.bank.app.infrastructure.adapter.out.security.TokenBlacklistAdapter;
import com.bank.app.infrastructure.adapter.in.security.SecurityConfig;
import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.infrastructure.adapter.in.security.JwtAuthenticationFilter;
import com.bank.app.infrastructure.adapter.in.config.JpaAuditingConfig;
import com.bank.app.infrastructure.adapter.in.handler.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@Configuration
@Import({
    SecurityConfig.class,
    JwtTokenProvider.class,
    JwtAuthenticationFilter.class,
    JpaAuditingConfig.class,
    GlobalExceptionHandler.class,
    TokenBlacklistAdapter.class,
    ClientIpResolver.class,
    ApiVersionConfig.class
})
@SuppressWarnings("null")
public class ModuleIntegrationTestConfig {

    @Bean
    @Primary
    public IdempotencyPort idempotencyPort() {
        return mock(IdempotencyPort.class);
    }
}
