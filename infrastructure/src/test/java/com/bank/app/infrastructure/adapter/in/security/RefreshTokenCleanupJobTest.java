package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.user.application.port.out.RefreshTokenPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
        var job = new RefreshTokenCleanupJob(refreshTokens);
        when(refreshTokens.deleteExpiredBefore(any())).thenReturn(5);

        job.cleanExpired();

        verify(refreshTokens).deleteExpiredBefore(cutoffCaptor.capture());
        long minutesAgo = ChronoUnit.MINUTES.between(cutoffCaptor.getValue(), LocalDateTime.now());
        assertThat(minutesAgo).isBetween(0L, 5L);
    }

    @Test
    void shouldSwallowStoreFailuresForNextSchedule() {
        var job = new RefreshTokenCleanupJob(refreshTokens);
        when(refreshTokens.deleteExpiredBefore(any()))
                .thenThrow(new IllegalStateException("store unavailable"));

        assertThatNoException().isThrownBy(job::cleanExpired);
    }
}
