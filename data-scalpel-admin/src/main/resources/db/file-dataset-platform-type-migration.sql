-- File dataset field type migration for FILE_DATASET_INPUT.
--
-- This script is intentionally idempotent and can be executed before or after
-- starting the version that introduces PlatformTypeDefinition fields.

ALTER TABLE ds_file_dataset_field
    ADD COLUMN IF NOT EXISTS length integer,
    ADD COLUMN IF NOT EXISTS precision integer,
    ADD COLUMN IF NOT EXISTS scale integer;

UPDATE ds_file_dataset_field
SET logical_type = CASE logical_type
    WHEN 'INTEGER' THEN 'LONG'
    WHEN 'DATETIME' THEN 'TIMESTAMP_NTZ'
    WHEN 'TIME' THEN 'STRING'
    WHEN 'JSON' THEN 'STRING'
    WHEN 'ARRAY' THEN 'STRING'
    WHEN 'OTHER' THEN 'STRING'
    ELSE logical_type
END
WHERE logical_type IN (
    'INTEGER',
    'DATETIME',
    'TIME',
    'JSON',
    'ARRAY',
    'OTHER'
);

-- Historical DECIMAL metadata did not retain its parameters. Use a conservative
-- platform-valid default; newly parsed files persist their actual values.
UPDATE ds_file_dataset_field
SET precision = COALESCE(precision, 38),
    scale = LEAST(COALESCE(scale, 18), COALESCE(precision, 38))
WHERE logical_type = 'DECIMAL'
  AND (precision IS NULL OR scale IS NULL OR scale > precision);
