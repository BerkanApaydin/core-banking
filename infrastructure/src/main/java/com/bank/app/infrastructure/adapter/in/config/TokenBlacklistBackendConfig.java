package com.bank.app.infrastructure.adapter.in.config;

import com.bank.app.infrastructure.adapter.out.security.DatabaseTokenBlacklistAdapter;
import com.bank.app.infrastructure.adapter.out.security.HybridTokenBlacklistAdapter;
import com.bank.app.infrastructure.adapter.out.security.RedisTokenBlacklistAdapter;
import com.bank.app.infrastructure.adapter.out.security.TokenBlacklistAdapter;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TokenBlacklistBackendConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.security.token-blacklist.backend", havingValue = "database")
    TokenBlacklistPort databaseTokenBlacklist(JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager, JwtPort jwtPort) {
        return new DatabaseTokenBlacklistAdapter(jdbc, transactionManager, jwtPort);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.security.token-blacklist.backend", havingValue = "hybrid")
    TokenBlacklistPort hybridTokenBlacklist(JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager, JwtPort jwtPort,
            StringRedisTemplate redisTemplate, TokenBlacklistAdapter local) {
        return new HybridTokenBlacklistAdapter(
                new DatabaseTokenBlacklistAdapter(jdbc, transactionManager, jwtPort),
                new RedisTokenBlacklistAdapter(redisTemplate), local);
    }
}
