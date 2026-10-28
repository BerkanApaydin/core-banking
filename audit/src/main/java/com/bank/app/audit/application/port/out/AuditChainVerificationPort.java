package com.bank.app.audit.application.port.out;

import java.util.OptionalLong;

/**
 * Reads the database-side audit hash-chain verifier. The verifier recomputes
 * every row hash and checks each prev_hash link, so a mutation or a deletion
 * inside the retained chain surfaces as a chain_seq to investigate.
 */
public interface AuditChainVerificationPort {

    /**
     * @return the chain_seq of the first broken link, or empty when the
     *         retained chain is intact.
     */
    OptionalLong findFirstBrokenChainSeq();
}
