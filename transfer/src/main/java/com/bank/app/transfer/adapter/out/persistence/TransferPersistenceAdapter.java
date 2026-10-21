package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.exception.TransferNotFoundException;
import java.math.BigDecimal;
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
        if (transfer.getId() == null) {
            TransferJpaEntity entity = mapper.toJpaEntity(transfer);
            TransferJpaEntity saved = repository.save(entity);
            return mapper.toDomain(saved);
        }
        // Perf-1: status-transition writes (COMPLETED/CANCELLED) are a single
        // versioned UPDATE with no preceding SELECT. Only the rare 0-row path
        // loads once to distinguish not-found from conflict. A missing expected
        // version is rejected outright: blind writes without an OCC precondition
        // are never allowed (pinned by rejectsMissingVersionForExistingTransfer).
        if (transfer.getVersion() == null) {
            // Exceptional path only (callers always carry the version they read):
            // a context-bypassing count distinguishes a missing row from a
            // blind write. Domain NOT_FOUND (404 like the account sibling),
            // never bare IllegalArgumentException (500).
            if (repository.countById(transfer.getId()) == 0) {
                throw new TransferNotFoundException(transfer.getId());
            }
            throw new ObjectOptimisticLockingFailureException(TransferJpaEntity.class, transfer.getId());
        }
        int updated = repository.updateStatusIfVersionMatch(
                transfer.getId(), transfer.getVersion(), transfer.getStatus());
        if (updated == 1) {
            Long bumped = transfer.getVersion() + 1;
            return new Transfer(transfer.getId(), transfer.getSenderAccountId(),
                    transfer.getReceiverAccountId(), transfer.getAmount(),
                    transfer.getStatus(), transfer.getCreatedAt(), bumped);
        }
        if (repository.countById(transfer.getId()) == 0) {
            throw new TransferNotFoundException(transfer.getId());
        }
        throw new ObjectOptimisticLockingFailureException(TransferJpaEntity.class, transfer.getId());
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
        // PERF: every page uses the rows-only query plus a separate
        // covering-index count.
        //
        // The first page used to ride on a `COUNT(*) OVER()` window, which
        // PostgreSQL can only evaluate AFTER materialising the WHOLE union
        // and sorting it — so a 20-row page cost O(n) over the account's
        // whole transfer history on the hottest read path. The standalone
        // count is an index-only count over the two per-side indexes and
        // does not sort, so the page is paid once instead of per request.
        List<Transfer> items = repository.findHistoryRows(accountId, safeSize, offset).stream()
                .map(mapper::toDomain)
                .toList();
        return new LoadTransferPort.HistoryPage(items, repository.countHistory(accountId));
    }

    @Override
    public List<Transfer> findHistoryBetween(Long accountId, LocalDateTime start, LocalDateTime end, int page, int size) {
        return repository.findHistoryBetween(accountId, start, end, overfetchPage(page, size))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Transfer> findHistoryBetweenKeyset(Long accountId, LocalDateTime start, LocalDateTime end,
            LocalDateTime cursorCreatedAt, Long cursorId, int size) {
        int safeSize = Math.max(size, 1);
        // size+1 over-fetch: same hasNext-without-second-query contract.
        Pageable limit = PageRequest.of(0, safeSize + 1, Sort.unsorted());
        return repository.findHistoryBetweenKeyset(accountId, start, end, cursorCreatedAt, cursorId, limit)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public LoadTransferPort.ReportTotals summarizeRange(Long accountId, LocalDateTime start, LocalDateTime end) {
        List<Object[]> rows = repository.summarizeRange(accountId, start, end);
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).length < 2) {
            return new LoadTransferPort.ReportTotals(0, BigDecimal.ZERO);
        }
        Object[] row = rows.get(0);
        long count = row[0] instanceof Number n ? n.longValue() : 0L;
        // Never funnel a monetary sum through double: BigDecimal.valueOf(double)
        // would silently lose cents if the driver ever returns Double/Float.
        // Number.toString preserves the decimal representation (K-2/P-2).
        BigDecimal volume = row[1] instanceof BigDecimal v ? v
                : row[1] instanceof Number n ? new BigDecimal(n.toString())
                : BigDecimal.ZERO;
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
