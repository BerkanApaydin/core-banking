package com.bank.app.user;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.bank.app.BankApplication.class, properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session.events.log=false",
        "app.observability.backlog.enabled=false", "app.integrity.enabled=false"
})
class AuthenticationQueryIntegrationTest extends AbstractSpringBootIntegrationTest {
    @Autowired UserJpaRepository users;
    @Autowired LoginUserUseCase login;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtPort jwt;
    @Autowired EntityManagerFactory entityManagerFactory;
    private UserJpaEntity user;
    private Statistics statistics;

    @BeforeEach
    void seed() {
        user = users.save(new UserJpaEntity(null, "query-" + UUID.randomUUID(),
                encoder.encode("ValidPassword123"), "ROLE_ADMIN", null, null, null));
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @AfterEach
    void clean() {
        users.deleteById(user.getId());
    }

    @Test
    void loginUsesOneCredentialQueryAndKeepsAuthenticatedRole() {
        var response = login.execute(new AuthRequest(user.getUsername(), "ValidPassword123"));
        long queries = statistics.getPrepareStatementCount();
        assertEquals(1L, queries, "Successful login must not query the same user twice");
        var verified = jwt.verifyAndDecode(response.token());
        assertNotNull(verified);
        assertEquals(user.getId(), verified.userId());
        assertEquals("ROLE_ADMIN", verified.role());
        assertEquals(user.getUsername(), response.username());
    }

    @Test
    void wrongPasswordDoesNotIssueAnIdentityOrRunASecondQuery() {
        assertThrows(AuthenticationFailedException.class,
                () -> login.execute(new AuthRequest(user.getUsername(), "WrongPassword123")));
        assertEquals(1L, statistics.getPrepareStatementCount());
    }
}
