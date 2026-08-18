-- SPARK_JAR incremental migration for the Admin PostgreSQL database.
--
-- Run with the Admin application stopped after the existing task/Canvas
-- migrations. Existing tasks and runs are preserved; no table is rebuilt or
-- truncated. Re-running the script is safe.

BEGIN;

SET LOCAL search_path TO public;
SET LOCAL lock_timeout TO '10s';
SET LOCAL statement_timeout TO '5min';

DO $migration$
BEGIN
    IF to_regclass('task') IS NULL OR to_regclass('task_run') IS NULL THEN
        RAISE EXCEPTION 'task and task_run are required; run the existing task migrations first';
    END IF;
END
$migration$;

LOCK TABLE task, task_run IN SHARE ROW EXCLUSIVE MODE;

-- Hibernate ddl-auto=update does not replace an enum CHECK when TaskType grows.
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
    CHECK (type IN (
        'LOCAL_SQL', 'SPARK_CANVAS', 'SPARK_STREAMING_CANVAS',
        'SPARK_MODEL_QUALITY', 'SPARK_JAR'
    ));

ALTER TABLE task_run
    ADD COLUMN IF NOT EXISTS user_jar_file_name varchar(255),
    ADD COLUMN IF NOT EXISTS user_jar_sha256 varchar(64),
    ADD COLUMN IF NOT EXISTS user_jar_size_bytes bigint,
    ADD COLUMN IF NOT EXISTS run_user_jar_object_key varchar(500),
    ADD COLUMN IF NOT EXISTS user_jar_cleanup_status varchar(16);

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
          AND attribute_row.attname IN ('task_type', 'user_jar_cleanup_status')
    LOOP
        EXECUTE format('ALTER TABLE task_run DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END
$migration$;

ALTER TABLE task_run
    ADD CONSTRAINT task_run_task_type_check
    CHECK (task_type IN (
        'LOCAL_SQL', 'SPARK_CANVAS', 'SPARK_STREAMING_CANVAS',
        'SPARK_MODEL_QUALITY', 'SPARK_JAR'
    )),
    ADD CONSTRAINT task_run_user_jar_cleanup_status_check
    CHECK (user_jar_cleanup_status IS NULL OR user_jar_cleanup_status IN ('PENDING', 'COMPLETED')),
    ADD CONSTRAINT task_run_user_jar_metadata_check
    CHECK (
        task_type <> 'SPARK_JAR'
        OR (user_jar_size_bytes IS NULL OR user_jar_size_bytes BETWEEN 1 AND 104857600)
    );

CREATE INDEX IF NOT EXISTS idx_task_run_user_jar_cleanup
    ON task_run (user_jar_cleanup_status, ended_at)
    WHERE user_jar_cleanup_status = 'PENDING';

CREATE TABLE IF NOT EXISTS task_spark_jar_definition (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    task_id uuid NOT NULL,
    jar_object_key varchar(500),
    jar_file_name varchar(255),
    jar_sha256 varchar(64),
    jar_size_bytes bigint,
    job_class varchar(500),
    job_api_version integer,
    parameters_json text NOT NULL DEFAULT '[]',
    spark_conf_json text NOT NULL DEFAULT '[]',
    timeout_seconds integer NOT NULL DEFAULT 3600,
    version integer NOT NULL DEFAULT 1,
    CONSTRAINT task_spark_jar_definition_pkey PRIMARY KEY (id),
    CONSTRAINT task_spark_jar_definition_timeout_check
        CHECK (timeout_seconds BETWEEN 1 AND 86400),
    CONSTRAINT task_spark_jar_definition_jar_size_check
        CHECK (jar_size_bytes IS NULL OR jar_size_bytes BETWEEN 1 AND 104857600),
    CONSTRAINT task_spark_jar_definition_version_check
        CHECK (version >= 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_spark_jar_definition_task
    ON task_spark_jar_definition (task_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_task_spark_jar_definition_object
    ON task_spark_jar_definition (jar_object_key)
    WHERE jar_object_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS task_spark_jar_resource_binding (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    task_id uuid NOT NULL,
    binding_name varchar(100) NOT NULL,
    resource_type varchar(32) NOT NULL,
    resource_id uuid NOT NULL,
    access_mode varchar(16) NOT NULL,
    CONSTRAINT task_spark_jar_resource_binding_pkey PRIMARY KEY (id),
    CONSTRAINT task_spark_jar_binding_resource_type_check
        CHECK (resource_type IN ('MODEL', 'JDBC_DATA_SOURCE')),
    CONSTRAINT task_spark_jar_binding_access_mode_check
        CHECK (access_mode IN ('READ', 'WRITE', 'READ_WRITE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_spark_jar_binding_name
    ON task_spark_jar_resource_binding (task_id, binding_name);
CREATE INDEX IF NOT EXISTS idx_task_spark_jar_binding_task
    ON task_spark_jar_resource_binding (task_id);
CREATE INDEX IF NOT EXISTS idx_task_spark_jar_binding_resource
    ON task_spark_jar_resource_binding (resource_type, resource_id);

COMMIT;

