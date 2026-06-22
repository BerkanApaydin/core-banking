package com.bank.app.transfer.application.port.out;

import com.bank.app.transfer.domain.Transfer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LoadTransferPort {
    Optional<Transfer> findById(Long id);
    Optional<Transfer> findByIdWithLock(Long id);
    List<Transfer> findBySenderAccountId(Long accountId);
    List<Transfer> findBySenderAccountIdAndCreatedAtBetween(Long accountId, LocalDateTime start, LocalDateTime end);
    List<Transfer> findHistory(Long accountId, int page, int size);
    List<Transfer> findHistoryBetween(Long accountId, LocalDateTime start, LocalDateTime end, int page, int size);
    long countHistory(Long accountId);
}
