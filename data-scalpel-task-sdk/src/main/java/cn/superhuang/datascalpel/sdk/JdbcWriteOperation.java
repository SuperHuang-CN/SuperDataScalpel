package cn.superhuang.datascalpel.sdk;

/**
 * 配置目标表、写入方式和字段映射。
 * @apiGroup 读写 JDBC
 * @apiNote UPSERT 必须指定目标键列，并依赖数据库约束；写入失败以数据库返回结果为准。
 */
public interface JdbcWriteOperation {
    /**
     * 启用单目标原子批写；实时任务不支持，不会自动降级。
     * @param options 原子提交及覆盖范围，不能为空。
     */
    default JdbcWriteOperation batchWrite(BatchWriteOptions options) {
        throw new UnsupportedOperationException("当前运行时不支持原子批写");
    }

    /**
     * 声明目标 Geometry 字段的 EPSG SRID；不改变坐标。
     * @param targetColumnName 参与映射的目标 Geometry 字段。
     * @param epsg 正整数 EPSG 编码，需与数据坐标一致。
     */
    default JdbcWriteOperation geometrySrid(String targetColumnName, int epsg) {
        throw new UnsupportedOperationException("当前运行时不支持空间 JDBC 写入");
    }
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
