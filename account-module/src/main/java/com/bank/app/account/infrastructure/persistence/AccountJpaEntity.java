package com.bank.app.account.infrastructure.persistence;

import com.bank.app.common.persistence.AuditableJpaEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class AccountJpaEntity extends AuditableJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(unique = true, nullable = false)
    private String iban;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false)
    private String currency;

    private String status;

    @Version
    private Long version;

    public AccountJpaEntity() {}

    public AccountJpaEntity(Long id, Long userId, String iban, String ownerName, BigDecimal balance, String currency, String status) {
        this.id = id;
        this.userId = userId;
        this.iban = iban;
        this.ownerName = ownerName;
        this.balance = balance;
        this.currency = currency;
        this.status = status;
    }

    public AccountJpaEntity(Long id, Long userId, String iban, String ownerName, BigDecimal balance, String currency, String status, Long version) {
        this(id, userId, iban, ownerName, balance, currency, status);
        this.version = version;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
