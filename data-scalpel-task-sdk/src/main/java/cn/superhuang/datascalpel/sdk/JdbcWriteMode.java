package cn.superhuang.datascalpel.sdk;

/**
 * JDBC 目标表写入方式。
 * @apiGroup 读写 JDBC
 */
public enum JdbcWriteMode {
    /**
     * 追加到目标表。
     */
    APPEND,
    /**
     * 覆盖目标表数据，使用前确认数据清理风险。
     */
    OVERWRITE,
    /**
     * 按指定目标键列更新或插入。
     */
    UPSERT
}
