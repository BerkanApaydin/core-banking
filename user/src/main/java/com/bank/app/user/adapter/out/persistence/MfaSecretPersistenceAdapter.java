package com.bank.app.user.adapter.out.persistence;

import com.bank.app.user.application.port.out.MfaSecretPort;
import org.springframework.dao.DataAccessException;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import org.springframework.stereotype.Component;
import java.util.Optional;

/**
 * AES-GCM ciphertext store for TOTP secrets (column
 * {@code users.mfa_secret_encrypted}). Dedicated statements — never the
 * aggregate mapper — so generic user saves cannot overwrite or leak secrets.
 */
@Component
public class MfaSecretPersistenceAdapter implements MfaSecretPort {

    private final UserJpaRepository repository;

    public MfaSecretPersistenceAdapter(UserJpaRepository repository) {
        this.repository = repository;
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
}
