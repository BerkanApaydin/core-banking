package com.bank.app.user.adapter.out.persistence;

import com.bank.app.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenJpaEntity extends AuditableJpaEntity implements Persistable<String> {

    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /**
     * Assigned token-hash ids look "existing" to Spring Data, which would
     * merge() (SELECT-before-INSERT) on every issuance. Hashes are random per
     * issuance, so fresh instances are always new rows (same pattern as
     * RefreshTokenJpaEntity).
     */
    @Transient
    private boolean isNew = true;

    public PasswordResetTokenJpaEntity() {
    }

    @Override
    public String getId() {
        return getTokenHash();
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PrePersist
    void markNotNew() {
        this.isNew = false;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
