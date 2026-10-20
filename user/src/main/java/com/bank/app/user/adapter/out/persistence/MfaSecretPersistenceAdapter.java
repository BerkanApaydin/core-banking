package com.bank.app.user.adapter.out.persistence;

import com.bank.app.common.application.port.out.TransactionBoundaryPort;
import com.bank.app.user.application.port.out.MfaSecretPort;
import org.springframework.dao.DataAccessException;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * AES-GCM ciphertext store for TOTP secrets (column
 * {@code users.mfa_secret_encrypted}). Dedicated statements — never the
 * aggregate mapper — so generic user saves cannot overwrite or leak secrets.
 */
@Component
public class MfaSecretPersistenceAdapter implements MfaSecretPort {

    /**
     * Replay-counter and lazy re-encryption writes are row-scoped and must
     * commit independently of any caller transaction: the login use case is
     * intentionally not transactional (Redis login guards stay outside a
     * database transaction), while the JPA {@code @Modifying} statements
     * require one.
     */
    private static final int WRITE_TX_TIMEOUT_SECONDS = 30;

    private final UserJpaRepository repository;
    private final TransactionBoundaryPort transactionBoundary;

    public MfaSecretPersistenceAdapter(UserJpaRepository repository,
            TransactionBoundaryPort transactionBoundary) {
        this.repository = repository;
        this.transactionBoundary = transactionBoundary;
    }

    @Override
    public void saveSecret(Long userId, String encryptedBase64) {
        try {
            repository.updateMfaSecret(userId, encryptedBase64);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public void reencryptSecret(Long userId, String encryptedBase64) {
        try {
            transactionBoundary.executeRequiresNew(() -> {
                repository.updateMfaSecret(userId, encryptedBase64);
                return null;
            }, WRITE_TX_TIMEOUT_SECONDS);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public Optional<String> findSecret(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            return repository.findMfaSecret(userId).filter(s -> s != null && !s.isBlank());
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public void deleteSecret(Long userId) {
        try {
            repository.clearMfaSecret(userId);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public boolean recordAcceptedTotpCounter(Long userId, long counter) {
        if (userId == null) {
            return false;
        }
        try {
            // Conditional write: only a strictly newer counter is accepted.
            // Zero updated rows means the code was a replay. REQUIRES_NEW
            // supplies the transaction the JPA update requires when the
            // (deliberately non-transactional) login use case is the caller.
            Boolean updated = transactionBoundary.executeRequiresNew(
                    () -> repository.updateMfaLastTotpCounterIfNewer(userId, counter) > 0,
                    WRITE_TX_TIMEOUT_SECONDS);
            return Boolean.TRUE.equals(updated);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public OptionalLong findLastAcceptedTotpCounter(Long userId) {
        if (userId == null) {
            return OptionalLong.empty();
        }
        try {
            return repository.findMfaLastTotpCounter(userId)
                    .filter(java.util.Objects::nonNull)
                    .map(OptionalLong::of)
                    .orElse(OptionalLong.empty());
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }
}
