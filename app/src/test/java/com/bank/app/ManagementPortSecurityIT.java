package com.bank.app;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F5 regression: on a separate management port the Spring Security filter
 * chain must still guard {@code /actuator/loggers/**}. The existing actuator
 * test runs on the application port, so a matcher or management-context
 * change could silently open runtime log-level changes without any test
 * noticing.
 */
@SpringBootTest(classes = BankApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.endpoints.web.exposure.include=health,prometheus,loggers"
        })
@DisplayName("Management port security")
class ManagementPortSecurityIT extends AbstractSpringBootIntegrationTest {

    private final TestRestTemplate restTemplate;
    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtPort jwtPort;

    @LocalManagementPort
    private int managementPort;

    private String adminToken;
    private String userToken;

    @Autowired
    ManagementPortSecurityIT(TestRestTemplate restTemplate, UserJpaRepository userRepository,
            PasswordEncoder passwordEncoder, JwtPort jwtPort,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtPort = jwtPort;
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UserJpaEntity admin = new UserJpaEntity();
        admin.setUsername("mgmt_admin_" + suffix);
        admin.setPassword(passwordEncoder.encode("Admin_pass12"));
        admin.setRole(Role.ROLE_ADMIN);
        admin.setMfaEnabled(true);
        admin = userRepository.save(admin);
        adminToken = jwtPort.generateToken(admin.getId(), admin.getUsername(), "ROLE_ADMIN");

        UserJpaEntity user = new UserJpaEntity();
        user.setUsername("mgmt_user_" + suffix);
        user.setPassword(passwordEncoder.encode("User_pass12"));
        user.setRole(Role.ROLE_USER);
        user = userRepository.save(user);
        userToken = jwtPort.generateToken(user.getId(), user.getUsername(), "ROLE_USER");
    }

    private String loggersUrl() {
        return "http://localhost:" + managementPort + "/actuator/loggers";
    }

    private ResponseEntity<String> getLoggers(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(loggersUrl(), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("anonymous is rejected with 401")
    void anonymousIsRejected() {
        assertThat(getLoggers(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("authenticated non-admin is rejected with 403")
    void nonAdminIsForbidden() {
        assertThat(getLoggers(userToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("admin reaches the loggers endpoint")
    void adminIsAllowed() {
        ResponseEntity<String> response = getLoggers(adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("loggers");
    }
}
