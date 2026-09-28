package cn.superhuang.datascalpel.sdk;

/**
 * 记录一段操作的耗时，结束时恢复先前 Spark 作业说明。
 * @apiGroup 日志与指标
 * @apiExample try (var operation = context.observability().operation("transform")) {
 *       // 执行需要计时的操作
 *   }
 * @apiNote 必须在创建它的线程关闭；重复关闭不会重复计时。
 */
public interface JobOperation extends AutoCloseable {
    /**
     * 结束计时并恢复先前的 Spark 作业说明。
     */
    @Override
    void close();
}
