package com.bank.app.common.application.port.out;

import java.time.Clock;

/**
 * Provides the current time to the application layer so that domain mutations are
 * testable and not implicitly bound to the JVM default zone. Adapters supply the
 * concrete clock (e.g. system UTC or system default zone).
 */
public interface ClockProviderPort {
    Clock clock();
}
