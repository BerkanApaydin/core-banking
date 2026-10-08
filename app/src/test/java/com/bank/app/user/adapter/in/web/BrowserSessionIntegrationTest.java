package com.bank.app.user.adapter.in.web;

import com.bank.app.BankApplication;

import com.bank.app.user.domain.Role;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.adapter.in.security.BrowserSessionCookies;
import com.bank.app.common.domain.Iban;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.bank.app.user.adapter.in.web.dto.AuthWebRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BankApplication.class)
@AutoConfigureMockMvc
@Transactional
@SuppressWarnings("null")
class BrowserSessionIntegrationTest extends AbstractSpringBootIntegrationTest {
    private final MockMvc mockMvc;
    private final UserJpaRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Autowired
    BrowserSessionIntegrationTest(MockMvc mockMvc, UserJpaRepository users,
            PasswordEncoder passwordEncoder, ObjectMapper objectMapper,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.mockMvc = mockMvc;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Test
    void anonymousBrowserSessionMatchesHealthSmokeContract() throws Exception {
        mockMvc.perform(get("/api/v1/auth/browser/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void expiredCookieDoesNotBlockPublicUiButProtectedApiRemainsUnauthorized() throws Exception {
        Cookie expiredSession = new Cookie("BANK_SESSION", "expired-token");

        mockMvc.perform(get("/").cookie(expiredSession))
                .andExpect(status().isOk());
        mockMvc.perform(get("/boot.js").cookie(expiredSession))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/browser/session").cookie(expiredSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cookieSessionRequiresCsrfAndLogoutRevokesIt() throws Exception {
        mockMvc.perform(get("/boot.js")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/accounts/capabilities")).andExpect(status().isUnauthorized());
        String username = "browser_" + UUID.randomUUID().toString().replace("-", "");
        UserJpaEntity user = new UserJpaEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("BrowserPass12"));
        user.setRole(Role.ROLE_USER);
        users.saveAndFlush(user);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/browser/login")
                        .cookie(new Cookie("BANK_SESSION", "expired-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthWebRequest(username, "BrowserPass12"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        List<String> setCookies = login.getResponse().getHeaders("Set-Cookie");
        assertEquals(3, setCookies.size());
        assertEquals("no-store", login.getResponse().getHeader("Cache-Control"));
        String sessionHeader = setCookies.stream().filter(s -> s.startsWith("BANK_SESSION=")).findFirst().orElseThrow();
        String csrfHeader = setCookies.stream().filter(s -> s.startsWith("BANK_CSRF=")).findFirst().orElseThrow();
        String refreshHeader = setCookies.stream().filter(s -> s.startsWith("BANK_REFRESH=")).findFirst().orElseThrow();
        assertTrue(sessionHeader.contains("HttpOnly"));
        assertTrue(sessionHeader.contains("SameSite=Strict"));
        assertFalse(csrfHeader.contains("HttpOnly"));
        // Long-lived refresh token: HttpOnly and scoped to its endpoint only.
        assertTrue(refreshHeader.contains("HttpOnly"));
        assertTrue(refreshHeader.contains("Path=/api/v1/auth/browser/refresh"));
        Cookie session = fromSetCookie(sessionHeader);
        Cookie csrf = fromSetCookie(csrfHeader);
        Cookie refresh = fromSetCookie(refreshHeader);

        mockMvc.perform(get("/api/v1/auth/browser/session").cookie(session, csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
        mockMvc.perform(get("/api/v1/accounts/capabilities").cookie(session, csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialFundingEnabled").value(true));
        MvcResult created = mockMvc.perform(post("/api/v1/accounts").cookie(session, csrf)
                        .header(BrowserSessionCookies.CSRF_HEADER, csrf.getValue())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ownerName", "Browser Test", "initialBalance", 100,
                                "currency", "TRY"))))
                .andExpect(status().isCreated())
                .andReturn();
        String iban = objectMapper.readTree(created.getResponse().getContentAsString()).get("iban").asText();
        assertTrue(new Iban(iban).hasValidChecksum());
        mockMvc.perform(get("/api/v1/accounts").cookie(session, csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].iban").value(iban));
        mockMvc.perform(post("/api/v1/auth/browser/logout").cookie(session, csrf))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/browser/logout").cookie(session, csrf)
                        .header(BrowserSessionCookies.CSRF_HEADER, "x".repeat(43)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/browser/logout").cookie(session, csrf)
                        .header(BrowserSessionCookies.CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/browser/session").cookie(session, csrf))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cookieRefreshRotatesSessionAndDetectsReuse() throws Exception {
        String username = "refresh_" + UUID.randomUUID().toString().replace("-", "");
        UserJpaEntity user = new UserJpaEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("BrowserPass12"));
        user.setRole(Role.ROLE_USER);
        users.saveAndFlush(user);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/browser/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthWebRequest(username, "BrowserPass12"))))
                .andExpect(status().isOk())
                .andReturn();

        List<String> setCookies = login.getResponse().getHeaders("Set-Cookie");
        Cookie csrf = fromSetCookie(setCookies.stream()
                .filter(s -> s.startsWith("BANK_CSRF=")).findFirst().orElseThrow());
        Cookie firstRefresh = fromSetCookie(setCookies.stream()
                .filter(s -> s.startsWith("BANK_REFRESH=")).findFirst().orElseThrow());

        // Rotate with CSRF header: fresh session + fresh refresh cookie.
        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/browser/refresh")
                        .cookie(firstRefresh, csrf)
                        .header(BrowserSessionCookies.CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();
        List<String> rotatedCookies = rotated.getResponse().getHeaders("Set-Cookie");
        Cookie secondRefresh = fromSetCookie(rotatedCookies.stream()
                .filter(s -> s.startsWith("BANK_REFRESH=")).findFirst().orElseThrow());
        assertNotEquals(firstRefresh.getValue(), secondRefresh.getValue());

        // Replaying the rotated token is reuse: 401 and the family dies.
        mockMvc.perform(post("/api/v1/auth/browser/refresh")
                        .cookie(firstRefresh, csrf)
                        .header(BrowserSessionCookies.CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isUnauthorized());

        // The replacement died with its family too.
        mockMvc.perform(post("/api/v1/auth/browser/refresh")
                        .cookie(secondRefresh, csrf)
                        .header(BrowserSessionCookies.CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cookieRefreshRequiresCsrf() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/browser/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthWebRequest("ghost", "GhostPass1234"))))
                .andReturn();
        // Unknown user: login itself fails, but the refresh path shape is the point.
        // Use any refresh cookie value: missing CSRF must 403 before token checks.
        Cookie csrf = new Cookie("BANK_CSRF", "x".repeat(43));
        Cookie refresh = new Cookie("BANK_REFRESH", "whatever");

        mockMvc.perform(post("/api/v1/auth/browser/refresh")
                        .cookie(refresh, csrf))
                .andExpect(status().isForbidden());
    }

    private static Cookie fromSetCookie(String header) {
        String[] parts = header.substring(0, header.indexOf(';')).split("=", 2);
        return new Cookie(parts[0], parts[1]);
    }

}
