package com.bank.app.persistence;

import com.bank.app.BankApplication;
import com.bank.app.account.adapter.out.persistence.AccountJpaEntity;
import com.bank.app.account.adapter.out.persistence.AccountJpaRepository;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.domain.Currency;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2 regression: V65 makes {@code ledger_entries} append-only in the database.
 * The adapter already refused updates/deletes in application code, but a bug
 * or a direct session could still rewrite the journal. These tests exercise
 * the trigger through raw JDBC, below the adapter.
 */
@SpringBootTest(classes = BankApplication.class)
@DisplayName("Ledger append-only guard")
class LedgerAppendOnlyIntegrationTest extends AbstractSpringBootIntegrationTest {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final UserJpaRepository userRepository;
    private final AccountJpaRepository accountRepository;

    private Long userId;
    private Long accountId;

    @Autowired
    LedgerAppendOnlyIntegrationTest(JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            UserJpaRepository userRepository,
            AccountJpaRepository accountRepository,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        transactionTemplate.executeWithoutResult(status -> {
            UserJpaEntity user = userRepository.save(new UserJpaEntity(
                    null, "ledger_guard_" + suffix, "pass", Role.ROLE_USER, null, null, null));
            userId = user.getId();
            AccountJpaEntity account = accountRepository.save(new AccountJpaEntity(
                    null, userId, "TR" + UUID.randomUUID().toString().replace("-", ""),
                    "Ledger Guard", BigDecimal.ZERO, Currency.TRY, AccountStatus.ACTIVE, null));
            accountId = account.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('app.ledger_maintenance', 'on', true)", String.class);
            jdbcTemplate.update("DELETE FROM ledger_entries WHERE account_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", accountId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        });
    }

    private Long insertLedgerEntry() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO ledger_entries
                    (id, transaction_ref, account_id, direction, amount, currency,
                     balance_after, business_at, created_at)
                VALUES (nextval('ledger_entry_ids'), ?, ?, 'CREDIT', 10.00, 'TRY', 10.00, NOW(), NOW())
                RETURNING id
                """, Long.class, UUID.randomUUID().toString(), accountId);
    }

    @Test
    @DisplayName("UPDATE is rejected by the database")
    void updateIsRejected() {
        Long ledgerId = insertLedgerEntry();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE ledger_entries SET amount = amount + 1 WHERE id = ?", ledgerId))
                .as("a direct UPDATE must not rewrite posted journal evidence")
                .hasStackTraceContaining("immutable");
    }

    @Test
    @DisplayName("DELETE without the maintenance flag is rejected")
    void deleteWithoutMaintenanceIsRejected() {
        Long ledgerId = insertLedgerEntry();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM ledger_entries WHERE id = ?", ledgerId))
                .as("only the transaction-local maintenance path may remove a leg")
                .hasStackTraceContaining("append-only");
    }

    @Test
    @DisplayName("TRUNCATE is rejected")
    void truncateIsRejected() {
        insertLedgerEntry();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> jdbcTemplate.execute("TRUNCATE ledger_entries")))
                .as("row triggers do not fire for TRUNCATE, so a statement trigger guards it")
                .hasStackTraceContaining("must not be truncated");
    }

    @Test
    @DisplayName("transaction-local maintenance flag allows test/archive deletes")
    void maintenanceFlagAllowsDelete() {
        Long ledgerId = insertLedgerEntry();

        Integer deleted = transactionTemplate.execute(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('app.ledger_maintenance', 'on', true)", String.class);
            return jdbcTemplate.update("DELETE FROM ledger_entries WHERE id = ?", ledgerId);
        });

        assertThat(deleted).isEqualTo(1);
    }
}
