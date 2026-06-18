package com.bank.app.user.infrastructure.web;

import com.bank.app.account.infrastructure.persistence.SpringDataAccountRepo;
import com.bank.app.transfer.infrastructure.persistence.SpringDataTransferRepo;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import com.bank.app.user.infrastructure.persistence.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SuppressWarnings("null")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpringDataAccountRepo accountRepo;

    @Autowired
    private SpringDataTransferRepo transferRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        transferRepo.deleteAll();
        accountRepo.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        AuthRequest request = new AuthRequest("new_user", "My_passw0rd");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        UserJpaEntity savedUser = userRepository.findByUsername("new_user")
                .orElseThrow(() -> new AssertionError("User was not saved"));

        assertEquals("new_user", savedUser.getUsername());
        assertEquals("ROLE_USER", savedUser.getRole());
        assertTrue(passwordEncoder.matches("My_passw0rd", savedUser.getPassword()));
    }

    @Test
    void shouldFailRegistrationWhenUsernameExists() throws Exception {
        UserJpaEntity existingUser = new UserJpaEntity();
        existingUser.setUsername("existing_user");
        existingUser.setPassword(passwordEncoder.encode("password"));
        existingUser.setRole("ROLE_USER");
        userRepository.save(existingUser);

        AuthRequest request = new AuthRequest("existing_user", "new_password");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Kullanıcı adı zaten kullanımda.")));
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        UserJpaEntity user = new UserJpaEntity();
        user.setUsername("login_user");
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole("ROLE_USER");
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
        user.setRole("ROLE_USER");
        userRepository.save(user);

        AuthRequest request = new AuthRequest("login_user", "wrong_password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("Bad credentials")));
    }
}
