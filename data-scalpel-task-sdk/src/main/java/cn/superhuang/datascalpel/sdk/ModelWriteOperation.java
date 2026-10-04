package cn.superhuang.datascalpel.sdk;

/**
 * 设置目标模型的写入方式与字段映射，最后执行。
 * @apiGroup 读写模型
 * @apiNote 生产写入立即生效，多个目标之间不提供跨目标事务；UPSERT 依赖目标模型的完整主键。
 */
public interface ModelWriteOperation {
    /**
     * 启用单目标原子批写；实时任务不支持，不会自动降级。
     * @param options 原子提交及覆盖范围，不能为空。
     */
    default ModelWriteOperation batchWrite(BatchWriteOptions options) {
        throw new UnsupportedOperationException("当前运行时不支持原子批写");
    }
    /**
     * 选择追加、覆盖或按主键更新插入。
     * @param mode 目标写入方式。
     */
    ModelWriteOperation mode(ModelWriteMode mode);

    /**
     * 把一个来源字段映射到目标字段：目标在前，来源在后。
     * @param targetColumnName 目标字段名称。
     * @param sourceColumnName 来源 Dataset 字段名称。
     */
    ModelWriteOperation map(String targetColumnName, String sourceColumnName);

    /**
     * 将来源字段映射到同名目标字段。
     */
    ModelWriteOperation mapSameName();

    /**
     * 仅在支持本地 Schema 检查的环境中验证结构；生产转换和约束由 Spark 与目标系统决定。
     */
    ModelWriteOperation checkSchema();

    /**
     * 执行当前模型写入并返回影响行数信息。
     */
    WriteResult execute();
}
