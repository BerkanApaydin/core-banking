package com.bank.testsupport.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone = ZoneOffset.UTC;

    public MutableClock(Instant instant) {
        this.instant = instant;
    }

    public void reset(Instant now) {
        this.instant = now;
    }

    public void advance(java.time.Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId otherZone) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return otherZone;
            }

            @Override
            public Clock withZone(ZoneId ignored) {
                return this;
            }

            @Override
            public Instant instant() {
                return instant;
            }
        };
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
