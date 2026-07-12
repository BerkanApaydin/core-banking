package com.bank.app.user;

import com.bank.app.infrastructure.adapter.in.api.ApiVersionConfig;
import com.bank.app.infrastructure.adapter.in.web.ClientIpResolver;
import com.bank.app.infrastructure.adapter.out.security.TokenBlacklistAdapter;
import com.bank.app.infrastructure.adapter.in.security.SecurityConfig;
import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.infrastructure.adapter.in.security.JwtAuthenticationFilter;
import com.bank.app.infrastructure.adapter.in.config.JpaAuditingConfig;
import com.bank.app.infrastructure.adapter.in.handler.GlobalExceptionHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
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
public class UserModuleIntegrationTestConfig {
}
