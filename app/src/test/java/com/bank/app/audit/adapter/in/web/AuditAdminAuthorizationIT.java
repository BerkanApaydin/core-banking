package com.bank.app.audit.adapter.in.web;

import com.bank.app.BankApplication;

import com.bank.app.user.domain.Role;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.bank.app.user.application.port.out.JwtPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full-chain proof: version prefix, security filter chain, application-layer
// role check and the real query wiring. ROLE_ADMIN users cannot be created
// through registration (always ROLE_USER); ops provisions the first admin
// directly, mirrored here by repository insert.
@AutoConfigureMockMvc
@Transactional
@SpringBootTest(classes = BankApplication.class)
@SuppressWarnings("null")
class AuditAdminAuthorizationIT extends AbstractSpringBootIntegrationTest {

    private final MockMvc mockMvc;

    private final UserJpaRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtPort jwtPort;

    @Autowired
    AuditAdminAuthorizationIT(MockMvc mockMvc, UserJpaRepository userRepository,
            PasswordEncoder passwordEncoder, JwtPort jwtPort,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtPort = jwtPort;
    }

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        UserJpaEntity admin = new UserJpaEntity();
        admin.setUsername("audit_admin");
        admin.setPassword(passwordEncoder.encode("Admin_pass12"));
        admin.setRole(Role.ROLE_ADMIN);
        admin = userRepository.save(admin);
        adminToken = jwtPort.generateToken(admin.getId(), admin.getUsername(), "ROLE_ADMIN");

        UserJpaEntity user = new UserJpaEntity();
        user.setUsername("audit_user");
        user.setPassword(passwordEncoder.encode("User_pass12"));
        user.setRole(Role.ROLE_USER);
        user = userRepository.save(user);
        userToken = jwtPort.generateToken(user.getId(), user.getUsername(), "ROLE_USER");
    }

    @Test
    @DisplayName("admin reads audit logs")
    void shouldAllowAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("non-admin gets 403 with ACCESS_DENIED")
    void shouldForbidNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    @DisplayName("anonymous gets 401")
    void shouldRejectAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("out-of-range limit gets 400")
    void shouldValidateLimit() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());
    }
}
