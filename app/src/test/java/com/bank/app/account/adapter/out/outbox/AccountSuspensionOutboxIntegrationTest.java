package com.bank.app.account.adapter.out.outbox;

import com.bank.app.BankApplication;
import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.in.SuspendAccountUseCase;
import com.bank.app.account.domain.AccountSuspendedEvent;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.Currency;
import com.bank.app.infrastructure.security.SimpleAuthenticatedPrincipal;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.bank.app.user.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-A01 regression against the real adapters: suspending an account must
 * publish the event from the aggregate that recorded it, so a row with
 * {@code AccountSuspendedEvent} lands in the outbox. Publishing from the
 * rehydrated save result (which has no domain events) silently lost the row.
 */
@SpringBootTest(classes = BankApplication.class)
@SuppressWarnings("null")
@DisplayName("Account suspension outbox event")
class AccountSuspensionOutboxIntegrationTest extends AbstractSpringBootIntegrationTest {

    private final CreateAccountUseCase createAccountUseCase;
    private final SuspendAccountUseCase suspendAccountUseCase;
    private final UserJpaRepository users;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    private Long userId;
    private String username;

    @Autowired
    AccountSuspensionOutboxIntegrationTest(CreateAccountUseCase createAccountUseCase,
            SuspendAccountUseCase suspendAccountUseCase, UserJpaRepository users,
            JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.createAccountUseCase = createAccountUseCase;
        this.suspendAccountUseCase = suspendAccountUseCase;
        this.users = users;
        this.jdbc = jdbc;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        username = "suspend_outbox_" + UUID.randomUUID().toString().replace("-", "");
        UserJpaEntity user = new UserJpaEntity();
        user.setUsername(username);
        user.setPassword("$2a$12$testencodedhash000000000000000000000001");
        user.setRole(Role.ROLE_ADMIN);
        user = users.saveAndFlush(user);
        userId = user.getId();

        var principal = new SimpleAuthenticatedPrincipal(userId, username,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (userId == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM outbox_events WHERE aggregate_id IN "
                    + "(SELECT CAST(id AS VARCHAR) FROM accounts WHERE user_id = ?)", userId);
            jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        });
        userId = null;
    }

    @Test
    void suspendWritesAccountSuspendedEventThroughTheRealOutboxAdapter() {
        AccountResponse account = createAccountUseCase.execute(new CreateAccountRequest(
                userId, null, "Suspension Owner", BigDecimal.ZERO, Currency.TRY));

        // Account creation publishes its own event; clear it so the assertion
        // below can only be satisfied by the suspend path.
        jdbc.update("DELETE FROM outbox_events WHERE aggregate_id = ?",
                String.valueOf(account.id()));

        suspendAccountUseCase.suspend(account.id());

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT event_type, aggregate_type, payload FROM outbox_events "
                        + "WHERE aggregate_id = ?",
                String.valueOf(account.id()));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst())
                .containsEntry("event_type", AccountSuspendedEvent.class.getName())
                .containsEntry("aggregate_type", "Account");
        assertThat((String) rows.getFirst().get("payload"))
                .contains("\"accountId\":" + account.id());
    }
}
