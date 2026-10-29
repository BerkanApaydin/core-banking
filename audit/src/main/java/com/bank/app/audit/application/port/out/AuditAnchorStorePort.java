package com.bank.app.audit.application.port.out;

import com.bank.app.audit.application.dto.AuditAnchor;
import java.util.Optional;

/**
 * Append-only sink for signed audit-chain anchors. Production adapters should
 * point at storage that is external to the database and, ideally, WORM
 * (object-lock bucket or immutable volume); the application still verifies the
 * signature and head continuity before trusting a stored anchor.
 */
public interface AuditAnchorStorePort {

    /**
     * @return the most recently appended anchor, or empty when no anchor has
     *         been written yet.
     */
    Optional<AuditAnchor> latest();

    /**
     * Appends one anchor line and makes it durable before returning.
     *
     * @throws IllegalStateException when the sink cannot be read or written
     */
    void append(AuditAnchor anchor);
}
