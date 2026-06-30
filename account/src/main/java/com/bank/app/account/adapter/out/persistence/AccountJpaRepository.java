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
    Optional<AccountJpaEntity> findByIbanWithLock(@Param("iban") String iban);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountJpaEntity a WHERE a.id = :id")
    Optional<AccountJpaEntity> findByIdWithLock(@Param("id") Long id);

    Page<AccountJpaEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<AccountJpaEntity> findByIdIn(Collection<Long> ids);
}
