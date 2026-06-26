package com.bank.app.user;

import com.bank.app.common.adapter.in.api.ApiVersionConfig;
import com.bank.app.common.adapter.in.config.JpaAuditingConfig;
import com.bank.app.common.adapter.in.handler.GlobalExceptionHandler;
import com.bank.app.common.adapter.in.security.JwtAuthenticationFilter;
import com.bank.app.common.adapter.in.web.ClientIpResolver;
import com.bank.app.common.adapter.out.security.JwtTokenProvider;
import com.bank.app.common.adapter.out.security.TokenBlacklistAdapter;
import com.bank.app.common.adapter.in.security.SecurityConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    SecurityConfig.class,
    JwtTokenProvider.class,
    TokenBlacklistAdapter.class,
    ClientIpResolver.class,
    JwtAuthenticationFilter.class,
    JpaAuditingConfig.class,
    GlobalExceptionHandler.class,
    ApiVersionConfig.class
})
@SuppressWarnings("null")
public class ModuleIntegrationTestConfig {
}
