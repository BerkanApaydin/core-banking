# API Versioning & Deprecation Policy (K19)

## Status

Accepted for `/api/v1` and the `account-api` published language.

## REST surface

- URL versioning (`/api/v1/**`), enforced by `@ApiVersion("v1")` +
  `ApiVersionValidationFilter`. A future `/api/v2` is additive: v1 keeps
  serving until its announced sunset.
- Additive changes (new optional fields, new endpoints) are minor: no version
  bump, documented in release notes.
- Breaking changes (removed/renamed fields, altered status codes, new required
  fields) require a new major version AND a deprecation window:
  1. Ship v(n+1) alongside v(n).
  2. Announce v(n) sunset (>= 90 days) in release notes + `docs/release.md`.
  3. Sunset: remove the version prefix handling and its WebMvc tests.
- `TransferReportResponse`/`PageResponse` pagination shapes are part of the
  contract: `pageTransferCount`, `pageVolume`, `hasNext` semantics must not
  change within a major version.

## `account-api` published language (Java)

- `AccountApi`, `AccountSnapshot`, `AccountAdjustmentResult` evolve by
  addition only (new methods get `default` implementations where possible).
- Removing or retyping a member is a major-module change: bump the module's
  documented version in this file and migrate `transfer` (the only consumer)
  in the same release — never leave the consumer behind.

## What is NOT versioned

- `audit_logs` / outbox event payloads: append-only evidence, read by versioned
  consumers that tolerate unknown fields.
- `messages.properties` texts: human-readable, not a contract.
