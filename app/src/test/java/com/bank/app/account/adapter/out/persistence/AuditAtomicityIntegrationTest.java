package com.bank.app.account.adapter.out.persistence;

import com.bank.app.BankApplication;

import com.bank.app.account.application.port.in.AdjustAccountBalancesUseCase;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import com.bank.app.user.adapter.out.persistence.UserJpaEntity;
import com.bank.app.user.adapter.out.persistence.UserJpaRepository;
import com.bank.app.user.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * A-2: audit atomicity contract (mandatory-same-tx). If the audit write blows
 * up, the money movement rolls back too — otherwise money would move silently
 * without an audit trail. This test pins the contract; future refactors (e.g.
 * a REQUIRES_NEW leak) turn red.
 */
@SpringBootTest(classes = BankApplication.class)
class AuditAtomicityIntegrationTest extends AbstractSpringBootIntegrationTest {

    @MockitoBean
    private AuditEventPort auditEventPort;

    private final AdjustAccountBalancesUseCase adjust;
    private final AccountJpaRepository accounts;
    private final UserJpaRepository users;

    @Autowired
    AuditAtomicityIntegrationTest(AdjustAccountBalancesUseCase adjust,
            AccountJpaRepository accounts,
            UserJpaRepository users,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.adjust = adjust;
        this.accounts = accounts;
        this.users = users;
    }

    @Test
    void failedAuditWriteShouldRollbackMoneyMovement() {
        // No outer transaction on purpose: the use-case aspect opens its own
        // (REQUIRED) transaction, so only without a joining test transaction can
        // this test observe the rollback in committed state. With a test-level
        // @Transactional the failed tx would stay rollback-only yet readable,
        // and the assertion would see dirty state instead of the rollback.
        // V24 FK (fk_accounts_user_id, RESTRICT): accounts need real owners.
        UserJpaEntity senderUser =
                new UserJpaEntity();
        senderUser.setUsername("audit-atom-sender-" + System.nanoTime());
        senderUser.setPassword("$2a$12$testencodedhash000000000000000000000001");
        senderUser.setRole(Role.ROLE_USER);
        final Long senderUserId = users.save(senderUser).getId();
        UserJpaEntity receiverUser =
                new UserJpaEntity();
        receiverUser.setUsername("audit-atom-receiver-" + System.nanoTime());
        receiverUser.setPassword("$2a$12$testencodedhash000000000000000000000001");
        receiverUser.setRole(Role.ROLE_USER);
        final Long receiverUserId = users.save(receiverUser).getId();
        // Unique IBANs per run: the shared container survives across reruns.
        String senderIban = uniqueIban();
        String receiverIban = uniqueIban();
        Account sender = new Account(null, new UserId(senderUserId), new Iban(senderIban),
                "Sender", Money.exact(new BigDecimal("100.00"), Currency.TRY), AccountStatus.ACTIVE);
        Account receiver = new Account(null, new UserId(receiverUserId), new Iban(receiverIban),
                "Receiver", Money.exact(new BigDecimal("0.00"), Currency.TRY), AccountStatus.ACTIVE);
        // Direct JPA setup (use-case would also audit; setup must stay silent).
        AccountJpaMapper mapper =
                new AccountJpaMapper();
        final Long senderId = accounts.save(mapper.toJpaEntity(sender)).getId();
        final Long receiverId = accounts.save(mapper.toJpaEntity(receiver)).getId();

        try {
            doThrow(new RuntimeException("audit store down")).when(auditEventPort).publish(any());

            assertThatThrownBy(() -> adjust.debitAndCredit(senderId, receiverId,
                    Money.exact(new BigDecimal("10.00"), Currency.TRY)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("audit store down");

            AccountJpaEntity sAfter =
                    accounts.findById(senderId).orElseThrow();
            AccountJpaEntity rAfter =
                    accounts.findById(receiverId).orElseThrow();
            // Rollback proof on committed state: balances did not move.
            assertThat(sAfter.getBalance())
                    .isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(rAfter.getBalance())
                    .isEqualByComparingTo(new BigDecimal("0.00"));
        } finally {
            // Shared container: never leak rows (FK order — accounts first).
            accounts.deleteById(receiverId);
            accounts.deleteById(senderId);
            users.deleteById(receiverUserId);
            users.deleteById(senderUserId);
        }
    }

    private static long ibanSeq = 990000L;

    private static synchronized String uniqueIban() {
        // TR + 24 digits, unique per invocation so reruns never hit the IBAN UNIQUE.
        return "TR" + String.format("%024d", ibanSeq += 7919);
    }
}
