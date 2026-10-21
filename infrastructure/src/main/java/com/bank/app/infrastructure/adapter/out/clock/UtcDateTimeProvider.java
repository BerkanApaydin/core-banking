package com.bank.app.infrastructure.adapter.out.clock;

import com.bank.app.common.application.port.out.ClockProviderPort;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

public class UtcDateTimeProvider implements DateTimeProvider {

    private final ClockProviderPort clockProviderPort;

    public UtcDateTimeProvider(ClockProviderPort clockProviderPort) {
        this.clockProviderPort = clockProviderPort;
    }

    @Override
    public Optional<TemporalAccessor> getNow() {
        return Optional.of(LocalDateTime.ofInstant(clockProviderPort.clock().instant(), ZoneOffset.UTC));
    }
}
