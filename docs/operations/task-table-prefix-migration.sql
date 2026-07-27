-- One-time PostgreSQL migration for the DataScalpel task table prefix change.
-- Run this script before starting the application version that maps task_* tables.

BEGIN;

DO $$
DECLARE
    old_name text;
    new_name text;
BEGIN
    FOR old_name, new_name IN
        SELECT * FROM (VALUES
            ('ds_data_task', 'task'),
            ('ds_local_sql_task_definition', 'task_local_sql_definition'),
            ('ds_local_sql_task_input', 'task_local_sql_input'),
            ('ds_task_run', 'task_run')
        ) AS names(old_name, new_name)
    LOOP
        IF to_regclass(old_name) IS NOT NULL AND to_regclass(new_name) IS NOT NULL THEN
            RAISE EXCEPTION 'Cannot migrate: both % and % exist', old_name, new_name;
        END IF;
        IF to_regclass(old_name) IS NULL AND to_regclass(new_name) IS NULL THEN
            RAISE EXCEPTION 'Cannot migrate: neither % nor % exists', old_name, new_name;
        END IF;
    END LOOP;
END $$;

DO $$
BEGIN
    IF to_regclass('ds_data_task') IS NOT NULL THEN
        ALTER TABLE ds_data_task RENAME TO task;
    END IF;
    IF to_regclass('ds_local_sql_task_definition') IS NOT NULL THEN
        ALTER TABLE ds_local_sql_task_definition RENAME TO task_local_sql_definition;
    END IF;
    IF to_regclass('ds_local_sql_task_input') IS NOT NULL THEN
        ALTER TABLE ds_local_sql_task_input RENAME TO task_local_sql_input;
    END IF;
    IF to_regclass('ds_task_run') IS NOT NULL THEN
        ALTER TABLE ds_task_run RENAME TO task_run;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ds_data_task_pkey' AND conrelid = 'task'::regclass) THEN
        ALTER TABLE task RENAME CONSTRAINT ds_data_task_pkey TO task_pkey;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ds_local_sql_task_definition_pkey' AND conrelid = 'task_local_sql_definition'::regclass) THEN
        ALTER TABLE task_local_sql_definition
            RENAME CONSTRAINT ds_local_sql_task_definition_pkey TO task_local_sql_definition_pkey;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ds_local_sql_task_input_pkey' AND conrelid = 'task_local_sql_input'::regclass) THEN
        ALTER TABLE task_local_sql_input
            RENAME CONSTRAINT ds_local_sql_task_input_pkey TO task_local_sql_input_pkey;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ds_task_run_pkey' AND conrelid = 'task_run'::regclass) THEN
        ALTER TABLE task_run RENAME CONSTRAINT ds_task_run_pkey TO task_run_pkey;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_ds_local_sql_task_definition_task' AND conrelid = 'task_local_sql_definition'::regclass) THEN
        ALTER TABLE task_local_sql_definition
            RENAME CONSTRAINT uk_ds_local_sql_task_definition_task TO uk_task_local_sql_definition_task;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_ds_local_sql_task_input_model' AND conrelid = 'task_local_sql_input'::regclass) THEN
        ALTER TABLE task_local_sql_input
            RENAME CONSTRAINT uk_ds_local_sql_task_input_model TO uk_task_local_sql_input_model;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_ds_local_sql_task_input_order' AND conrelid = 'task_local_sql_input'::regclass) THEN
        ALTER TABLE task_local_sql_input
            RENAME CONSTRAINT uk_ds_local_sql_task_input_order TO uk_task_local_sql_input_order;
    END IF;
END $$;

-- Task identity is UUID-based. The former human-maintained code and its dependent
-- unique constraint/index are removed together by PostgreSQL.
ALTER TABLE task DROP COLUMN IF EXISTS code;

ALTER TABLE task_run ADD COLUMN IF NOT EXISTS schedule_id uuid;
ALTER TABLE task_run ADD COLUMN IF NOT EXISTS scheduled_fire_at timestamptz;
ALTER TABLE task_run ADD COLUMN IF NOT EXISTS execution_mode varchar(32);

UPDATE task_run SET trigger_type = 'MANUAL' WHERE trigger_type IS NULL;
UPDATE task_run SET execution_mode = 'REAL' WHERE execution_mode IS NULL;
ALTER TABLE task_run ALTER COLUMN trigger_type SET NOT NULL;
ALTER TABLE task_run ALTER COLUMN execution_mode SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_task_run_schedule_fire' AND conrelid = 'task_run'::regclass
    ) THEN
        ALTER TABLE task_run ADD CONSTRAINT uk_task_run_schedule_fire UNIQUE (schedule_id, scheduled_fire_at);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_task_run_task_queued
    ON task_run (task_id, queued_at);
CREATE INDEX IF NOT EXISTS idx_task_run_task_status
    ON task_run (task_id, status);

COMMIT;
