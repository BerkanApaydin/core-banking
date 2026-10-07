package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.RevocationStoreUnavailableException;
import com.bank.app.common.domain.TokenDigest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseTokenBlacklistAdapterTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private JwtPort jwtPort;

    private DatabaseTokenBlacklistAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DatabaseTokenBlacklistAdapter(jdbc, transactionManager, jwtPort);
    }

    @Test
    void shouldCommitHashedRevocationWithSignedAbsoluteExpiry() {
        var status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        long expiresAtMs = System.currentTimeMillis() + 60_000;
        when(jwtPort.verifyAndDecode("token"))
                .thenReturn(new JwtPort.VerifiedToken("user", 1L, "ROLE_USER", "jti", expiresAtMs));

        adapter.blacklist("token", 1L);

        verify(jdbc).update(contains("INSERT INTO token_revocations"),
                eq(TokenDigest.sha256Hex("token")), any(OffsetDateTime.class));
        verify(transactionManager).commit(status);
    }

    @Test
    void shouldReturn503PathWhenCommitFails() {
        var status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        when(jwtPort.verifyAndDecode("token"))
                .thenReturn(new JwtPort.VerifiedToken("user", 1L, "ROLE_USER", "jti",
                        System.currentTimeMillis() + 60_000));
        doThrow(new TransactionSystemException("commit failed"))
                .when(transactionManager).commit(status);

        assertThatThrownBy(() -> adapter.blacklist("token", 60_000))
                .isInstanceOf(RevocationStoreUnavailableException.class)
                .hasCauseInstanceOf(TransactionSystemException.class);
    }

    @Test
    void shouldRejectInvalidTokenBeforeWriting() {
        assertThatThrownBy(() -> adapter.blacklist("invalid", 60_000))
                .isInstanceOf(IllegalArgumentException.class);
        verify(jwtPort).verifyAndDecode("invalid");
    }

    @Test
    void shouldReadSharedStateAndFailClosedOnIndeterminateResult() {
        String hash = TokenDigest.sha256Hex("token");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(hash)))
                .thenReturn(true, null);

        assertThat(adapter.isBlacklisted("token")).isTrue();
        assertThatThrownBy(() -> adapter.isBlacklisted("token"))
                .isInstanceOf(RevocationStoreUnavailableException.class);
    }

    @Test
    void shouldLimitCleanupToBoundedBatches() {
        when(jdbc.update(anyString())).thenReturn(1000, 3);

        adapter.cleanExpired();

        verify(jdbc, times(2)).update(anyString());
    }
}
