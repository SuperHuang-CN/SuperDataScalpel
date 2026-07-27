-- SPARK_STREAMING_CANVAS incremental migration for the Admin PostgreSQL database.
--
-- Run with the Admin application stopped. Existing LOCAL_SQL and SPARK_CANVAS
-- tasks/runs are preserved. The script does not rebuild or truncate any table.

BEGIN;

SET LOCAL search_path TO public;
SET LOCAL lock_timeout TO '10s';
SET LOCAL statement_timeout TO '5min';

DO $migration$
BEGIN
    IF to_regclass('task') IS NULL
            OR to_regclass('task_run') IS NULL
            OR to_regclass('task_canvas_definition') IS NULL THEN
        RAISE EXCEPTION
            'task, task_run and task_canvas_definition are required; run the existing task migrations first';
    END IF;
END
$migration$;

LOCK TABLE task, task_run, task_canvas_definition IN SHARE ROW EXCLUSIVE MODE;

-- Hibernate does not replace enum CHECK constraints when a Java enum grows.
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
        WHERE constraint_row.conrelid = 'task'::regclass
          AND constraint_row.contype = 'c'
          AND attribute_row.attname = 'type'
    LOOP
        EXECUTE format('ALTER TABLE task DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END
$migration$;

ALTER TABLE task
    ADD CONSTRAINT task_type_check
    CHECK (type IN ('LOCAL_SQL', 'SPARK_CANVAS', 'SPARK_STREAMING_CANVAS'));

ALTER TABLE task_run
    ADD COLUMN IF NOT EXISTS streaming_deployment_id uuid;

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
        WHERE constraint_row.conrelid = 'task_run'::regclass
          AND constraint_row.contype = 'c'
          AND attribute_row.attname IN ('task_type', 'status')
    LOOP
        EXECUTE format('ALTER TABLE task_run DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END
$migration$;

ALTER TABLE task_run
    ADD CONSTRAINT task_run_task_type_check
        CHECK (task_type IN ('LOCAL_SQL', 'SPARK_CANVAS', 'SPARK_STREAMING_CANVAS')),
    ADD CONSTRAINT task_run_status_check
        CHECK (status IN (
            'QUEUED', 'RUNNING', 'CANCEL_REQUESTED', 'STOP_REQUESTED',
            'SUCCESS', 'FAILED', 'TIMED_OUT', 'CANCELLED', 'STOPPED', 'SKIPPED'
        ));

CREATE INDEX IF NOT EXISTS idx_task_run_streaming_deployment
    ON task_run (streaming_deployment_id)
    WHERE streaming_deployment_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS task_streaming_configuration (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    task_id uuid NOT NULL,
    trigger_interval_seconds integer NOT NULL DEFAULT 10,
    CONSTRAINT task_streaming_configuration_pkey PRIMARY KEY (id),
    CONSTRAINT task_streaming_configuration_trigger_check
        CHECK (trigger_interval_seconds BETWEEN 1 AND 300)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_streaming_configuration_task
    ON task_streaming_configuration (task_id);

CREATE TABLE IF NOT EXISTS task_streaming_deployment (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    task_id uuid NOT NULL,
    definition_version integer NOT NULL,
    compute_engine_id uuid NOT NULL,
    current_run_id uuid,
    checkpoint_key_prefix varchar(500) NOT NULL,
    desired_state varchar(16) NOT NULL,
    actual_state varchar(16) NOT NULL,
    started_at timestamptz,
    stop_requested_at timestamptz,
    stopped_at timestamptz,
    last_progress_at timestamptz,
    last_error_at timestamptz,
    last_error varchar(2000),
    CONSTRAINT task_streaming_deployment_pkey PRIMARY KEY (id),
    CONSTRAINT task_streaming_deployment_desired_state_check
        CHECK (desired_state IN ('RUNNING', 'STOPPED')),
    CONSTRAINT task_streaming_deployment_actual_state_check
        CHECK (actual_state IN ('STARTING', 'RUNNING', 'STOPPING', 'STOPPED', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_streaming_deployment_version
    ON task_streaming_deployment (task_id, definition_version);
CREATE INDEX IF NOT EXISTS idx_task_streaming_deployment_task_state
    ON task_streaming_deployment (task_id, actual_state);
CREATE INDEX IF NOT EXISTS idx_task_streaming_deployment_run
    ON task_streaming_deployment (current_run_id);

CREATE TABLE IF NOT EXISTS task_streaming_query (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deployment_id uuid NOT NULL,
    output_node_id uuid NOT NULL,
    output_node_name varchar(100) NOT NULL,
    sink_type varchar(16) NOT NULL,
    checkpoint_key varchar(500) NOT NULL,
    state varchar(16) NOT NULL,
    latest_batch_id bigint,
    latest_input_rows bigint,
    input_rows_per_second double precision,
    processed_rows_per_second double precision,
    batch_duration_millis bigint,
    last_progress_at timestamptz,
    last_error_at timestamptz,
    last_error varchar(2000),
    CONSTRAINT task_streaming_query_pkey PRIMARY KEY (id),
    CONSTRAINT task_streaming_query_sink_type_check
        CHECK (sink_type IN ('KAFKA', 'JDBC')),
    CONSTRAINT task_streaming_query_state_check
        CHECK (state IN ('STARTING', 'RUNNING', 'STOPPING', 'STOPPED', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_streaming_query_output
    ON task_streaming_query (deployment_id, output_node_id);
CREATE INDEX IF NOT EXISTS idx_task_streaming_query_deployment_state
    ON task_streaming_query (deployment_id, state);

COMMIT;

