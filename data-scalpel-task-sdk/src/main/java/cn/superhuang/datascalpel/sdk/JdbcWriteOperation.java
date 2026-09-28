package cn.superhuang.datascalpel.sdk;

/**
 * 配置目标表、写入方式和字段映射。
 * @apiGroup 读写 JDBC
 * @apiNote UPSERT 必须指定目标键列，并依赖数据库约束；写入失败以数据库返回结果为准。
 */
public interface JdbcWriteOperation {
    /**
     * 设置写入的目标物理表。
     * @param table 目标物理表名称或结构化表标识。
     */
    JdbcWriteOperation table(JdbcTableIdentifier table);

    /**
     * 选择追加、覆盖或按键更新插入。
     * @param mode 目标写入方式。
     */
    JdbcWriteOperation mode(JdbcWriteMode mode);

    /**
     * 指定 UPSERT 使用的目标键列，可传多个。
     * @param targetColumnNames UPSERT 的目标键列，支持多列。
     */
    JdbcWriteOperation upsertKeyColumns(String... targetColumnNames);

    /**
     * 把来源字段映射到目标列：目标在前，来源在后。
     * @param targetColumnName 目标字段名称。
     * @param sourceColumnName 来源 Dataset 字段名称。
     */
    JdbcWriteOperation map(String targetColumnName, String sourceColumnName);

    /**
     * 执行当前 JDBC 写入并返回影响行数信息。
     */
    WriteResult execute();
}
