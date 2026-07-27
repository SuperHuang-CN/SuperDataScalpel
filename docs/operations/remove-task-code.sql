-- One-time PostgreSQL migration for installations that already use the task table.
-- Task relations and APIs use the UUID primary key; no other task table is rebuilt.

BEGIN;

DO $$
BEGIN
    IF to_regclass('task') IS NULL THEN
        RAISE EXCEPTION 'Cannot remove task code: table task does not exist';
    END IF;
END $$;

-- PostgreSQL automatically removes the unique constraint and index that depend on
-- this column. IF EXISTS makes the script safe to rerun.
ALTER TABLE task DROP COLUMN IF EXISTS code;

COMMIT;
