package cn.superhuang.datascalpel.sdk;

public interface ModelWriteOperation {
    ModelWriteOperation mode(ModelWriteMode mode);

    /** Maps a target column to a source Dataset column. */
    ModelWriteOperation map(String targetColumnName, String sourceColumnName);

    /** Maps every source Dataset column to the target column with the same name. */
    ModelWriteOperation mapSameName();

    /**
     * Validates source and target schemas when the current context supports local schema checks.
     * Production execution deliberately defers type conversion and physical constraints to Spark and
     * the target system.
     */
    ModelWriteOperation checkSchema();

    WriteResult execute();
}
