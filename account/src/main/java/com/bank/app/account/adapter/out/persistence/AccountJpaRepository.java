package com.bank.app.account.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
}
