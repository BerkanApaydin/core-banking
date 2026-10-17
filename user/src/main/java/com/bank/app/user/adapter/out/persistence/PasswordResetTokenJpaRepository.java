package com.bank.app.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenJpaEntity, String> {

    /**
     * Single-use enforcement as a conditional UPDATE: succeeds only while the
     * row is still unused AND unexpired, so two concurrent confirms with the
     * same token cannot both pass (the loser sees a used token afterwards)
     * and a token that expired between the SELECT and this UPDATE cannot be
     * consumed in the race window.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetTokenJpaEntity e SET e.usedAt = :usedAt "
            + "WHERE e.tokenHash = :hash AND e.usedAt IS NULL AND e.expiresAt > :usedAt")
    int markUsedIfUnused(@Param("hash") String tokenHash, @Param("usedAt") LocalDateTime usedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordResetTokenJpaEntity e WHERE e.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
