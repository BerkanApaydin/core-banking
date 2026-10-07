package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@Component
public class TransferPersistenceAdapter implements SaveTransferPort, LoadTransferPort {

    private final TransferJpaRepository repository;
    private final TransferJpaMapper mapper;

    public TransferPersistenceAdapter(TransferJpaRepository repository, TransferJpaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Transfer save(Transfer transfer) {
        if (transfer == null) {
            throw new IllegalArgumentException("Transfer must not be null");
        }
        TransferJpaEntity entity;
        if (transfer.getId() == null) {
            entity = mapper.toJpaEntity(transfer);
        } else {
            entity = repository.findById(transfer.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Transfer not found: " + transfer.getId()));
            // Loading a managed entity must not discard the caller's expected version.
            // Hibernate still detects a concurrent write after this comparison at flush.
            if (transfer.getVersion() == null || !transfer.getVersion().equals(entity.getVersion())) {
                throw new ObjectOptimisticLockingFailureException(TransferJpaEntity.class, transfer.getId());
            }
            mapper.updateJpaEntity(entity, transfer);
        }
        TransferJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Transfer> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Transfer> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public LoadTransferPort.HistoryPage findHistoryPage(Long accountId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        long offset = (long) safePage * safeSize;
        List<Object[]> rows = repository.findHistoryPage(accountId, safeSize, offset);
        List<Transfer> items = rows.stream()
                .map(row -> mapper.toDomain((TransferJpaEntity) row[0]))
                .toList();
        // Window function repeats the total on every row; empty page means zero.
        long total = rows.isEmpty() ? 0L : ((Number) rows.get(0)[1]).longValue();
        return new LoadTransferPort.HistoryPage(items, total);
    }

    @Override
    public List<Transfer> findHistoryBetween(Long accountId, LocalDateTime start, LocalDateTime end, int page, int size) {
        return repository.findHistoryBetween(accountId, start, end, overfetchPage(page, size))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public LoadTransferPort.ReportTotals summarizeRange(Long accountId, LocalDateTime start, LocalDateTime end) {
        List<Object[]> rows = repository.summarizeRange(accountId, start, end);
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).length < 2) {
            return new LoadTransferPort.ReportTotals(0, java.math.BigDecimal.ZERO);
        }
        Object[] row = rows.get(0);
        long count = row[0] instanceof Number n ? n.longValue() : 0L;
        java.math.BigDecimal volume = row[1] instanceof java.math.BigDecimal v ? v
                : row[1] instanceof Number n ? java.math.BigDecimal.valueOf(n.doubleValue())
                : java.math.BigDecimal.ZERO;
        return new LoadTransferPort.ReportTotals(count, volume);
    }

    /**
     * Fetches one row beyond the logical page (offset {@code page * size},
     * limit {@code size + 1}) so the caller decides {@code hasNext} from the
     * same result set instead of issuing a second query. A plain
     * {@link PageRequest#of(int, int)} cannot express this: it derives the
     * offset as {@code page * pageSize}, which would skip a row per page once
     * the limit grows by one — and {@code Slice} has the same derivation, so
     * it does not help either. Sort stays {@link Sort#unsorted()} so the
     * query's own {@code ORDER BY} applies.
     *
     * <p>K6/D10: the former 50-line anonymous implementation is replaced by
     * the small immutable {@link OffsetLimitPageable} below.
     */
    private static Pageable overfetchPage(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 0);
        return new OffsetLimitPageable((long) safePage * safeSize, safeSize + 1);
    }

    /**
     * Minimal offset/limit {@link Pageable}: Spring Data applies
     * {@code getOffset()}/{@code getPageSize()} to the query and never calls
     * the navigation methods for a {@code List} query, so they return
     * best-effort values anchored at this window.
     */
    record OffsetLimitPageable(long offset, int limit) implements Pageable {
        OffsetLimitPageable {
            if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
            if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        }

        @Override
        public int getPageNumber() {
            return limit == 0 ? 0 : (int) (offset / limit);
        }

        @Override
        public int getPageSize() {
            return limit;
        }

        @Override
        public long getOffset() {
            return offset;
        }

        @Override
        public Sort getSort() {
            return Sort.unsorted();
        }

        @Override
        public Pageable next() {
            return new OffsetLimitPageable(offset + limit, limit);
        }

        @Override
        public Pageable previousOrFirst() {
            return new OffsetLimitPageable(Math.max(offset - limit, 0), limit);
        }

        @Override
        public Pageable first() {
            return new OffsetLimitPageable(0, limit);
        }

        @Override
        public Pageable withPage(int pageNumber) {
            return new OffsetLimitPageable((long) Math.max(pageNumber, 0) * limit, limit);
        }

        @Override
        public boolean hasPrevious() {
            return offset > 0;
        }
    }
}
