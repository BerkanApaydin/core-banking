package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistCleanupJobTest {

    @Mock
    private TokenBlacklistPort blacklist;

    @Test
    void shouldDelegateCleanupToBlacklistStore() {
        var job = new TokenBlacklistCleanupJob(blacklist, AdvisorySchedulerLock.alwaysRun());

        job.cleanExpired();

        verify(blacklist).cleanExpired();
    }

    @Test
    void shouldSwallowStoreFailuresForNextSchedule() {
        var job = new TokenBlacklistCleanupJob(blacklist, AdvisorySchedulerLock.alwaysRun());
        doThrow(new IllegalStateException("store unavailable")).when(blacklist).cleanExpired();

        assertThatNoException().isThrownBy(job::cleanExpired);
    }
}
