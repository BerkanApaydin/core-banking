package com.bank.app.common.domain.exception;

/**
 * Marker for exceptions that represent a completed security response whose
 * database side effects MUST survive, even though the use case throws.
 *
 * <p>The canonical case is refresh-token reuse: the use case revokes the whole
 * token family (and writes the audit row) and then throws so the caller gets a
 * 401. A plain rollback would undo the revocation and leave the stolen session
 * alive — the exact opposite of the intended response.
 *
 * <p>Defined in the shared domain so the transaction aspect can recognise the
 * marker without depending on any bounded context. Only exceptions whose
 * pre-throw state is fully consistent may implement it; an audit failure
 * raised after the revoke is intentionally a different exception and still
 * rolls back.
 */
public interface CommitOnFailure {
}
