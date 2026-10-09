package com.bank.app;

import com.bank.app.common.AbstractSpringBootIntegrationTest;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I-05: locks the distributed-tracing seam end to end. The OTLP exporter
 * ({@code micrometer-tracing-bridge-otel} + {@code opentelemetry-exporter-otlp})
 * is only useful if spans are actually created in-process and the HTTP edge
 * propagates correlation/trace IDs that operators can join against collector
 * output (see {@code k8s/otel-collector.yaml}, {@code docs/tracing.md}).
 *
 * <p>Production disables tracing by default ({@code application-prod.yml})
 * and the simulation profile samples at 1.0 — this test runs on the default
 * profile where tracing is on, so a wiring regression (bridge dropped,
 * auto-configuration excluded) turns red here instead of silently shipping
 * a collector with nothing to collect.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TracingObservationIT extends AbstractSpringBootIntegrationTest {

    private final TestRestTemplate restTemplate;
    private final ObservationRegistry observationRegistry;
    private final ObjectProvider<Tracer> tracerProvider;

    @Autowired
    TracingObservationIT(TestRestTemplate restTemplate,
            ObjectProvider<CacheManager> cacheManagers,
            ObservationRegistry observationRegistry,
            ObjectProvider<Tracer> tracerProvider) {
        super(cacheManagers);
        this.restTemplate = restTemplate;
        this.observationRegistry = observationRegistry;
        this.tracerProvider = tracerProvider;
    }

    @Test
    void otelTracerBeanIsWired() {
        assertThat(tracerProvider.getIfAvailable())
                .as("micrometer-tracing-bridge-otel must contribute a Tracer; "
                        + "without it the OTLP exporter ships spans nowhere")
                .isNotNull();
    }

    @Test
    void observationCreatesInProcessSpan() {
        Tracer tracer = tracerProvider.getIfAvailable();
        assertThat(tracer).isNotNull();

        Observation observation = Observation.start("bank.tracing.smoke", observationRegistry);
        assertThat(observation.isNoop())
                .as("observation must be handled by the OTel bridge, not dropped as noop")
                .isFalse();
        try (Observation.Scope scope = observation.openScope()) {
            assertThat(tracer.currentSpan())
                    .as("an open observation scope must expose a current span")
                    .isNotNull();
        } finally {
            observation.stop();
        }
    }

    @Test
    void httpEdgePropagatesCorrelationAndTraceIds() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Correlation-ID"))
                .as("every response must carry a correlation ID for log joins")
                .isNotBlank();
        assertThat(response.getHeaders().getFirst("X-Trace-ID"))
                .as("trace ID must never be blank so prod JSON logs join to traces")
                .isNotBlank();
    }
}
