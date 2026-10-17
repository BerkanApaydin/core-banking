package com.bank.app.user.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, String> {

    List<RefreshTokenJpaEntity> findByFamilyId(String familyId);

    /**
     * C-1: conditional rotation — one UPDATE that succeeds only while the row
     * is still active, so two concurrent refreshes can never both rotate the
     * same token. Returns 1 when this caller won, 0 when a concurrent request
     * won the race (the loser re-reads and takes the reuse/family-revoke
     * path in the use case).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenJpaEntity e SET e.revoked = true, e.replacedByHash = :newHash "
            + "WHERE e.tokenHash = :hash AND e.revoked = false")
    int rotateIfActive(@Param("hash") String hash, @Param("newHash") String newHash);

    /**
     * P-2: family revocation as a single bulk UPDATE instead of
     * 1 SELECT + N UPDATE. Theft response stays one round trip.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenJpaEntity e SET e.revoked = true "
            + "WHERE e.familyId = :familyId AND e.revoked = false")
    int revokeFamilyBulk(@Param("familyId") String familyId);

    // Audit (P2): sibling bulk statements declare clearAutomatically +
    // flushAutomatically so the persistence context never serves stale
    // entities after the bulk write; this delete was the only exception.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshTokenJpaEntity e WHERE e.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
