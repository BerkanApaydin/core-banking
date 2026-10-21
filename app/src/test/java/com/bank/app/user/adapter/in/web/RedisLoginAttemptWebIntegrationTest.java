package com.bank.app.user.adapter.in.web;

import com.bank.app.BankApplication;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.bank.app.user.domain.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R-I01 end-to-end: the username is user input that reaches Redis SCAN as a
 * glob pattern. A successful login for a legacy account literally named "*"
 * must clear only that account's counters, not every other account's pair
 * buckets. The adapter unit test pins the escaping; this test pins that the
 * production login path actually reaches it.
 */
@AutoConfigureMockMvc
@SpringBootTest(classes = BankApplication.class,
        properties = "app.security.failed-login.backend=redis")
@Testcontainers
@DisplayName("R-I01: glob username login keeps other counters")
@SuppressWarnings("null")
class RedisLoginAttemptWebIntegrationTest extends AbstractSpringBootIntegrationTest {

    private static final String VICTIM_PAIR_KEY = "login_attempt:account_ip:victim|10.0.0.1";
    private static final String VICTIM_ACCOUNT_KEY = "login_attempt:user:victim";
    private static final String EXTERNAL_REDIS_HOST = System.getenv("TEST_REDIS_HOST");
    private static final String EXTERNAL_REDIS_PORT = System.getenv("TEST_REDIS_PORT");
    private static final boolean KEEP_KEYS = Boolean.parseBoolean(System.getenv("TEST_REDIS_KEEP_KEYS"));

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        if (EXTERNAL_REDIS_HOST != null && EXTERNAL_REDIS_PORT != null) {
            registry.add("spring.data.redis.host", () -> EXTERNAL_REDIS_HOST);
            registry.add("spring.data.redis.port", () -> EXTERNAL_REDIS_PORT);
            return;
        }
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    private final MockMvc mockMvc;
    private final UserJpaRepository users;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    @Autowired
    RedisLoginAttemptWebIntegrationTest(MockMvc mockMvc, UserJpaRepository users,
            PasswordEncoder passwordEncoder, StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper, JdbcTemplate jdbc,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() {
        cleanUp();
        UserJpaEntity globUser = new UserJpaEntity();
        globUser.setUsername("*");
        globUser.setPassword(passwordEncoder.encode("Glob_pass12"));
        globUser.setRole(Role.ROLE_USER);
        users.saveAndFlush(globUser);

        redisTemplate.opsForValue().set(VICTIM_PAIR_KEY, "2", Duration.ofMinutes(15));
        redisTemplate.opsForValue().set(VICTIM_ACCOUNT_KEY, "2", Duration.ofMinutes(15));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        if (!KEEP_KEYS) {
            redisTemplate.delete(List.of(VICTIM_PAIR_KEY, VICTIM_ACCOUNT_KEY));
        }
    }

    @Test
    void successfulGlobUsernameLoginDoesNotClearOtherAccountsCounters() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "*", "password", "Glob_pass12"))))
                .andExpect(status().isOk());

        assertThat(redisTemplate.opsForValue().get(VICTIM_PAIR_KEY)).isEqualTo("2");
        assertThat(redisTemplate.opsForValue().get(VICTIM_ACCOUNT_KEY)).isEqualTo("2");
    }

    private void cleanUp() {
        cleanDatabase();
        redisTemplate.delete(List.of(VICTIM_PAIR_KEY, VICTIM_ACCOUNT_KEY));
    }

    private void cleanDatabase() {
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN "
                + "(SELECT id FROM users WHERE username = ?)", "*");
        jdbc.update("DELETE FROM users WHERE username = ?", "*");
    }
}
