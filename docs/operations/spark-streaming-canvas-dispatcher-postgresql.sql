-- SPARK_STREAMING_CANVAS incremental migration for the Dispatcher PostgreSQL database.
--
-- Run with the Dispatcher stopped. No existing execution or outbox data is
-- rebuilt or cleared.

BEGIN;

SET LOCAL search_path TO dispatcher;
SET LOCAL lock_timeout TO '10s';
SET LOCAL statement_timeout TO '5min';

DO $migration$
BEGIN
    IF to_regclass('dispatcher_registration') IS NULL
            OR to_regclass('dispatcher_task_execution') IS NULL THEN
        RAISE EXCEPTION
            'dispatcher_registration and dispatcher_task_execution are required';
    END IF;
END
$migration$;

LOCK TABLE dispatcher_registration, dispatcher_task_execution
    IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE dispatcher_registration
    ADD COLUMN IF NOT EXISTS runner_control_topic varchar(249);

UPDATE dispatcher_registration
SET runner_control_topic = runner_event_topic || '.control'
WHERE runner_control_topic IS NULL OR btrim(runner_control_topic) = '';

ALTER TABLE dispatcher_registration
    ALTER COLUMN runner_control_topic SET NOT NULL;

ALTER TABLE dispatcher_task_execution
    ADD COLUMN IF NOT EXISTS streaming_deployment_id uuid,
    ADD COLUMN IF NOT EXISTS streaming_stop_requested_at timestamptz,
    ADD COLUMN IF NOT EXISTS streaming_force_stop_at timestamptz,
    ALTER COLUMN deadline_at DROP NOT NULL;

DO $migration$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT DISTINCT constraint_row.conname
        FROM pg_constraint constraint_row
        JOIN pg_attribute attribute_row
          ON attribute_row.attrelid = constraint_row.conrelid
         AND attribute_row.attnum = ANY (constraint_row.conkey)
        WHERE constraint_row.conrelid = 'dispatcher_task_execution'::regclass
          AND constraint_row.contype = 'c'
          AND attribute_row.attname IN ('task_type', 'state')
    LOOP
        EXECUTE format(
            'ALTER TABLE dispatcher_task_execution DROP CONSTRAINT %I',
            constraint_name
        );
    END LOOP;
END
$migration$;

ALTER TABLE dispatcher_task_execution
    ADD CONSTRAINT dispatcher_task_execution_task_type_check
        CHECK (task_type IN ('SPARK_CANVAS', 'SPARK_STREAMING_CANVAS')),
    ADD CONSTRAINT dispatcher_task_execution_state_check
        CHECK (state IN (
            'QUEUED', 'SUBMITTING', 'SUBMITTED', 'RUNNING',
            'CANCEL_REQUESTED', 'SUCCESS', 'FAILED', 'TIMED_OUT',
            'CANCELLED', 'STOPPED', 'LOST'
        ));

CREATE INDEX IF NOT EXISTS idx_dispatcher_execution_streaming_deployment
    ON dispatcher_task_execution (streaming_deployment_id)
    WHERE streaming_deployment_id IS NOT NULL;

COMMIT;
