package com.bank.app.audit.adapter.out.persistence;

import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditChainVerificationAdapter")
class AuditChainVerificationAdapterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AuditChainVerificationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AuditChainVerificationAdapter(jdbcTemplate);
    }

    @Test
    @DisplayName("should report an intact chain when the verifier returns null")
    void shouldReportIntactChain() {
        when(jdbcTemplate.queryForObject(
                AuditChainVerificationAdapter.FIRST_BROKEN_SEQ_SQL, Long.class))
                .thenReturn(null);

        assertThat(adapter.findFirstBrokenChainSeq()).isEmpty();
    }

    @Test
    @DisplayName("should surface the first broken chain_seq")
    void shouldSurfaceBrokenChainSeq() {
        when(jdbcTemplate.queryForObject(
                AuditChainVerificationAdapter.FIRST_BROKEN_SEQ_SQL, Long.class))
                .thenReturn(42L);

        assertThat(adapter.findFirstBrokenChainSeq()).isEqualTo(OptionalLong.of(42L));
    }
}
