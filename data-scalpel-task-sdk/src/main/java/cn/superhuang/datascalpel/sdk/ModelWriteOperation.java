package cn.superhuang.datascalpel.sdk;

public interface ModelWriteOperation {
    ModelWriteOperation mode(ModelWriteMode mode);

    /** Maps a target column to a source Dataset column. */
    ModelWriteOperation map(String targetColumnName, String sourceColumnName);

    WriteResult execute();
}
