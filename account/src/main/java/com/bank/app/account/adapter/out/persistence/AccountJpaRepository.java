package com.bank.app.account.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bank.app.account.domain.AccountStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, Long> {

    Optional<AccountJpaEntity> findByIban(String iban);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.iban = :iban")
    Optional<AccountJpaEntity> findByIbanForUpdate(@Param("iban") String iban);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.id = :id")
    Optional<AccountJpaEntity> findByIdForUpdate(@Param("id") Long id);

    Page<AccountJpaEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    List<AccountJpaEntity> findByIdIn(Collection<Long> ids);

    /**
     * Read-model projections (see LoadAccountPort): id/owner/currency/status
     * columns only. Balance (NUMERIC(38,2)), IBAN and owner name stay in the
     * database on authorization hot paths.
     *
     * <p>{@code List} (not {@code Optional}) return: Spring Data double-wraps
     * {@code Optional<Object[]>} for multi-column selects; the single-element
     * list form is the proven pattern (see findHistoryPage).
     */
    @Query("SELECT a.id, a.userId, a.currency, a.status FROM AccountJpaEntity a WHERE a.id = :id")
    List<Object[]> findInfoById(@Param("id") Long id);

    @Query("SELECT a.id, a.userId, a.currency, a.status FROM AccountJpaEntity a WHERE a.iban = :iban")
    List<Object[]> findInfoByIban(@Param("iban") String iban);

    @Query("SELECT a.id, a.iban FROM AccountJpaEntity a WHERE a.id IN :ids")
    List<Object[]> findIbansByIds(@Param("ids") Collection<Long> ids);

    /**
     * Perf-1/P-1: versioned bulk update — single UPDATE ... WHERE id AND
     * version, no preceding SELECT. Returns affected rows (0 = missing row or
     * concurrent write; caller disambiguates only on the rare 0 path).
     * Hibernate bumps {@code @Version} automatically for managed-entity writes;
     * here the bump is explicit so the bulk path stays version-consistent.
     * R1: the bulk path bypasses Hibernate dirty checking AND the
     * {@code AuditingEntityListener}, so {@code updatedAt} is refreshed
     * explicitly — otherwise balance mutations would leave a stale audit
     * timestamp while the managed-entity path updates it.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AccountJpaEntity a SET a.balance = :balance, a.status = :status, "
            + "a.ownerName = :ownerName, a.version = a.version + 1, "
            + "a.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE a.id = :id AND a.version = :version")
    int updateIfVersionMatch(@Param("id") Long id,
            @Param("version") Long version,
            @Param("balance") BigDecimal balance,
            @Param("status") AccountStatus status,
            @Param("ownerName") String ownerName);
}
