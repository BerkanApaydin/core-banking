package com.bank.app.user.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Server-side refresh-token sessions owned by the User bounded context.
 * Only token digests cross this port — raw tokens never reach persistence.
 */
public interface RefreshTokenPort {

    record StoredRefresh(String tokenHash, Long userId, String familyId,
                         LocalDateTime expiresAt, boolean revoked, String replacedByHash) {
    }

    void save(String tokenHash, Long userId, String familyId, LocalDateTime expiresAt);

    Optional<StoredRefresh> findByTokenHash(String tokenHash);

    /** Marks a rotated token revoked and links it to its replacement. */
    void markRotated(String oldHash, String newHash);

    /** Revokes a single session (logout); unknown hashes are ignored. */
    void revoke(String tokenHash);

    /** Revokes every session of a family (theft response, logout-all). */
    void revokeFamily(String familyId);

    /** Hygiene: drops expired rows; returns the deleted count. */
    int deleteExpiredBefore(LocalDateTime cutoff);
}
