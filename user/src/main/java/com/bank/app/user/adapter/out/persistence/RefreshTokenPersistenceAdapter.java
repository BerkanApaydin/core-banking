package com.bank.app.user.adapter.out.persistence;

import com.bank.app.common.application.port.out.TransactionBoundaryPort;
import com.bank.app.user.application.port.out.AuthenticationBackendUnavailableException;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenPort {

    private static final int BULK_TX_TIMEOUT_SECONDS = 30;

    private final RefreshTokenJpaRepository repository;
    private final UserJpaRepository userRepository;
    private final TransactionBoundaryPort transactionBoundary;

    public RefreshTokenPersistenceAdapter(RefreshTokenJpaRepository repository,
            UserJpaRepository userRepository,
            TransactionBoundaryPort transactionBoundary) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.transactionBoundary = transactionBoundary;
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
        try {
            repository.save(entity);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public Optional<StoredRefresh> findByTokenHash(String tokenHash) {
        if (tokenHash == null) {
            return Optional.empty();
        }
        try {
            return repository.findById(tokenHash).map(entity -> new StoredRefresh(
                    entity.getTokenHash(),
                    entity.getUserId(),
                    entity.getFamilyId(),
                    entity.getExpiresAt(),
                    entity.isRevoked(),
                    entity.getReplacedByHash()));
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public void markRotated(String oldHash, String newHash) {
        Objects.requireNonNull(oldHash, "Old hash must not be null");
        Objects.requireNonNull(newHash, "New hash must not be null");
        // C-1: conditional UPDATE (no read-modify-write): 0 rows means a
        // concurrent refresh already rotated this token, or it is unknown.
        // Same fail-fast contract as before — the use case re-reads to tell
        // reuse (family revoke) apart from a stale/unknown token.
        int rotated;
        try {
            rotated = repository.rotateIfActive(oldHash, newHash);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
        if (rotated == 0) {
            throw new IllegalArgumentException("Unknown refresh token");
        }
    }

    @Override
    public void revoke(String tokenHash) {
        if (tokenHash == null) {
            return;
        }
        // R-U09: conditional UPDATE instead of read-modify-write. The old
        // findById + save overwrote replacedByHash when a concurrent refresh
        // had already rotated the token, defeating reuse detection.
        try {
            repository.revokeIfActive(tokenHash);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    @Override
    public void revokeFamily(String familyId, Long userId) {
        Objects.requireNonNull(familyId, "Family ID must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");
        // P-2: single bulk UPDATE — no SELECT + N UPDATE round trips. The
        // logout path is intentionally not transactional, so it still gets a
        // dedicated transaction; the refresh/reuse path already runs inside
        // one and joins it (K11/D4: a failed audit must roll the revoke back
        // instead of deadlocking on a nested REQUIRES_NEW).
        try {
            if (transactionBoundary.isTransactionActive()) {
                revokeFamilyLockingUser(familyId, userId);
                return;
            }
            transactionBoundary.executeRequiresNew(
                    () -> revokeFamilyLockingUser(familyId, userId), BULK_TX_TIMEOUT_SECONDS);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    /**
     * R-T10: serialize family revocation against rotation on the user row.
     * Both this method and the rotation path lock the same user row before
     * touching refresh rows, so a rotation either commits its successor
     * before this scan (and that successor is revoked too) or waits and then
     * fails on the already-revoked token. Lock ordering is always
     * user row -> refresh rows, so no deadlock is possible.
     */
    private int revokeFamilyLockingUser(String familyId, Long userId) {
        userRepository.findByIdForUpdate(userId);
        return repository.revokeFamilyBulk(familyId);
    }

    @Override
    public void revokeAllForUser(Long userId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        try {
            // Join an ambient transaction (password change / MFA disable):
            // REQUIRES_NEW would commit the revoke even if the surrounding
            // security change rolls back, and could deadlock on the user row
            // the caller already holds. Logout-all has no ambient transaction
            // and still gets a dedicated one.
            if (transactionBoundary.isTransactionActive()) {
                revokeAllLockingUser(userId);
                return;
            }
            transactionBoundary.executeRequiresNew(
                    () -> revokeAllLockingUser(userId), BULK_TX_TIMEOUT_SECONDS);
        } catch (DataAccessException e) {
            throw new AuthenticationBackendUnavailableException(e);
        }
    }

    private int revokeAllLockingUser(Long userId) {
        userRepository.findByIdForUpdate(userId);
        return repository.revokeAllForUserBulk(userId);
    }

    @Override
    public int deleteExpiredBefore(LocalDateTime cutoff) {
        Objects.requireNonNull(cutoff, "Cutoff must not be null");
        return repository.deleteExpiredBefore(cutoff);
    }
}
