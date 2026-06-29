package com.bank.app.account;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.bank.app", excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootApplication.class))
@EntityScan(basePackages = "com.bank.app")
@EnableJpaRepositories(basePackages = "com.bank.app")
@SuppressWarnings("null")
public class ModuleIntegrationTestConfig {
}
