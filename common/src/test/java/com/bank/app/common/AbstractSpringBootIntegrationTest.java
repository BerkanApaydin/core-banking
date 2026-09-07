package com.bank.app.common;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles({"test", "testcontainers"})
public abstract class AbstractSpringBootIntegrationTest {

    private static final String[] ACCOUNT_CACHES = {"accountAclInfo"};

    @Autowired
    private ObjectProvider<CacheManager> cacheManagers;

    /**
     * Integration fixtures wipe and reseed the same natural keys (IBANs) with new row IDs
     * before every test. TTL caches would otherwise serve entries from the previous test,
     * so they are cleared here — the same hygiene as cleaning tables.
     */
    @BeforeEach
    void clearAccountCaches() {
        cacheManagers.forEach(manager -> {
            for (String name : ACCOUNT_CACHES) {
                Cache cache = manager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            }
        });
    }

    static {
        var pg = TestDatabaseContainer.getInstance();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        var pg = TestDatabaseContainer.getInstance();
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.liquibase.enabled", () -> "false");
    }
}
