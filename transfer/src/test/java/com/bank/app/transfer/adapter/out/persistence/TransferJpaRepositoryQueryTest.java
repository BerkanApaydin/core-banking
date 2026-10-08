package com.bank.app.transfer.adapter.out.persistence;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard: report/history date ranges must filter business time
 * (business_created_at, V20; NOT NULL since V28) rather than the auditing
 * insert timestamp, so the report semantics stay consistent with the
 * cancellation window and the domain mapper. The predicate must reference
 * the bare column — a COALESCE wrapper would defeat the V21 business-time
 * index — and both history queries must UNION ALL per-side branches (P-1)
 * instead of an OR that forces a bitmap-or. No database needed: asserts on
 * the query strings only.
 */
class TransferJpaRepositoryQueryTest {

    @Test
    void findHistoryBetweenShouldFilterBusinessTimeWithUnionAll() throws Exception {
        Query query = TransferJpaRepository.class
                .getMethod("findHistoryBetween", Long.class,
                        LocalDateTime.class, LocalDateTime.class,
                        Pageable.class)
                .getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value())
                .contains("UNION ALL")
                .contains("business_created_at BETWEEN")
                .contains("sender_account_id = :accountId")
                .contains("receiver_account_id = :accountId")
                .doesNotContain("COALESCE")
                .doesNotContain(" OR t.")
                .contains("ORDER BY combined.created_at DESC, combined.id DESC");
    }

    @Test
    void findHistoryBetweenKeysetShouldFilterBusinessTimeWithUnionAll() throws Exception {
        Query query = TransferJpaRepository.class
                .getMethod("findHistoryBetweenKeyset", Long.class,
                        LocalDateTime.class, LocalDateTime.class,
                        LocalDateTime.class, Long.class, Pageable.class)
                .getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value())
                .contains("UNION ALL")
                .contains("business_created_at BETWEEN")
                .contains("CAST(:cursorCreatedAt AS TIMESTAMP) IS NULL")
                .doesNotContain("COALESCE")
                .contains("ORDER BY combined.created_at DESC, combined.id DESC");
    }
}
