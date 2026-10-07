package com.bank.app.infrastructure.adapter.out.clock;

import com.bank.app.common.application.port.out.ClockProviderPort;

import java.time.Clock;

public class SystemClockProvider implements ClockProviderPort {

    private final Clock clock;

    public SystemClockProvider(Clock clock) {
        // UTC by default, not systemDefaultZone: every timestamp the domain
        // mints (created_at, business time, audit instants) must mean the same
        // instant on every replica, developer laptop and CI runner. Deployment
        // pinning (TZ=UTC) covers containers; this covers everything else.
        // Callers that need a fixed instant (tests) pass their own clock.
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public Clock clock() {
        return clock;
    }
}
