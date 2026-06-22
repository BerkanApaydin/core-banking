package com.bank.app.transfer.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, Long> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select t from TransferJpaEntity t where t.id = :id")
        Optional<TransferJpaEntity> findByIdWithLock(@Param("id") Long id);

        List<TransferJpaEntity> findBySenderAccountId(Long senderAccountId);

        List<TransferJpaEntity> findBySenderAccountIdAndCreatedAtBetween(Long senderAccountId, LocalDateTime start,
                        LocalDateTime end);

        @Query("select t from TransferJpaEntity t where t.senderAccountId = :accountId or t.receiverAccountId = :accountId order by t.createdAt desc")
        List<TransferJpaEntity> findHistory(@Param("accountId") Long accountId, Pageable pageable);

        @Query("select t from TransferJpaEntity t where (t.senderAccountId = :accountId or t.receiverAccountId = :accountId) and t.createdAt between :start and :end")
        List<TransferJpaEntity> findHistoryBetween(
                        @Param("accountId") Long accountId,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end,
                        Pageable pageable);

        @Query("select count(t) from TransferJpaEntity t where t.senderAccountId = :accountId or t.receiverAccountId = :accountId")
        long countHistory(@Param("accountId") Long accountId);
}
