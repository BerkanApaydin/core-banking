package com.bank.app.user.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByUsername(String username);

    /**
     * R-U12: row lock for the password-reset request path. The lock is held
     * until the enclosing use-case transaction commits, serializing the
     * "retire old token + insert new token" pair for the same account.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM UserJpaEntity e WHERE e.username = :username")
    Optional<UserJpaEntity> findByUsernameForUpdate(@Param("username") String username);

    /**
     * R-T10: primary-key row lock used by family revocation. Rotation takes
     * the same lock before retiring a refresh token, which serializes
     * "revoke family" against "rotate and insert a successor".
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM UserJpaEntity e WHERE e.id = :userId")
    Optional<UserJpaEntity> findByIdForUpdate(@Param("userId") Long userId);

    /**
     * MFA secret writes bypass the aggregate mapper on purpose (see
     * UserJpaMapper): ciphertext must never be round-tripped through generic
     * user saves. clearAutomatically + flushAutomatically match the other
     * bulk statements so the persistence context never serves stale rows.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserJpaEntity e SET e.mfaSecretEncrypted = :secret WHERE e.id = :userId")
    int updateMfaSecret(@Param("userId") Long userId, @Param("secret") String encryptedBase64);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserJpaEntity e SET e.mfaSecretEncrypted = NULL WHERE e.id = :userId")
    int clearMfaSecret(@Param("userId") Long userId);

    @Query("SELECT e.mfaSecretEncrypted FROM UserJpaEntity e WHERE e.id = :userId")
    Optional<String> findMfaSecret(@Param("userId") Long userId);

    /**
     * TOTP replay protection (V56). The counter is advanced ONLY by a
     * strictly newer value, so a replayed code (or two concurrent replays of
     * the same code) updates zero rows and is rejected by the database.
     *
     * <p>{@code clearAutomatically + flushAutomatically} keep the persistence
     * context from serving a stale counter to a later read in the same
     * transaction.
     *
     * @return 1 when the counter was accepted, 0 when it was a replay
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserJpaEntity e SET e.mfaLastTotpCounter = :counter "
            + "WHERE e.id = :userId "
            + "AND (e.mfaLastTotpCounter IS NULL OR e.mfaLastTotpCounter < :counter)")
    int updateMfaLastTotpCounterIfNewer(@Param("userId") Long userId,
                                        @Param("counter") long counter);

    @Query("SELECT e.mfaLastTotpCounter FROM UserJpaEntity e WHERE e.id = :userId")
    Optional<Long> findMfaLastTotpCounter(@Param("userId") Long userId);
}
