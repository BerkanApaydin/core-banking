package com.bank.app.transfer.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard: report/history date ranges must filter business time
 * (business_created_at, V20; NOT NULL since V28) rather than the auditing
 * insert timestamp, so the report semantics stay consistent with the
 * cancellation window and the domain mapper. The predicate must reference
 * the bare column — a COALESCE wrapper would defeat the V21 business-time
 * index. No database needed: asserts on the JPQL string only.
 */
class TransferJpaRepositoryQueryTest {

    @Test
    void findHistoryBetweenShouldFilterBusinessTime() throws Exception {
        Query query = TransferJpaRepository.class
                .getMethod("findHistoryBetween", Long.class,
                        java.time.LocalDateTime.class, java.time.LocalDateTime.class,
                        org.springframework.data.domain.Pageable.class)
                .getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("businessCreatedAt BETWEEN")
                .doesNotContain("COALESCE")
                .contains("t.createdAt DESC, t.id DESC");
    }
}
