-- SPARK_CANVAS Admin database migration for PostgreSQL.
--
-- Run this script against the DataScalpel Admin database while the Admin
-- application is stopped. The script preserves existing task and task_run data
-- and can be executed repeatedly.

BEGIN;

SET LOCAL search_path TO public;
SET LOCAL lock_timeout TO '10s';
SET LOCAL statement_timeout TO '5min';

DO $migration$
BEGIN
    IF to_regclass('task') IS NULL THEN
        RAISE EXCEPTION 'Required table "task" does not exist. Run task-table-prefix-migration.sql first.';
    END IF;
    IF to_regclass('task_run') IS NULL THEN
        RAISE EXCEPTION 'Required table "task_run" does not exist. Run task-table-prefix-migration.sql first.';
    END IF;
END
$migration$;

LOCK TABLE task, task_run IN SHARE ROW EXCLUSIVE MODE;

-- ---------------------------------------------------------------------------
-- 1. Common task metadata
-- ---------------------------------------------------------------------------

ALTER TABLE task
    ADD COLUMN IF NOT EXISTS compute_engine_id uuid;

-- Hibernate ddl-auto=update does not reliably replace an existing enum CHECK.
-- Drop every CHECK that constrains task.type, then install the stable contract.
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

-- ---------------------------------------------------------------------------
-- 2. Generalized task execution history
-- ---------------------------------------------------------------------------

ALTER TABLE task_run
    ADD COLUMN IF NOT EXISTS task_type varchar(32),
    ADD COLUMN IF NOT EXISTS external_execution_id uuid,
    ADD COLUMN IF NOT EXISTS execution_run_id uuid,
    ADD COLUMN IF NOT EXISTS compute_engine_id uuid,
    ADD COLUMN IF NOT EXISTS command_topic_snapshot varchar(249),
    ADD COLUMN IF NOT EXISTS last_dispatcher_event_sequence bigint,
    ADD COLUMN IF NOT EXISTS backend_application_id varchar(300),
    ADD COLUMN IF NOT EXISTS tracking_url varchar(1000),
    ADD COLUMN IF NOT EXISTS execution_attempt integer,
    ADD COLUMN IF NOT EXISTS execution_deadline_at timestamptz,
    ADD COLUMN IF NOT EXISTS manifest_object_key varchar(500),
    ADD COLUMN IF NOT EXISTS result_object_key varchar(500),
    ADD COLUMN IF NOT EXISTS log_object_key varchar(500);

-- Existing runs predate SPARK_CANVAS. If a run was already created by a newer
-- application version, derive its type from the owning task instead of forcing
-- it to LOCAL_SQL.
UPDATE task_run run
SET task_type = COALESCE(
        (SELECT task.type FROM task WHERE task.id = run.task_id),
        'LOCAL_SQL'
    )
WHERE run.task_type IS NULL;

UPDATE task_run
SET last_dispatcher_event_sequence = 0
WHERE last_dispatcher_event_sequence IS NULL;

ALTER TABLE task_run
    ALTER COLUMN task_type SET NOT NULL,
    ALTER COLUMN last_dispatcher_event_sequence SET NOT NULL;

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

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_run_execution_run_id
    ON task_run (execution_run_id)
    WHERE execution_run_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_task_run_type_status
    ON task_run (task_type, status);

CREATE INDEX IF NOT EXISTS idx_task_run_task_queued
    ON task_run (task_id, queued_at);

CREATE INDEX IF NOT EXISTS idx_task_run_task_status
    ON task_run (task_id, status);

-- ---------------------------------------------------------------------------
-- 3. Stable Canvas definition
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS task_canvas_definition (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    task_id uuid NOT NULL,
    schema_version integer NOT NULL,
    schema_minor_version integer NOT NULL DEFAULT 0,
    definition_json text NOT NULL,
    version integer NOT NULL,
    CONSTRAINT task_canvas_definition_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_canvas_definition_task
    ON task_canvas_definition (task_id);

ALTER TABLE task_canvas_definition
    ADD COLUMN IF NOT EXISTS schema_minor_version integer;

UPDATE task_canvas_definition
SET schema_minor_version = 0
WHERE schema_minor_version IS NULL;

ALTER TABLE task_canvas_definition
    ALTER COLUMN schema_minor_version SET DEFAULT 0,
    ALTER COLUMN schema_minor_version SET NOT NULL;

-- ---------------------------------------------------------------------------
-- 4. Canvas schema 1.0/1.1 and model-reference projection
--
-- Canvas JSON remains the source of truth. task_canvas_model_reference is a
-- replaceable projection used for model deletion protection and reverse
-- lookup. Missing schemaMinorVersion means legacy 1.0. New definitions use
-- 1.1. The application must be stopped while this projection is rebuilt.
-- ---------------------------------------------------------------------------

LOCK TABLE task_canvas_definition IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE IF NOT EXISTS task_canvas_model_reference (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    task_id uuid NOT NULL,
    node_id uuid NOT NULL,
    model_id uuid NOT NULL,
    reference_role varchar(16) NOT NULL,
    CONSTRAINT task_canvas_model_reference_pkey PRIMARY KEY (id),
    CONSTRAINT task_canvas_model_reference_role_check
        CHECK (reference_role IN ('INPUT', 'OUTPUT'))
);

DO $migration$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'task_canvas_model_reference'::regclass
          AND conname = 'task_canvas_model_reference_role_check'
    ) THEN
        ALTER TABLE task_canvas_model_reference
            ADD CONSTRAINT task_canvas_model_reference_role_check
            CHECK (reference_role IN ('INPUT', 'OUTPUT'));
    END IF;
END
$migration$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_canvas_model_reference_task_node
    ON task_canvas_model_reference (task_id, node_id);

CREATE INDEX IF NOT EXISTS idx_task_canvas_model_reference_task
    ON task_canvas_model_reference (task_id);

CREATE INDEX IF NOT EXISTS idx_task_canvas_model_reference_model
    ON task_canvas_model_reference (model_id);

LOCK TABLE task_canvas_model_reference IN SHARE ROW EXCLUSIVE MODE;

-- Refuse unsafe or internally inconsistent definitions before changing data.
-- An incomplete draft may keep an empty model UUID, but every non-empty model
-- reference and every model-node ID must be a valid UUID.
DO $migration$
DECLARE
    definition_row record;
    definition_document jsonb;
    node_document jsonb;
    document_schema_version integer;
    document_schema_minor_version integer;
    node_id_value text;
    model_id_value text;
BEGIN
    FOR definition_row IN
        SELECT id, task_id, schema_version, schema_minor_version, definition_json
        FROM task_canvas_definition
        ORDER BY id
    LOOP
        BEGIN
            definition_document := definition_row.definition_json::jsonb;
        EXCEPTION WHEN others THEN
            RAISE EXCEPTION 'Canvas definition % contains invalid JSON', definition_row.id;
        END;

        IF jsonb_typeof(definition_document) <> 'object'
                OR jsonb_typeof(definition_document -> 'nodes') <> 'array'
                OR jsonb_typeof(definition_document -> 'edges') <> 'array' THEN
            RAISE EXCEPTION 'Canvas definition % is not a loadable object with nodes and edges arrays',
                definition_row.id;
        END IF;

        IF COALESCE(definition_document ->> 'schemaVersion', '') !~ '^[0-9]+$' THEN
            RAISE EXCEPTION 'Canvas definition % has no numeric schemaVersion', definition_row.id;
        END IF;
        document_schema_version := (definition_document ->> 'schemaVersion')::integer;
        IF definition_document ? 'schemaMinorVersion'
                AND COALESCE(definition_document ->> 'schemaMinorVersion', '') !~ '^[0-9]+$' THEN
            RAISE EXCEPTION 'Canvas definition % has a non-numeric schemaMinorVersion', definition_row.id;
        END IF;
        document_schema_minor_version := COALESCE(
            (definition_document ->> 'schemaMinorVersion')::integer,
            0
        );
        IF definition_row.schema_version NOT IN (1, 2)
                OR document_schema_version NOT IN (1, 2)
                OR definition_row.schema_version <> document_schema_version THEN
            RAISE EXCEPTION 'Canvas definition % has unsupported or inconsistent schema versions: column %, JSON %',
                definition_row.id, definition_row.schema_version, document_schema_version;
        END IF;
        IF definition_row.schema_version = 1
                AND (definition_row.schema_minor_version NOT IN (0, 1)
                    OR document_schema_minor_version NOT IN (0, 1)
                    OR definition_row.schema_minor_version <> document_schema_minor_version) THEN
            RAISE EXCEPTION 'Canvas definition % has unsupported or inconsistent schema minor versions: column %, JSON %',
                definition_row.id, definition_row.schema_minor_version, document_schema_minor_version;
        END IF;
        IF definition_row.schema_version = 2 AND document_schema_minor_version NOT IN (0, 1) THEN
            RAISE EXCEPTION 'Interim Canvas v2 definition % has unsupported schemaMinorVersion %',
                definition_row.id, document_schema_minor_version;
        END IF;

        IF definition_row.schema_version = 1
                AND document_schema_minor_version = 0
                AND EXISTS (
                    SELECT 1
                    FROM jsonb_array_elements(definition_document -> 'nodes') node_row(model_node)
                    WHERE model_node ->> 'type' IN ('MODEL_INPUT', 'MODEL_OUTPUT')
                ) THEN
            RAISE EXCEPTION 'Canvas definition % declares 1.0 but contains MODEL_INPUT or MODEL_OUTPUT',
                definition_row.id;
        END IF;

        FOR node_document IN
            SELECT value
            FROM jsonb_array_elements(definition_document -> 'nodes')
            WHERE value ->> 'type' IN ('MODEL_INPUT', 'MODEL_OUTPUT')
        LOOP
            node_id_value := COALESCE(node_document ->> 'id', '');
            IF node_id_value !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN
                RAISE EXCEPTION 'Canvas definition % has a model node with invalid UUID id: %',
                    definition_row.id, node_id_value;
            END IF;
            IF jsonb_typeof(node_document -> 'configuration') <> 'object' THEN
                RAISE EXCEPTION 'Canvas definition % model node % has no configuration object',
                    definition_row.id, node_id_value;
            END IF;

            model_id_value := CASE node_document ->> 'type'
                WHEN 'MODEL_INPUT' THEN COALESCE(node_document #>> '{configuration,modelId}', '')
                ELSE COALESCE(node_document #>> '{configuration,targetModelId}', '')
            END;
            IF model_id_value <> ''
                    AND model_id_value !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN
                RAISE EXCEPTION 'Canvas definition % model node % has invalid model UUID: %',
                    definition_row.id, node_id_value, model_id_value;
            END IF;
        END LOOP;
    END LOOP;
END
$migration$;

-- During development an unreleased interim implementation wrote model nodes
-- as major version 2. Normalize only those interim rows to the final 1.1
-- contract. Formal 1.0 rows stay byte-for-byte unchanged and remain readable.
UPDATE task_canvas_definition
SET schema_version = 1,
    schema_minor_version = 1,
    definition_json = jsonb_set(
        jsonb_set(
            definition_json::jsonb,
            '{schemaVersion}',
            to_jsonb(1),
            false
        ),
        '{schemaMinorVersion}',
        to_jsonb(1),
        true
    )::text,
    updated_at = CURRENT_TIMESTAMP
WHERE schema_version = 2;

DELETE FROM task_canvas_model_reference;

INSERT INTO task_canvas_model_reference (
    id, created_at, updated_at, task_id, node_id, model_id, reference_role
)
SELECT
    md5(definition_row.task_id::text || ':' || (node_document ->> 'id'))::uuid,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    definition_row.task_id,
    (node_document ->> 'id')::uuid,
    (CASE node_document ->> 'type'
        WHEN 'MODEL_INPUT' THEN node_document #>> '{configuration,modelId}'
        ELSE node_document #>> '{configuration,targetModelId}'
    END)::uuid,
    CASE node_document ->> 'type'
        WHEN 'MODEL_INPUT' THEN 'INPUT'
        ELSE 'OUTPUT'
    END
FROM task_canvas_definition definition_row
CROSS JOIN LATERAL jsonb_array_elements(definition_row.definition_json::jsonb -> 'nodes')
    AS node_row(node_document)
WHERE node_document ->> 'type' IN ('MODEL_INPUT', 'MODEL_OUTPUT')
  AND CASE node_document ->> 'type'
      WHEN 'MODEL_INPUT' THEN COALESCE(node_document #>> '{configuration,modelId}', '')
      ELSE COALESCE(node_document #>> '{configuration,targetModelId}', '')
  END <> '';

-- ---------------------------------------------------------------------------
-- 5. Admin -> Kafka transactional Outbox
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS task_execution_outbox (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    message_id uuid NOT NULL,
    aggregate_id uuid NOT NULL,
    execution_id uuid NOT NULL,
    engine_id uuid NOT NULL,
    topic varchar(249) NOT NULL,
    message_type varchar(64) NOT NULL,
    payload text NOT NULL,
    state varchar(32) NOT NULL,
    attempts integer NOT NULL,
    next_attempt_at timestamptz NOT NULL,
    claimed_at timestamptz,
    published_at timestamptz,
    last_error varchar(1000),
    CONSTRAINT task_execution_outbox_pkey PRIMARY KEY (id),
    CONSTRAINT task_execution_outbox_state_check
        CHECK (state IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_execution_outbox_message
    ON task_execution_outbox (message_id);

CREATE INDEX IF NOT EXISTS idx_task_execution_outbox_due
    ON task_execution_outbox (state, next_attempt_at);

-- ---------------------------------------------------------------------------
-- 6. Dispatcher -> Admin idempotent event Inbox
--
-- The current JPA entity maps this technical integration table to
-- dispatcher_event_inbox. Keep this name until the entity is renamed too.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS dispatcher_event_inbox (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    message_id uuid NOT NULL,
    execution_id uuid NOT NULL,
    run_id uuid NOT NULL,
    attempt integer NOT NULL,
    sequence bigint NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz,
    processing_state varchar(32) NOT NULL,
    safe_error varchar(1000),
    CONSTRAINT dispatcher_event_inbox_pkey PRIMARY KEY (id),
    CONSTRAINT dispatcher_event_inbox_processing_state_check
        CHECK (processing_state IN ('RECEIVED', 'PROCESSED', 'REJECTED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dispatcher_event_inbox_message
    ON dispatcher_event_inbox (message_id);

CREATE INDEX IF NOT EXISTS idx_dispatcher_event_inbox_execution
    ON dispatcher_event_inbox (execution_id, sequence);

COMMIT;

-- Verification queries. They intentionally run after COMMIT and do not change
-- data. Review the returned enum counts, schema versions, reference counts and
-- the six expected table names.
SELECT type, count(*)
FROM task
GROUP BY type
ORDER BY type;

SELECT task_type, status, count(*)
FROM task_run
GROUP BY task_type, status
ORDER BY task_type, status;

SELECT schema_version, schema_minor_version, count(*)
FROM task_canvas_definition
GROUP BY schema_version, schema_minor_version
ORDER BY schema_version, schema_minor_version;

SELECT reference_role, count(*)
FROM task_canvas_model_reference
GROUP BY reference_role
ORDER BY reference_role;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN (
      'task', 'task_run', 'task_canvas_definition',
      'task_canvas_model_reference', 'task_execution_outbox',
      'dispatcher_event_inbox'
  )
ORDER BY table_name;
