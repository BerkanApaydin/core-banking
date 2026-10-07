-- V34__audit_actor_user_id.sql
-- Stable audit attribution (8.2). audit_logs.username is a display snapshot:
-- it defaults to "system" for background legs and breaks if a username is
-- ever renamed. actor_user_id carries the stable users.id alongside it, so a
-- report can prove *which identity* acted even when the name is gone.
--
-- Nullable with NO foreign key by design (same rationale as V31
-- refresh_tokens.user_id): there is no user-deletion flow, history rows must
-- survive independently, and a hard FK would couple audit retention to the
-- user lifecycle. History rows stay NULL; only new writes populate the id.
-- No index yet: no query filters on it (admin listing is limit-ordered).
-- Add one with the first filtered query, not before.

ALTER TABLE audit_logs ADD COLUMN actor_user_id BIGINT;
