package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.common.domain.Currency;
import com.bank.app.persistence.AuditableJpaEntity;
import com.bank.app.transfer.domain.TransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.ColumnResult;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityResult;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transfers")
@SqlResultSetMapping(
        name = "TransferHistoryPageMapping",
        entities = @EntityResult(entityClass = TransferJpaEntity.class),
        columns = @ColumnResult(name = "total_count", type = Long.class)
)
@NamedNativeQuery(
        name = "TransferJpaEntity.findHistoryPage",
        query = """
                SELECT t.*, COUNT(*) OVER() AS total_count
                FROM transfers t
                WHERE t.sender_account_id = :accountId OR t.receiver_account_id = :accountId
                ORDER BY t.created_at DESC, t.id DESC
                LIMIT :limit OFFSET :offset
                """,
        resultSetMapping = "TransferHistoryPageMapping"
)
public class TransferJpaEntity extends AuditableJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_account_id", nullable = false)
    private Long senderAccountId;

    @Column(name = "receiver_account_id", nullable = false)
    private Long receiverAccountId;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TransferStatus status;

    /**
     * Business creation instant assigned by the domain ({@code Transfer.create}).
     * Kept separate from the auditing {@code created_at} populated at insert time,
     * so the cancellation window and time-travel tests stay deterministic across
     * save→reload round trips. NOT NULL since V28 (V20 backfilled legacy rows);
     * report queries filter this column directly so the business-time index applies.
     */
    @Column(name = "business_created_at", nullable = false)
    private LocalDateTime businessCreatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public TransferJpaEntity() {
    }

    public TransferJpaEntity(Long id, Long senderAccountId, Long receiverAccountId, BigDecimal amount,
                             Currency currency, TransferStatus status, Long version) {
        this.id = id;
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSenderAccountId() {
        return senderAccountId;
    }

    public void setSenderAccountId(Long senderAccountId) {
        this.senderAccountId = senderAccountId;
    }

    public Long getReceiverAccountId() {
        return receiverAccountId;
    }

    public void setReceiverAccountId(Long receiverAccountId) {
        this.receiverAccountId = receiverAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public void setStatus(TransferStatus status) {
        this.status = status;
    }

    public LocalDateTime getBusinessCreatedAt() {
        return businessCreatedAt;
    }

    public void setBusinessCreatedAt(LocalDateTime businessCreatedAt) {
        this.businessCreatedAt = businessCreatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
