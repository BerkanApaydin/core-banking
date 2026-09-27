package com.bank.app.user.adapter.in.web;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.adapter.in.security.BrowserSessionCookies;
import com.bank.app.common.domain.Iban;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.bank.app.BankApplication.class)
@AutoConfigureMockMvc
@Transactional
@SuppressWarnings("null")
class BrowserSessionIntegrationTest extends AbstractSpringBootIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

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
        user.setPassword(passwordEncoder.encode("BrowserPass1"));
        user.setRole("ROLE_USER");
        users.saveAndFlush(user);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/browser/login")
                        .cookie(new Cookie("BANK_SESSION", "expired-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.bank.app.user.adapter.in.web.dto.AuthWebRequest(username, "BrowserPass1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        List<String> setCookies = login.getResponse().getHeaders("Set-Cookie");
        assertEquals(2, setCookies.size());
        assertEquals("no-store", login.getResponse().getHeader("Cache-Control"));
        String sessionHeader = setCookies.stream().filter(s -> s.startsWith("BANK_SESSION=")).findFirst().orElseThrow();
        String csrfHeader = setCookies.stream().filter(s -> s.startsWith("BANK_CSRF=")).findFirst().orElseThrow();
        assertTrue(sessionHeader.contains("HttpOnly"));
        assertTrue(sessionHeader.contains("SameSite=Strict"));
        assertFalse(csrfHeader.contains("HttpOnly"));
        Cookie session = fromSetCookie(sessionHeader);
        Cookie csrf = fromSetCookie(csrfHeader);

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
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
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

    private static Cookie fromSetCookie(String header) {
        String[] parts = header.substring(0, header.indexOf(';')).split("=", 2);
        return new Cookie(parts[0], parts[1]);
    }

}
