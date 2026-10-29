package com.bank.app.audit.application.port.out;

import com.bank.app.audit.application.dto.AuditChainHead;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Reads the database-side chain head and a retained prefix row for the
 * external anchor publisher. Reads only; the anchor job never mutates the
 * chain or its state.
 */
public interface AuditChainHeadPort {

    /**
     * @return the singleton {@code audit_chain_state} row, or empty when the
     *         state row is missing (which the verifier treats as tampering).
     */
    Optional<AuditChainHead> findHead();

    /**
     * @param chainSeq the chain sequence to look up
     * @return the stored row hash, or empty when the row is not retained.
     */
    Optional<String> findRowHash(long chainSeq);

    /**
     * @return the oldest retained chain sequence, or empty when the table is
     *         empty.
     */
    OptionalLong findMinChainSeq();
}
