# Distributed tracing

The app ships Micrometer Tracing + OTLP exporter wiring, but tracing is
**off by default everywhere** (`TRACING_ENABLED=false`). With no collector
deployed, enabling the exporter only dials localhost noisily — worse, the
exporter is created fail-fast, so a configured-but-unreachable endpoint can
fail boot (D22). Enable tracing only where a collector actually runs.

## Local (docker compose)

`docker-compose.yml` runs `otel/opentelemetry-collector-contrib` with
`docker/otel-collector.yaml` and sets for the app:

- `TRACING_ENABLED=true`
- `MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces`

Verify spans arrive: `docker compose logs otel-collector` shows sampled
spans (sampling 10% in prod config, unsampled local logging). The local
config deletes `http.target` (may carry account IDs) and exports to logging
only — it proves flow, not retention.

## Production (Kubernetes)

1. Apply `k8s/otel-collector.yaml` (Deployment + Service + ConfigMap).
   Point the ConfigMap exporters at the real backend (Jaeger/Tempo/an
   OTLP/HTTP endpoint) — the shipped `logging` exporter keeps nothing
   queryable.
2. Patch the deployment (do NOT flip the app default):
   `TRACING_ENABLED=true` and
   `MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces`
   (same namespace; cross-namespace use the FQDN shown in
   `application-prod.yml`).
3. Confirm `traces_received` on the collector and exemplar-linked logs
   (correlation IDs already join logs to error payloads).

## Sampling

`management.tracing.sampling.probability=0.1` in prod. Raise temporarily for
incident capture, never permanently: banking traffic is high-cardinality and
the collector is single-replica.
