-- SPARK_JAR incremental migration for the Dispatcher PostgreSQL database.
--
-- Run with the Dispatcher stopped. Existing executions and messages are
-- preserved; no table is rebuilt or truncated. Re-running the script is safe.

BEGIN;

SET LOCAL search_path TO dispatcher;
SET LOCAL lock_timeout TO '10s';
SET LOCAL statement_timeout TO '5min';

DO $migration$
BEGIN
    IF to_regclass('dispatcher_task_execution') IS NULL THEN
        RAISE EXCEPTION 'dispatcher.dispatcher_task_execution is required';
    END IF;
END
$migration$;

LOCK TABLE dispatcher_task_execution IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE dispatcher_task_execution
    ADD COLUMN IF NOT EXISTS user_jar_object_key varchar(500),
    ADD COLUMN IF NOT EXISTS user_jar_sha256 varchar(64),
    ADD COLUMN IF NOT EXISTS user_jar_size_bytes bigint,
    ADD COLUMN IF NOT EXISTS spark_conf text;

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
          AND attribute_row.attname IN ('task_type', 'user_jar_size_bytes')
    LOOP
        EXECUTE format('ALTER TABLE dispatcher_task_execution DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END
$migration$;

ALTER TABLE dispatcher_task_execution
    ADD CONSTRAINT dispatcher_task_execution_task_type_check
    CHECK (task_type IN (
        'SPARK_CANVAS', 'SPARK_STREAMING_CANVAS',
        'SPARK_MODEL_QUALITY', 'SPARK_JAR'
    )),
    ADD CONSTRAINT dispatcher_task_execution_user_jar_size_check
    CHECK (user_jar_size_bytes IS NULL OR user_jar_size_bytes BETWEEN 1 AND 104857600),
    ADD CONSTRAINT dispatcher_task_execution_user_jar_fields_check
    CHECK (
        task_type <> 'SPARK_JAR'
        OR (user_jar_object_key IS NOT NULL AND user_jar_sha256 IS NOT NULL AND user_jar_size_bytes IS NOT NULL)
    );

COMMIT;

