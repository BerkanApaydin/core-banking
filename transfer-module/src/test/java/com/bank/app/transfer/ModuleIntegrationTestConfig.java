package com.bank.app.transfer;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.bank.app.common.adapter.in.config.JpaAuditingConfig;
import com.bank.app.common.adapter.in.handler.GlobalExceptionHandler;
import com.bank.app.common.adapter.in.security.JwtAuthenticationFilter;
import com.bank.app.common.adapter.out.security.JwtTokenProvider;
import com.bank.app.common.adapter.in.security.SecurityConfig;
@Configuration
@ComponentScan(basePackages = "com.bank.app", excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootApplication.class))
@EntityScan(basePackages = "com.bank.app")
@EnableJpaRepositories(basePackages = "com.bank.app")
@Import({
        SecurityConfig.class,
        JwtTokenProvider.class,
        JwtAuthenticationFilter.class,
        JpaAuditingConfig.class,
        GlobalExceptionHandler.class
})
@SuppressWarnings("null")
public class ModuleIntegrationTestConfig {
}
