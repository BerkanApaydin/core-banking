package com.bank.app.user.adapter.in.web;

import com.bank.app.BankApplication;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.adapter.in.security.BrowserSessionCookies;
import com.bank.app.infrastructure.adapter.out.security.JwtTokenProvider;
import com.bank.app.user.adapter.in.web.dto.AuthWebRequest;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.bank.app.user.domain.Role;
import com.bank.testsupport.clock.ClockConfiguration;
import com.bank.testsupport.clock.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R-U02/R-U03 end-to-end contract for the production (secure) browser
 * session: host-prefixed cookies, refresh after the 15-minute access token
 * has expired with a reissued CSRF pair, and logout clearing the same
 * prefixed cookies. The mutable clock is injected into both the JWT provider
 * and the application ClockProviderPort so refresh sees one advanced timeline.
 */
@AutoConfigureMockMvc
@SpringBootTest(classes = BankApplication.class,
        properties = "app.security.browser-session.secure=true")
@Import(ClockConfiguration.class)
@SuppressWarnings("null")
@DisplayName("Secure browser session clock expiry")
class BrowserSessionClockExpiryIntegrationTest extends AbstractSpringBootIntegrationTest {

    private final MockMvc mockMvc;
    private final UserJpaRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final MutableClock clock;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    private String username;

    @Autowired
    BrowserSessionClockExpiryIntegrationTest(MockMvc mockMvc, UserJpaRepository users,
            PasswordEncoder passwordEncoder, ObjectMapper objectMapper,
            JwtTokenProvider jwtTokenProvider, MutableClock clock,
            EntityManager entityManager, PlatformTransactionManager transactionManager,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.clock = clock;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        clock.reset(Instant.now());
        jwtTokenProvider.setClockForTests(clock);
        username = "clock_" + UUID.randomUUID().toString().replace("-", "");
        UserJpaEntity user = new UserJpaEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("BrowserPass12"));
        user.setRole(Role.ROLE_USER);
        users.saveAndFlush(user);
    }

    @AfterEach
    void tearDown() {
        jwtTokenProvider.setClockForTests(null);
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM refresh_tokens WHERE user_id IN "
                            + "(SELECT id FROM users WHERE username = :username)")
                    .setParameter("username", username)
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users WHERE username = :username")
                    .setParameter("username", username)
                    .executeUpdate();
        });
    }

    @Test
    @DisplayName("refresh after access expiry rotates secure cookies and logout clears them")
    void refreshAfterAccessExpiryRotatesSecureCookiesAndLogoutClearsThem() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/browser/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthWebRequest(username, "BrowserPass12"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();

        List<String> loginCookies = login.getResponse().getHeaders("Set-Cookie");
        assertThat(loginCookies).hasSize(3);
        assertThat(loginCookies).allMatch(header -> header.contains("Secure"));
        Cookie session = fromSetCookie(cookieHeader(loginCookies, "__Host-BANK_SESSION="));
        Cookie refresh = fromSetCookie(cookieHeader(loginCookies, "__Secure-BANK_REFRESH="));
        Cookie csrf = fromSetCookie(cookieHeader(loginCookies, "__Host-BANK_CSRF="));

        mockMvc.perform(get("/api/v1/accounts/capabilities").cookie(session, csrf))
                .andExpect(status().isOk());

        // The access token is 15 minutes plus the configured 30-second
        // verification skew; move beyond both but keep the 7-day refresh
        // token valid.
        clock.advance(Duration.ofMinutes(15).plusSeconds(31));

        mockMvc.perform(get("/api/v1/accounts/capabilities").cookie(session, csrf))
                .andExpect(status().isUnauthorized());

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/browser/refresh")
                        .cookie(refresh, csrf)
                        .header(BrowserSessionCookies.CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();

        List<String> rotatedCookies = rotated.getResponse().getHeaders("Set-Cookie");
        assertThat(rotatedCookies).hasSize(3);
        String rotatedSessionHeader = cookieHeader(rotatedCookies, "__Host-BANK_SESSION=");
        String rotatedRefreshHeader = cookieHeader(rotatedCookies, "__Secure-BANK_REFRESH=");
        String rotatedCsrfHeader = cookieHeader(rotatedCookies, "__Host-BANK_CSRF=");
        assertThat(rotatedSessionHeader).contains("Max-Age=900", "Secure", "HttpOnly");
        assertThat(rotatedRefreshHeader)
                .contains("Max-Age=604800", "Secure", "HttpOnly", "Path=/api/v1/auth/browser");
        assertThat(rotatedCsrfHeader)
                .contains("Max-Age=604800", "Secure", "Path=/")
                .doesNotContain("HttpOnly");

        Cookie rotatedSession = fromSetCookie(rotatedSessionHeader);
        Cookie rotatedRefresh = fromSetCookie(rotatedRefreshHeader);
        Cookie rotatedCsrf = fromSetCookie(rotatedCsrfHeader);
        assertThat(rotatedCsrf.getValue()).isNotEqualTo(csrf.getValue());

        mockMvc.perform(get("/api/v1/accounts/capabilities")
                        .cookie(rotatedSession, rotatedCsrf))
                .andExpect(status().isOk());

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/browser/logout")
                        .cookie(rotatedSession, rotatedRefresh, rotatedCsrf)
                        .header(BrowserSessionCookies.CSRF_HEADER, rotatedCsrf.getValue()))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> clearedCookies = logout.getResponse().getHeaders("Set-Cookie");
        assertThat(clearedCookies).hasSize(3);
        assertThat(cookieHeader(clearedCookies, "__Host-BANK_SESSION="))
                .contains("Max-Age=0", "Secure", "HttpOnly", "Path=/");
        assertThat(cookieHeader(clearedCookies, "__Secure-BANK_REFRESH="))
                .contains("Max-Age=0", "Secure", "HttpOnly", "Path=/api/v1/auth/browser");
        assertThat(cookieHeader(clearedCookies, "__Host-BANK_CSRF="))
                .contains("Max-Age=0", "Secure", "Path=/")
                .doesNotContain("HttpOnly");

        mockMvc.perform(post("/api/v1/auth/browser/refresh")
                        .cookie(rotatedRefresh, rotatedCsrf)
                        .header(BrowserSessionCookies.CSRF_HEADER, rotatedCsrf.getValue()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/browser/session")
                        .cookie(rotatedSession, rotatedCsrf))
                .andExpect(status().isUnauthorized());
    }

    private static String cookieHeader(List<String> headers, String prefix) {
        return headers.stream().filter(header -> header.startsWith(prefix))
                .findFirst().orElseThrow();
    }

    private static Cookie fromSetCookie(String header) {
        String[] parts = header.substring(0, header.indexOf(';')).split("=", 2);
        return new Cookie(parts[0], parts[1]);
    }

}
