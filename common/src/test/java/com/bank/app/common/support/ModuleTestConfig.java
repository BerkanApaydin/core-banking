package com.bank.app.common.support;

import com.bank.app.common.adapter.in.config.JpaAuditingConfig;
import com.bank.app.common.adapter.in.handler.GlobalExceptionHandler;
import com.bank.app.common.adapter.in.security.JwtAuthenticationFilter;
import com.bank.app.common.adapter.in.security.SecurityConfig;
import com.bank.app.common.adapter.out.security.JwtTokenProvider;
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
public class ModuleTestConfig {
}
