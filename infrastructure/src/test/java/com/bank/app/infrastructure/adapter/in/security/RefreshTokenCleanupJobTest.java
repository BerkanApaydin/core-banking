package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupJobTest {

    @Mock
    private RefreshTokenPort refreshTokens;

    @Captor
    private ArgumentCaptor<LocalDateTime> cutoffCaptor;

    @Test
    void shouldDeleteSessionsExpiredAsOfNow() {
        Clock fixed =
                Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);
        var job = new RefreshTokenCleanupJob(refreshTokens,
                AdvisorySchedulerLock.alwaysRun(),
                () -> fixed);
        when(refreshTokens.deleteExpiredBefore(any())).thenReturn(5);

        job.cleanExpired();

        verify(refreshTokens).deleteExpiredBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 5, 1, 12, 0));
    }

    @Test
    void shouldSwallowStoreFailuresForNextSchedule() {
        var job = new RefreshTokenCleanupJob(refreshTokens);
        when(refreshTokens.deleteExpiredBefore(any()))
                .thenThrow(new IllegalStateException("store unavailable"));

        assertThatNoException().isThrownBy(job::cleanExpired);
    }
}
