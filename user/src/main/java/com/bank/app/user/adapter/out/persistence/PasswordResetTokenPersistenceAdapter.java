package com.bank.app.user.adapter.out.persistence;

import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.PasswordResetTokenPort;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Single-use reset-token store. Only SHA-256 digests are persisted; raw tokens
 * travel solely through the notification channel.
 */
@Component
public class PasswordResetTokenPersistenceAdapter implements PasswordResetTokenPort {

    private final PasswordResetTokenJpaRepository repository;

    public PasswordResetTokenPersistenceAdapter(PasswordResetTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void issue(PasswordResetToken token) {
        try {
            PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity();
            entity.setTokenHash(token.tokenHash());
            entity.setUserId(token.userId());
            entity.setExpiresAt(token.expiresAt());
            entity.setUsedAt(token.usedAt());
            repository.save(entity);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public Optional<PasswordResetToken> findByHash(String tokenHash) {
        if (tokenHash == null) {
            return Optional.empty();
        }
        try {
            return repository.findById(tokenHash)
                    .map(e -> new PasswordResetToken(e.getTokenHash(), e.getUserId(),
                            e.getExpiresAt(), e.getUsedAt()));
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public boolean tryMarkUsed(String tokenHash, LocalDateTime usedAt) {
        try {
            return repository.markUsedIfUnused(tokenHash, usedAt) == 1;
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public void deleteByUserId(Long userId) {
        try {
            repository.deleteByUserId(userId);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }
}
