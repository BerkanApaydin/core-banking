# Long Term: Outbox → Kafka Migration Readiness

**Status:** `OutboxPort`/`OutboxEventPort` are already broker-agnostic
abstractions. Current implementation: JDBC poller (`OutboxPoller` SKIP LOCKED +
partitions + parallel workers) → in-pod `ApplicationEventPublisher`.

**Migration plan (infrastructure change, no business-code change):**
1. New `KafkaOutboxRelay implements OutboxEventPort` (producer only; the
   consumer side — existing handlers — stays unchanged).
2. `OutboxPoller` → `KafkaOutboxPoller` or Debezium CDC: replace
   `findAndLockUnprocessed` with a log tail; the `SKIP LOCKED` + partition key
   moves to topic partitions.
3. Handler idempotency keys (`outbox_handler_*`) stay as-is — at-least-once is
   already the assumption, exactly-once is never promised.
4. Dead-letter + retention stay as-is; topic retention moves to the broker.

**Ordering note:** Today there is intentionally no ordering guarantee inside a
parallel batch. On Kafka, per-key ordering can be restored with
partition-key = `accountId % partitions` (per-key order, not global order).

**Capacity trigger:** Migrate once the poller ceiling (~100-200 ev/s after
parallelization) is exceeded; before that it is YAGNI.
