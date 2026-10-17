package com.bank.app.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByUsername(String username);

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
}
