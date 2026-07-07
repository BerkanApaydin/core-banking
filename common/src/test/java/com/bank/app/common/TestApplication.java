package com.bank.app.common;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SuppressWarnings("null")
@SpringBootApplication(exclude = RedisAutoConfiguration.class)
@ComponentScan("com.bank.app")
@EnableJpaRepositories(basePackages = "com.bank.app")
@EntityScan(basePackages = "com.bank.app")
@ConfigurationPropertiesScan("com.bank.app")
public class TestApplication {
}
