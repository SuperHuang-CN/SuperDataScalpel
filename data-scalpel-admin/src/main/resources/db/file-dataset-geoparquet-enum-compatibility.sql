-- GEOPARQUET adds values to Java enums persisted as varchar columns.
-- Hibernate ddl-auto=update does not replace pre-existing enum CHECK constraints.
-- Run once after deploying GEOPARQUET-capable binaries and before creating GeoParquet datasets.

DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT constraint_definition.conname
        FROM pg_constraint constraint_definition
        JOIN pg_attribute attribute_definition
          ON attribute_definition.attrelid = constraint_definition.conrelid
         AND attribute_definition.attnum = ANY (constraint_definition.conkey)
        WHERE constraint_definition.conrelid = 'ds_file_dataset'::regclass
          AND constraint_definition.contype = 'c'
          AND attribute_definition.attname = 'type'
          AND pg_get_constraintdef(constraint_definition.oid) LIKE '%''CSV''%'
    LOOP
        EXECUTE format('ALTER TABLE ds_file_dataset DROP CONSTRAINT %I', constraint_name);
    END LOOP;

    FOR constraint_name IN
        SELECT constraint_definition.conname
        FROM pg_constraint constraint_definition
        JOIN pg_attribute attribute_definition
          ON attribute_definition.attrelid = constraint_definition.conrelid
         AND attribute_definition.attnum = ANY (constraint_definition.conkey)
        WHERE constraint_definition.conrelid = 'ds_file_dataset_file'::regclass
          AND constraint_definition.contype = 'c'
          AND attribute_definition.attname = 'format'
          AND pg_get_constraintdef(constraint_definition.oid) LIKE '%''CSV''%'
    LOOP
        EXECUTE format('ALTER TABLE ds_file_dataset_file DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE ds_file_dataset
    ADD CONSTRAINT ds_file_dataset_type_check
    CHECK (type IN (
        'CSV', 'TSV', 'TXT', 'JSON', 'JSONL', 'GEOJSON', 'GEOJSONL', 'GEOPARQUET',
        'PARQUET', 'AVRO', 'EXCEL', 'GDB', 'SHP'
    ));

ALTER TABLE ds_file_dataset_file
    ADD CONSTRAINT ds_file_dataset_file_format_check
    CHECK (format IN (
        'CSV', 'TSV', 'TXT', 'JSON', 'JSONL', 'GEOJSON', 'GEOJSONL', 'GEOPARQUET',
        'XLS', 'XLSX', 'PARQUET', 'AVRO', 'GDB', 'SHP'
    ));
