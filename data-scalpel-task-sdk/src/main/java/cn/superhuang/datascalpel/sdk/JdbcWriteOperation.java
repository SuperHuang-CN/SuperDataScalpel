package cn.superhuang.datascalpel.sdk;

public interface JdbcWriteOperation {
    JdbcWriteOperation table(JdbcTableIdentifier table);

    JdbcWriteOperation mode(JdbcWriteMode mode);

    JdbcWriteOperation upsertKeyColumns(String... targetColumnNames);

    /** Maps a target column to a source Dataset column. */
    JdbcWriteOperation map(String targetColumnName, String sourceColumnName);

    WriteResult execute();
}
