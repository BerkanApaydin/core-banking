package com.bank.app.transfer.application.port;

import com.bank.app.transfer.domain.Transfer;
import org.springframework.lang.NonNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LoadTransferPort {
    Optional<Transfer> findById(@NonNull Long id);

    Optional<Transfer> findByIdWithLock(@NonNull Long id);

    List<Transfer> findBySenderAccountId(Long accountId);

    List<Transfer> findBySenderAccountIdAndCreatedAtBetween(Long accountId, LocalDateTime start, LocalDateTime end);

    List<Transfer> findHistory(Long accountId, int page, int size);

    List<Transfer> findHistoryBetween(Long accountId, LocalDateTime start, LocalDateTime end);

    List<Transfer> findHistoryBetween(Long accountId, LocalDateTime start, LocalDateTime end, int page, int size);

    long countHistory(Long accountId);
}
