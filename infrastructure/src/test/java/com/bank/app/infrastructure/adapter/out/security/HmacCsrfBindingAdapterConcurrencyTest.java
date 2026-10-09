package com.bank.app.infrastructure.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * I-01: the browser-session CSRF contract under contention. The filter calls
 * {@link HmacCsrfBindingAdapter} on every mutating request, so issue/verify
 * must be thread-safe (fresh {@code Mac} per call, thread-safe
 * {@code SecureRandom}) and cross-credential replays must fail even when 16
 * threads race. A regression here would be a CSRF bypass, not a flaky test.
 */
class HmacCsrfBindingAdapterConcurrencyTest {

    private static final byte[] MAC_KEY =
            "test-mac-key-for-concurrency-check-0123456789".getBytes(StandardCharsets.UTF_8);

    @Test
    void shouldVerifyBoundPairsUnderConcurrency() throws Exception {
        HmacCsrfBindingAdapter adapter = new HmacCsrfBindingAdapter(MAC_KEY);
        int threads = 16;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final String credential = "session-jwt-" + t;
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        String token = adapter.issueCsrfToken(credential);
                        if (token.length() != 87) {
                            return false;
                        }
                        if (!adapter.verifyCsrfToken(token, token, credential)) {
                            return false;
                        }
                        // Cross-credential replay must fail even under contention.
                        if (adapter.verifyCsrfToken(token, token, credential + "-other")) {
                            return false;
                        }
                        // A tampered random part must fail the MAC check.
                        char c42 = token.charAt(42);
                        String tampered = token.substring(0, 42) + (c42 == 'A' ? 'B' : 'A')
                                + token.substring(43);
                        if (adapter.verifyCsrfToken(tampered, tampered, credential)) {
                            return false;
                        }
                    }
                    return true;
                }));
            }
            for (Future<Boolean> future : futures) {
                assertTrue(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
