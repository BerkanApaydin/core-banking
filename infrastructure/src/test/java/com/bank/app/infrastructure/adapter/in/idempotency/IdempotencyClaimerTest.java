package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.adapter.in.idempotency.Idempotent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyClaimerTest {

    @Mock
    private IdempotencyGuard guard;

    @Mock
    private IdempotencyRequestResolver resolver;

    @Mock
    private ServletRequestAttributes attributes;

    private Idempotent annotation() {
        Idempotent ann = mock(Idempotent.class);
        return ann;
    }

    private IdempotencyRequestResolver.RequestIdentity identity() {
        return new IdempotencyRequestResolver.RequestIdentity("key-1", "hash-1");
    }

    @Test
    void shouldProceedWhenNoIdentity() throws Throwable {
        when(resolver.resolve(any(), any(), any())).thenReturn(null);

        var decision = new IdempotencyClaimer(guard, resolver)
                .claim(attributes, annotation(), new Object[0]);

        assertThat(decision).isInstanceOf(IdempotencyClaimer.Decision.Proceed.class);
    }

    @Test
    void shouldReplayWhenCompleted() throws Throwable {
        when(resolver.resolve(any(), any(), any())).thenReturn(identity());
        var completed = IdempotencyGuard.IdempotencyResult.completed("{}", 201);
        when(guard.startRequest(eq("key-1"), eq("hash-1"))).thenReturn(completed);

        var decision = new IdempotencyClaimer(guard, resolver)
                .claim(attributes, annotation(), new Object[0]);

        assertThat(decision).isEqualTo(new IdempotencyClaimer.Decision.Replay(completed));
    }

    @Test
    void shouldContendWhenPending() throws Throwable {
        when(resolver.resolve(any(), any(), any())).thenReturn(identity());
        when(guard.startRequest(eq("key-1"), eq("hash-1")))
                .thenReturn(IdempotencyGuard.IdempotencyResult.pending());

        var decision = new IdempotencyClaimer(guard, resolver)
                .claim(attributes, annotation(), new Object[0]);

        assertThat(decision).isInstanceOf(IdempotencyClaimer.Decision.Contended.class);
    }

    @Test
    void shouldGuardWhenNew() throws Throwable {
        when(resolver.resolve(any(), any(), any())).thenReturn(identity());
        when(guard.startRequest(eq("key-1"), eq("hash-1")))
                .thenReturn(IdempotencyGuard.IdempotencyResult.newRequest());

        var decision = new IdempotencyClaimer(guard, resolver)
                .claim(attributes, annotation(), new Object[0]);

        assertThat(decision).isEqualTo(new IdempotencyClaimer.Decision.Guarded("key-1"));
    }
}
