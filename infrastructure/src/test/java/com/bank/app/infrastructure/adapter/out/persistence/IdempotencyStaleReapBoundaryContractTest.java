package com.bank.app.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reaper's "older than" boundary lives in the bulk UPDATE, not in the
 * scheduler (which only computes {@code now - olderThan}). No database is
 * available to the unit suite, so the declarative query contract is asserted:
 * a row exactly AT the cutoff must survive (still considered in flight), while
 * a row 1 ms older must be reaped.
 */
class IdempotencyStaleReapBoundaryContractTest {

    @Test
    void shouldReapOnlyPendingHttpRowsStrictlyOlderThanTheCutoff() throws Exception {
        Method method = IdempotencyKeyJpaRepository.class
                .getMethod("failStalePending", LocalDateTime.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        // Strict '<': EXACTLY at the cutoff is NOT reaped; one millisecond
        // older is. A '<=' mutant would fail the live request that owns the
        // reservation at the boundary.
        assertThat(query.value()).contains("created_at < :threshold");
        assertThat(query.value()).doesNotContain("created_at <= :threshold");
        assertThat(query.value()).contains("status='PENDING'");
        assertThat(query.value()).contains("key_kind='HTTP'");
    }
}
