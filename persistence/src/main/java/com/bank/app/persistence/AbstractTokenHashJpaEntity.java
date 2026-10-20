package com.bank.app.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Base for token rows keyed by an assigned, random token hash.
 *
 * <p>Assigned ids look "existing" to Spring Data, which would merge()
 * (SELECT-before-INSERT) on every issuance. Hashes are random per issuance,
 * so fresh instances are always new rows; {@link #markNotNew()} flips the
 * flag after load/persist.
 */
@MappedSuperclass
public abstract class AbstractTokenHashJpaEntity extends AuditableJpaEntity
        implements Persistable<String> {

    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Transient
    private boolean isNew = true;

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
    public void markNotNew() {
        this.isNew = false;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }
}
