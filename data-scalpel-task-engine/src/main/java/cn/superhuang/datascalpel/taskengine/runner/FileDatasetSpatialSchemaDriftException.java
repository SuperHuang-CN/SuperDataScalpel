package cn.superhuang.datascalpel.taskengine.runner;

/** Internal signal that the physical spatial file no longer matches the protected manifest schema. */
final class FileDatasetSpatialSchemaDriftException extends IllegalArgumentException {

    FileDatasetSpatialSchemaDriftException(String detail) {
        super("FILE_SPATIAL_SCHEMA_DRIFT: " + detail);
    }
}
