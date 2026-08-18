-- SPARK_STREAMING_JAR incremental migration for the Admin PostgreSQL database.
--
-- Apply after spark-jar-admin-postgresql.sql and
-- spark-streaming-canvas-admin-postgresql.sql with the Admin application
-- stopped. Existing tasks, runs, deployments and checkpoints are preserved.

BEGIN;

SET LOCAL search_path TO public;
SET LOCAL lock_timeout TO '10s';
SET LOCAL statement_timeout TO '5min';

DO $migration$
BEGIN
    IF to_regclass('task') IS NULL
       OR to_regclass('task_run') IS NULL
       OR to_regclass('task_spark_jar_definition') IS NULL
       OR to_regclass('task_spark_jar_resource_binding') IS NULL
       OR to_regclass('task_streaming_deployment') IS NULL
       OR to_regclass('task_streaming_query') IS NULL THEN
        RAISE EXCEPTION 'Spark JAR and streaming Canvas migrations must be applied first';
    END IF;
END
$migration$;

LOCK TABLE task, task_run, task_spark_jar_definition,
    task_spark_jar_resource_binding, task_streaming_deployment,
    task_streaming_query IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE task_spark_jar_definition
    ADD COLUMN IF NOT EXISTS job_mode varchar(16);
UPDATE task_spark_jar_definition SET job_mode = 'BATCH' WHERE job_mode IS NULL;
ALTER TABLE task_spark_jar_definition
    ALTER COLUMN job_mode SET DEFAULT 'BATCH',
    ALTER COLUMN job_mode SET NOT NULL;

ALTER TABLE task_spark_jar_resource_binding
    ADD COLUMN IF NOT EXISTS topic_name varchar(249);

ALTER TABLE task_streaming_deployment
    ADD COLUMN IF NOT EXISTS checkpoint_generation integer,
    ADD COLUMN IF NOT EXISTS checkpoint_start_mode varchar(16),
    ADD COLUMN IF NOT EXISTS checkpoint_source_deployment_id uuid;
UPDATE task_streaming_deployment
SET checkpoint_generation = 1,
    checkpoint_start_mode = 'FRESH',
    checkpoint_source_deployment_id = NULL
WHERE checkpoint_generation IS NULL OR checkpoint_start_mode IS NULL;
ALTER TABLE task_streaming_deployment
    ALTER COLUMN checkpoint_generation SET DEFAULT 1,
    ALTER COLUMN checkpoint_generation SET NOT NULL,
    ALTER COLUMN checkpoint_start_mode SET DEFAULT 'FRESH',
    ALTER COLUMN checkpoint_start_mode SET NOT NULL;

DO $migration$
DECLARE
    matched_constraint record;
BEGIN
    FOR matched_constraint IN
        SELECT DISTINCT constraint_row.conrelid, constraint_row.conname
        FROM pg_constraint constraint_row
        JOIN pg_attribute attribute_row
          ON attribute_row.attrelid = constraint_row.conrelid
         AND attribute_row.attnum = ANY (constraint_row.conkey)
        WHERE constraint_row.contype = 'c'
          AND (
            constraint_row.conrelid = 'task'::regclass AND attribute_row.attname = 'type'
            OR constraint_row.conrelid = 'task_run'::regclass
               AND attribute_row.attname IN ('task_type', 'user_jar_size_bytes', 'user_jar_cleanup_status')
            OR constraint_row.conrelid = 'task_spark_jar_definition'::regclass
               AND attribute_row.attname = 'job_mode'
            OR constraint_row.conrelid = 'task_spark_jar_resource_binding'::regclass
               AND attribute_row.attname IN ('resource_type', 'topic_name')
            OR constraint_row.conrelid = 'task_streaming_deployment'::regclass
               AND attribute_row.attname IN ('checkpoint_generation', 'checkpoint_start_mode', 'checkpoint_source_deployment_id')
            OR constraint_row.conrelid = 'task_streaming_query'::regclass
               AND attribute_row.attname = 'sink_type'
          )
    LOOP
        EXECUTE format(
            'ALTER TABLE %s DROP CONSTRAINT %I',
            matched_constraint.conrelid::regclass,
            matched_constraint.conname
        );
    END LOOP;
END
$migration$;

ALTER TABLE task
    ADD CONSTRAINT task_type_check
    CHECK (type IN (
        'LOCAL_SQL', 'SPARK_CANVAS', 'SPARK_STREAMING_CANVAS',
        'SPARK_MODEL_QUALITY', 'SPARK_JAR', 'SPARK_STREAMING_JAR'
    ));

ALTER TABLE task_run
    ADD CONSTRAINT task_run_task_type_check
    CHECK (task_type IN (
        'LOCAL_SQL', 'SPARK_CANVAS', 'SPARK_STREAMING_CANVAS',
        'SPARK_MODEL_QUALITY', 'SPARK_JAR', 'SPARK_STREAMING_JAR'
    )),
    ADD CONSTRAINT task_run_user_jar_cleanup_status_check
    CHECK (user_jar_cleanup_status IS NULL OR user_jar_cleanup_status IN ('PENDING', 'COMPLETED')),
    ADD CONSTRAINT task_run_user_jar_metadata_check
    CHECK (
        task_type NOT IN ('SPARK_JAR', 'SPARK_STREAMING_JAR')
        OR (user_jar_size_bytes IS NULL OR user_jar_size_bytes BETWEEN 1 AND 104857600)
    );

ALTER TABLE task_spark_jar_definition
    ADD CONSTRAINT task_spark_jar_definition_job_mode_check
    CHECK (job_mode IN ('BATCH', 'STREAMING'));

ALTER TABLE task_spark_jar_resource_binding
    ADD CONSTRAINT task_spark_jar_binding_resource_type_check
    CHECK (resource_type IN ('MODEL', 'JDBC_DATA_SOURCE', 'KAFKA_TOPIC')),
    ADD CONSTRAINT task_spark_jar_binding_topic_check
    CHECK (
        resource_type = 'KAFKA_TOPIC' AND topic_name IS NOT NULL AND btrim(topic_name) <> ''
        OR resource_type <> 'KAFKA_TOPIC' AND topic_name IS NULL
    );

ALTER TABLE task_streaming_deployment
    ADD CONSTRAINT task_streaming_deployment_checkpoint_generation_check
    CHECK (checkpoint_generation >= 1),
    ADD CONSTRAINT task_streaming_deployment_checkpoint_mode_check
    CHECK (checkpoint_start_mode IN ('CONTINUE', 'FRESH')),
    ADD CONSTRAINT task_streaming_deployment_checkpoint_source_check
    CHECK (
        checkpoint_start_mode = 'CONTINUE' AND checkpoint_source_deployment_id IS NOT NULL
        OR checkpoint_start_mode = 'FRESH' AND checkpoint_source_deployment_id IS NULL
    );

ALTER TABLE task_streaming_deployment
    DROP CONSTRAINT IF EXISTS uk_task_streaming_deployment_version;
DROP INDEX IF EXISTS uk_task_streaming_deployment_version;
CREATE UNIQUE INDEX uk_task_streaming_deployment_version
    ON task_streaming_deployment (task_id, definition_version, checkpoint_generation);

ALTER TABLE task_streaming_query
    ADD CONSTRAINT task_streaming_query_sink_type_check
    CHECK (sink_type IN ('KAFKA', 'JDBC', 'CUSTOM'));

COMMIT;
