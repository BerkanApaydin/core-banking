-- Test/support database only. Flyway owns the schema of bank_db (V1..V28
-- migrations run on startup); nothing here may create tables or roles.
-- Testcontainers-based tests do NOT use this database at all.
-- Create test database if it doesn't exist
CREATE DATABASE bank_db_test;
