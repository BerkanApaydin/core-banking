package com.bank.app.account.infrastructure.persistence;

import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.ModuleIntegrationTestConfig;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.security.CustomUserDetails;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import com.bank.app.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {com.bank.app.account.TestApplication.class, ModuleIntegrationTestConfig.class})
@Transactional
@SuppressWarnings("null")
class AccountTransactionRollbackIntegrationTest extends AbstractSpringBootIntegrationTest {

    @Autowired
    private CreateAccountUseCase createAccountUseCase;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        UserJpaEntity user = new UserJpaEntity();
        user.setUsername("testuser");
        user.setPassword("encoded");
        user.setRole("ROLE_USER");
        user = userRepository.save(user);
        userId = user.getId();

        var details = new CustomUserDetails(user.getId(), "testuser", "",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRollbackTransactionWhenDuplicateIbanExceptionThrown() {
        CreateAccountRequest request = new CreateAccountRequest(
                userId,
                "TR290006200000000000000999",
                "Test User",
                new BigDecimal("500.00"),
                Currency.TRY);

        createAccountUseCase.execute(request);

        long countBefore = accountJpaRepository.count();

        CreateAccountRequest duplicateRequest = new CreateAccountRequest(
                userId,
                "TR290006200000000000000999",
                "Test User 2",
                new BigDecimal("1000.00"),
                Currency.TRY);

        assertThrows(Exception.class, () -> createAccountUseCase.execute(duplicateRequest));

        long countAfter = accountJpaRepository.count();
        assertEquals(countBefore, countAfter,
                "Duplicate IBAN exception should rollback transaction, leaving DB unchanged");
    }

    @Test
    void shouldRollbackTransactionWhenInvalidIbanProvided() {
        long countBefore = accountJpaRepository.count();

        CreateAccountRequest request = new CreateAccountRequest(
                userId,
                "INVALID_IBAN",
                "Test User",
                new BigDecimal("500.00"),
                Currency.TRY);

        assertThrows(Exception.class, () -> createAccountUseCase.execute(request));

        long countAfter = accountJpaRepository.count();
        assertEquals(countBefore, countAfter,
                "Invalid IBAN should rollback transaction, leaving DB unchanged");
    }
}
