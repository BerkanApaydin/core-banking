package com.bank.app.common.application.port.out;

/**
 * D-1: testable identity generation.
 *
 * <p>Domain value objects must not call {@code UUID.randomUUID()} directly:
 * randomness inside the domain is untestable and unseedable. Production code
 * generates IDs through this port (UUID-backed); tests inject a fixed
 * implementation and assert exact refs.
 */
public interface IdGeneratorPort {

    /** Returns a new unique identifier string (UUID-shaped in production). */
    String newId();
}
