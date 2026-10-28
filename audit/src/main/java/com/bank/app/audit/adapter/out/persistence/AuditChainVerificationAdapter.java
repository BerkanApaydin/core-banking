package com.bank.app.audit.adapter.out.persistence;

import com.bank.app.audit.application.port.out.AuditChainVerificationPort;
import java.util.OptionalLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuditChainVerificationAdapter implements AuditChainVerificationPort {

    static final String FIRST_BROKEN_SEQ_SQL =
            "SELECT audit_logs_first_broken_chain_seq()";

    private final JdbcTemplate jdbcTemplate;

    public AuditChainVerificationAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OptionalLong findFirstBrokenChainSeq() {
        Long broken = jdbcTemplate.queryForObject(FIRST_BROKEN_SEQ_SQL, Long.class);
        return broken == null ? OptionalLong.empty() : OptionalLong.of(broken);
    }
}
