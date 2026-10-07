package com.bank.app.user.adapter.in.web;

import com.bank.app.user.domain.Role;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.infrastructure.adapter.out.security.CaffeineLoginAttemptAdapter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@SpringBootTest(classes = com.bank.app.BankApplication.class)
@SuppressWarnings("null")
class AuthControllerIntegrationTest extends AbstractSpringBootIntegrationTest {

    private final MockMvc mockMvc;

    private final UserJpaRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper;

    private final CaffeineLoginAttemptAdapter CaffeineLoginAttemptAdapter;

    @Autowired
    AuthControllerIntegrationTest(MockMvc mockMvc, UserJpaRepository userRepository,
            PasswordEncoder passwordEncoder, ObjectMapper objectMapper,
            CaffeineLoginAttemptAdapter CaffeineLoginAttemptAdapter,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.CaffeineLoginAttemptAdapter = CaffeineLoginAttemptAdapter;
    }

    @BeforeEach
    void setUp() {
        CaffeineLoginAttemptAdapter.reset("127.0.0.1");
        CaffeineLoginAttemptAdapter.reset("10.0.0.99");
    }

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        AuthRequest request = new AuthRequest("new_user", "My_passw0rd12");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        UserJpaEntity savedUser = userRepository.findByUsername("new_user")
                .orElseThrow(() -> new AssertionError("User was not saved"));

        assertEquals("new_user", savedUser.getUsername());
        assertEquals(Role.ROLE_USER, savedUser.getRole());
        assertTrue(passwordEncoder.matches("My_passw0rd12", savedUser.getPassword()));
    }

    @Test
    void shouldFailRegistrationWhenUsernameExists() throws Exception {
        UserJpaEntity existingUser = new UserJpaEntity();
        existingUser.setUsername("existing_user");
        existingUser.setPassword(passwordEncoder.encode("password"));
        existingUser.setRole(Role.ROLE_USER);
        userRepository.save(existingUser);

        AuthRequest request = new AuthRequest("existing_user", "NewPassword12");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.code", is("USERNAME_TAKEN")))
                .andExpect(jsonPath("$.message", is("Username is already in use: existing_user")));
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        UserJpaEntity user = new UserJpaEntity();
        user.setUsername("login_user");
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(Role.ROLE_USER);
        userRepository.save(user);

        AuthRequest request = new AuthRequest("login_user", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.userId", is(user.getId().intValue())))
                .andExpect(jsonPath("$.username", is("login_user")));
    }

    @Test
    void shouldFailLoginWithIncorrectPassword() throws Exception {
        UserJpaEntity user = new UserJpaEntity();
        user.setUsername("login_user");
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(Role.ROLE_USER);
        userRepository.save(user);

        AuthRequest request = new AuthRequest("login_user", "wrong_password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_FAILED")));
    }

    @Test
    void shouldLoginWithXForwardedForSingleIp() throws Exception {
        createUser("xff_single");
        AuthRequest request = new AuthRequest("xff_single", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldLoginWithXForwardedForMultipleIps() throws Exception {
        createUser("xff_multi");
        AuthRequest request = new AuthRequest("xff_multi", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldResolveIpFromRemoteAddrWhenXForwardedForIsEmpty() throws Exception {
        createUser("xff_empty");
        AuthRequest request = new AuthRequest("xff_empty", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldResolveIpFromRemoteAddrWhenXForwardedForIsUnknown() throws Exception {
        createUser("xff_unknown");
        AuthRequest request = new AuthRequest("xff_unknown", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private UserJpaEntity createUser(String username) {
        UserJpaEntity user = new UserJpaEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(Role.ROLE_USER);
        return userRepository.save(user);
    }

    @Test
    void shouldFailRegistrationWhenPasswordDoesNotMeetPolicy() throws Exception {
        AuthRequest request = new AuthRequest("weak_user", "short");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void shouldReturn400WhenUsernameIsBlank() throws Exception {
        String body = "{\"username\": \"\", \"password\": \"ValidPass1\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenPasswordIsBlank() throws Exception {
        String body = "{\"username\": \"validuser\", \"password\": \"\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenEmailIsInvalid() throws Exception {
        AuthRequest request = new AuthRequest("validuser", "ValidPass1", "not-an-email", "5551234567");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenRequestBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenLoginWithBlankUsername() throws Exception {
        String body = "{\"username\": \"\", \"password\": \"password\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailLoginWithNonExistentUser() throws Exception {
        AuthRequest request = new AuthRequest("nonexistent", "SomePassw0rd!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_FAILED")));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        EventPublisherPort eventPublisherPort() {
            return event -> {};
        }

        @Bean
        DomainEventPublisherService domainEventPublisherService(EventPublisherPort port) {
            return new DomainEventPublisherService(port);
        }
    }

    @Test
    void shouldBlockLoginAfterTooManyFailedAttempts() throws Exception {
        UserJpaEntity user = new UserJpaEntity();
        user.setUsername("blocked_user");
        user.setPassword(passwordEncoder.encode("Mypassw0rd!"));
        user.setRole(Role.ROLE_USER);
        userRepository.save(user);

        AuthRequest request = new AuthRequest("blocked_user", "wrongpass");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(req -> {
                                req.setRemoteAddr("10.0.0.99");
                                return req;
                            }))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.99");
                            return req;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", is("TOO_MANY_FAILED_LOGIN_ATTEMPTS")));
    }
}



