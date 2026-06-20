package com.bank.app.user;

import com.bank.app.common.config.JpaAuditingConfig;
import com.bank.app.common.handler.GlobalExceptionHandler;
import com.bank.app.common.security.JwtAuthenticationFilter;
import com.bank.app.common.security.JwtTokenProvider;
import com.bank.app.common.security.SecurityConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    SecurityConfig.class,
    JwtTokenProvider.class,
    JwtAuthenticationFilter.class,
    JpaAuditingConfig.class,
    GlobalExceptionHandler.class
})
public class ModuleIntegrationTestConfig {
}
