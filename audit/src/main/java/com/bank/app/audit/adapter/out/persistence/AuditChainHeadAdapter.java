package com.bank.app.audit.adapter.out.persistence;

import com.bank.app.audit.application.dto.AuditChainHead;
import com.bank.app.audit.application.port.out.AuditChainHeadPort;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only projection of the V67 chain state for the external anchor
 * publisher. The head query is the same singleton row the trigger locks; no
 * lock is taken here because an anchor snapshot only needs a consistent read
 * of the current committed head.
 */
@Component
public class AuditChainHeadAdapter implements AuditChainHeadPort {

    static final String HEAD_SQL =
            "SELECT last_seq, last_hash FROM audit_chain_state WHERE id = 1";
    static final String ROW_HASH_SQL =
            "SELECT row_hash FROM audit_logs WHERE chain_seq = ?";
    static final String MIN_CHAIN_SEQ_SQL =
            "SELECT min(chain_seq) FROM audit_logs";

    private final JdbcTemplate jdbcTemplate;

    public AuditChainHeadAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AuditChainHead> findHead() {
        List<AuditChainHead> rows = jdbcTemplate.query(HEAD_SQL,
                (rs, rowNum) -> new AuditChainHead(
                        rs.getLong("last_seq"),
                        rs.getString("last_hash")));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<String> findRowHash(long chainSeq) {
        List<String> rows = jdbcTemplate.query(ROW_HASH_SQL,
                (rs, rowNum) -> rs.getString("row_hash"), chainSeq);
        return rows.stream().findFirst();
    }

    @Override
    public OptionalLong findMinChainSeq() {
        Long min = jdbcTemplate.queryForObject(MIN_CHAIN_SEQ_SQL, Long.class);
        return min == null ? OptionalLong.empty() : OptionalLong.of(min);
    }
}
