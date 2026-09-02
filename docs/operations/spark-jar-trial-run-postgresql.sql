-- Spark JAR online trial-run incremental migration for an existing Admin
-- PostgreSQL database. Hibernate ddl-auto=update does not replace enum CHECK
-- constraints when a Java enum gains a new value.
--
-- This script preserves every task run and is safe to execute repeatedly.

BEGIN;

SET LOCAL search_path TO public;
SET LOCAL lock_timeout TO '10s';
SET LOCAL statement_timeout TO '5min';

DO $migration$
DECLARE
    constraint_name text;
BEGIN
    IF to_regclass('task_run') IS NULL THEN
        RAISE EXCEPTION 'task_run is required; run the existing task migrations first';
    END IF;

    FOR constraint_name IN
        SELECT DISTINCT constraint_row.conname
        FROM pg_constraint constraint_row
        JOIN pg_attribute attribute_row
          ON attribute_row.attrelid = constraint_row.conrelid
         AND attribute_row.attnum = ANY (constraint_row.conkey)
        WHERE constraint_row.conrelid = 'task_run'::regclass
          AND constraint_row.contype = 'c'
          AND attribute_row.attname = 'execution_mode'
    LOOP
        EXECUTE format('ALTER TABLE task_run DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END
$migration$;

ALTER TABLE task_run
    ADD CONSTRAINT task_run_execution_mode_check
    CHECK (execution_mode IN ('REAL', 'SIMULATED', 'TRIAL'));

COMMIT;
