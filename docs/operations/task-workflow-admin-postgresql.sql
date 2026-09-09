-- TaskWorkflow V1, Admin PostgreSQL only. Apply to an existing schema while Admin is stopped.
-- New installations can create all tables through the existing Hibernate ddl-auto=update.
BEGIN;
SET LOCAL search_path TO public;
SET LOCAL lock_timeout TO '10s';
SET LOCAL statement_timeout TO '5min';

CREATE TABLE IF NOT EXISTS task_workflow_definition (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    task_id uuid NOT NULL,
    version integer NOT NULL,
    definition_json text NOT NULL,
    CONSTRAINT uk_task_workflow_definition_task UNIQUE (task_id)
);
ALTER TABLE task_run ADD COLUMN IF NOT EXISTS parent_run_id uuid,
                     ADD COLUMN IF NOT EXISTS workflow_node_id varchar(128);
CREATE INDEX IF NOT EXISTS idx_task_run_parent ON task_run (parent_run_id);
DO $migration$
DECLARE c record;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'task_run'::regclass AND conname = 'uk_task_run_workflow_node') THEN
        ALTER TABLE task_run ADD CONSTRAINT uk_task_run_workflow_node UNIQUE (parent_run_id, workflow_node_id);
    END IF;
    -- Replace only the single-column enum checks, preserving other business constraints.
    FOR c IN SELECT p.conrelid, p.conname FROM pg_constraint p
        JOIN pg_attribute a ON a.attrelid = p.conrelid AND a.attnum = p.conkey[1]
        WHERE p.contype = 'c' AND cardinality(p.conkey) = 1
          AND ((p.conrelid = 'task'::regclass AND a.attname = 'type')
            OR (p.conrelid = 'task_run'::regclass AND a.attname IN ('task_type', 'trigger_type')))
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', c.conrelid::regclass, c.conname);
    END LOOP;
END
$migration$;
ALTER TABLE task ADD CONSTRAINT task_type_check CHECK (type IN (
    'LOCAL_SQL', 'WORKFLOW', 'SPARK_CANVAS', 'SPARK_STREAMING_CANVAS', 'SPARK_MODEL_QUALITY', 'SPARK_JAR', 'SPARK_STREAMING_JAR'));
ALTER TABLE task_run ADD CONSTRAINT task_run_task_type_check CHECK (task_type IN (
    'LOCAL_SQL', 'WORKFLOW', 'SPARK_CANVAS', 'SPARK_STREAMING_CANVAS', 'SPARK_MODEL_QUALITY', 'SPARK_JAR', 'SPARK_STREAMING_JAR'));
ALTER TABLE task_run ADD CONSTRAINT task_run_trigger_type_check CHECK (trigger_type IN ('MANUAL', 'SCHEDULED', 'WORKFLOW'));
CREATE INDEX IF NOT EXISTS idx_task_execution_outbox_submission ON task_execution_outbox (aggregate_id, execution_id, message_type, state);
COMMIT;
