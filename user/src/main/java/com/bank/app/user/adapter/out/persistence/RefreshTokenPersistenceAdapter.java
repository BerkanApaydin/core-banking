package com.bank.app.user.adapter.out.persistence;

import com.bank.app.user.application.port.out.RefreshTokenPort;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenPort {

    private final RefreshTokenJpaRepository repository;

    public RefreshTokenPersistenceAdapter(RefreshTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(String tokenHash, Long userId, String familyId, LocalDateTime expiresAt) {
        Objects.requireNonNull(tokenHash, "Token hash must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(familyId, "Family ID must not be null");
        Objects.requireNonNull(expiresAt, "Expiry must not be null");
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.setTokenHash(tokenHash);
        entity.setUserId(userId);
        entity.setFamilyId(familyId);
        entity.setExpiresAt(expiresAt);
        entity.setRevoked(false);
        repository.save(entity);
    }

    @Override
    public Optional<StoredRefresh> findByTokenHash(String tokenHash) {
        if (tokenHash == null) {
            return Optional.empty();
        }
        return repository.findById(tokenHash).map(entity -> new StoredRefresh(
                entity.getTokenHash(),
                entity.getUserId(),
                entity.getFamilyId(),
                entity.getExpiresAt(),
                entity.isRevoked(),
                entity.getReplacedByHash()));
    }

    @Override
    public void markRotated(String oldHash, String newHash) {
        Objects.requireNonNull(oldHash, "Old hash must not be null");
        Objects.requireNonNull(newHash, "New hash must not be null");
        // C-1: conditional UPDATE (no read-modify-write): 0 rows means a
        // concurrent refresh already rotated this token, or it is unknown.
        // Same fail-fast contract as before — the use case re-reads to tell
        // reuse (family revoke) apart from a stale/unknown token.
        int rotated = repository.rotateIfActive(oldHash, newHash);
        if (rotated == 0) {
            throw new IllegalArgumentException("Unknown refresh token");
        }
    }

    @Override
    public void revoke(String tokenHash) {
        if (tokenHash == null) {
            return;
        }
        repository.findById(tokenHash).ifPresent(entity -> {
            entity.setRevoked(true);
            repository.save(entity);
        });
    }

    @Override
    public void revokeFamily(String familyId) {
        Objects.requireNonNull(familyId, "Family ID must not be null");
        // P-2: single bulk UPDATE — no SELECT + N UPDATE round trips.
        repository.revokeFamilyBulk(familyId);
    }

    @Override
    public int deleteExpiredBefore(LocalDateTime cutoff) {
        Objects.requireNonNull(cutoff, "Cutoff must not be null");
        return repository.deleteExpiredBefore(cutoff);
    }
}
