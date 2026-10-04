package cn.superhuang.datascalpel.sdk;

/**
 * 显式启用中间表装载与单目标事务提交，不支持的数据库明确拒绝。
 * @apiGroup 批处理原子写入
 * @apiMode BATCH
 * @param overwriteCondition OVERWRITE 删除范围；null 表示全表，其他写入模式必须为 null。
 * @param allowEmptyOverwrite 是否允许空输入清空覆盖范围；默认 false，其他模式必须为 false。
 * @apiNote 需要中间表 CREATE/DROP 权限。每次 execute 单独提交，不提供多目标事务。未调用 batchWrite 保持旧直接写入。
 * @apiExample context.models().write("output", rows).mode(ModelWriteMode.APPEND).mapSameName().batchWrite(BatchWriteOptions.atomic()).execute();
 */
public record BatchWriteOptions(WriteCondition overwriteCondition, boolean allowEmptyOverwrite) {
    /** 创建原子提交选项，覆盖时空输入失败。 */
    public static BatchWriteOptions atomic() { return new BatchWriteOptions(null, false); }
    /**
     * 创建按条件覆盖选项；输入必须全部属于该范围。
     * @param condition 非空目标字段条件。
     */
    public static BatchWriteOptions overwriteWhere(WriteCondition condition) {
        return new BatchWriteOptions(java.util.Objects.requireNonNull(condition), false);
    }
}
