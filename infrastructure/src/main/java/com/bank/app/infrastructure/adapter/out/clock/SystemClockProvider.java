package com.bank.app.infrastructure.adapter.out.clock;

import com.bank.app.common.application.port.out.ClockProviderPort;

import java.time.Clock;

public class SystemClockProvider implements ClockProviderPort {

    private final Clock clock;

    public SystemClockProvider(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    @Override
    public Clock clock() {
        return clock;
    }
}
