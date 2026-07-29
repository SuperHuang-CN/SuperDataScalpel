-- Remove obsolete compute-engine configuration and Dispatcher protocol versions.
--
-- Run during a coordinated maintenance window after stopping the old Admin and
-- Dispatcher processes. The script is idempotent and does not remove the JPA
-- optimistic-lock "version" column or revision columns owned by other domains.

BEGIN;

SET LOCAL lock_timeout TO '10s';
SET LOCAL statement_timeout TO '5min';

ALTER TABLE IF EXISTS public.compute_engine
    DROP COLUMN IF EXISTS config_revision;
ALTER TABLE IF EXISTS public.compute_engine
    DROP COLUMN IF EXISTS protocol_version;

ALTER TABLE IF EXISTS dispatcher69.dispatcher_registration
    DROP COLUMN IF EXISTS config_revision;
ALTER TABLE IF EXISTS dispatcher69.dispatcher_registration
    DROP COLUMN IF EXISTS protocol_version;

-- Clean up the legacy local Dispatcher schema when it exists.
ALTER TABLE IF EXISTS dispatcher.dispatcher_registration
    DROP COLUMN IF EXISTS config_revision;
ALTER TABLE IF EXISTS dispatcher.dispatcher_registration
    DROP COLUMN IF EXISTS protocol_version;

COMMIT;

SELECT table_schema, table_name, column_name
FROM information_schema.columns
WHERE (table_schema, table_name) IN (
          ('public', 'compute_engine'),
          ('dispatcher69', 'dispatcher_registration'),
          ('dispatcher', 'dispatcher_registration')
      )
  AND column_name IN ('config_revision', 'protocol_version')
ORDER BY table_schema, table_name, column_name;
