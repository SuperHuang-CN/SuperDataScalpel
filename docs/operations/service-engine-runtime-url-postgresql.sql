-- Align an existing Admin database with the Service Engine and gateway-publication changes.
--
-- Hibernate ddl-auto=update can add columns, but it cannot safely infer that
-- public_url was renamed to runtime_url, nor can it safely add new non-null
-- gateway-publication fields to a table that may already contain records. Run
-- this once against the DataScalpel Admin PostgreSQL database before starting
-- the updated Admin application. The script is idempotent.

BEGIN;

SET LOCAL lock_timeout TO '10s';
SET LOCAL statement_timeout TO '1min';

DO $migration$
BEGIN
    IF to_regclass('public.ds_service_engine') IS NULL THEN
        RAISE EXCEPTION 'Table public.ds_service_engine does not exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ds_service_engine'
          AND column_name = 'public_url'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ds_service_engine'
          AND column_name = 'runtime_url'
    ) THEN
        ALTER TABLE public.ds_service_engine RENAME COLUMN public_url TO runtime_url;
    ELSIF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ds_service_engine'
          AND column_name = 'runtime_url'
    ) THEN
        RAISE EXCEPTION 'Neither public_url nor runtime_url exists on public.ds_service_engine';
    END IF;
END;
$migration$;

ALTER TABLE IF EXISTS public.ds_gateway_service_binding
    ADD COLUMN IF NOT EXISTS gateway_route_path varchar(255);
ALTER TABLE IF EXISTS public.ds_gateway_service_binding
    ADD COLUMN IF NOT EXISTS access_mode varchar(32);

DO $migration$
BEGIN
    IF to_regclass('public.ds_gateway_service_binding') IS NULL THEN
        RETURN;
    END IF;

    -- The previous version kept these publication settings on ds_data_service.
    -- Preserve them for any existing gateway binding before making the new
    -- binding-level columns mandatory.
    IF to_regclass('public.ds_data_service') IS NOT NULL
       AND EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'ds_data_service'
             AND column_name = 'route_path'
       )
       AND EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'ds_data_service'
             AND column_name = 'access_mode'
       ) THEN
        UPDATE public.ds_gateway_service_binding binding
        SET gateway_route_path = CASE
                    WHEN binding.gateway_route_path IS NULL OR binding.gateway_route_path = ''
                    THEN data_service.route_path
                    ELSE binding.gateway_route_path
                END,
            access_mode = CASE
                    WHEN binding.access_mode IS NULL OR binding.access_mode = ''
                    THEN data_service.access_mode
                    ELSE binding.access_mode
                END
        FROM public.ds_data_service data_service
        WHERE binding.data_service_id = data_service.id
          AND (
              binding.gateway_route_path IS NULL OR binding.gateway_route_path = ''
              OR binding.access_mode IS NULL OR binding.access_mode = ''
          );
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.ds_gateway_service_binding
        WHERE gateway_route_path IS NULL OR gateway_route_path = ''
           OR access_mode IS NULL OR access_mode = ''
    ) THEN
        RAISE EXCEPTION
            'Existing gateway bindings could not be backfilled. Restore their legacy ds_data_service.route_path/access_mode values or remove the incomplete bindings, then rerun this script.';
    END IF;

    ALTER TABLE public.ds_gateway_service_binding
        ALTER COLUMN gateway_route_path SET NOT NULL;
    ALTER TABLE public.ds_gateway_service_binding
        ALTER COLUMN access_mode SET NOT NULL;
END;
$migration$;

COMMIT;

SELECT column_name, data_type, is_nullable, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'ds_service_engine'
  AND column_name IN ('admin_url', 'runtime_url')
ORDER BY column_name;

SELECT column_name, data_type, is_nullable, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'ds_gateway_service_binding'
  AND column_name IN ('gateway_route_path', 'access_mode')
ORDER BY column_name;
