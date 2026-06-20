package com.bank.app.transfer;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.bank.app.common.config.JpaAuditingConfig;
import com.bank.app.common.handler.GlobalExceptionHandler;
import com.bank.app.common.security.JwtAuthenticationFilter;
import com.bank.app.common.security.JwtTokenProvider;
import com.bank.app.common.security.SecurityConfig;
import com.bank.app.transfer.domain.TransferDomainService;
import org.springframework.context.annotation.Bean;

@Configuration
@ComponentScan(basePackages = "com.bank.app",
    excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootApplication.class))
@EntityScan(basePackages = "com.bank.app")
@EnableJpaRepositories(basePackages = "com.bank.app")
@Import({
    SecurityConfig.class,
    JwtTokenProvider.class,
    JwtAuthenticationFilter.class,
    JpaAuditingConfig.class,
    GlobalExceptionHandler.class
})
public class ModuleIntegrationTestConfig {

    @Bean
    TransferDomainService transferDomainService() {
        return new TransferDomainService();
    }
}
