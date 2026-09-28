package cn.superhuang.datascalpel.sdk;

/**
 * 实时作业入口，在 start 中创建并注册流式查询。
 * @apiGroup 实时处理
 * @apiMode STREAMING
 * @apiNote 每个 StreamingQuery 都必须通过 context.queries().start 注册；start 内不要 awaitTermination。
 */
public interface SparkStreamingJob {
    /**
     * 启动并注册持续运行的流式查询，完成注册后返回。
     * @param context 平台传入的任务上下文。
     */
    void start(SparkStreamingJobContext context) throws Exception;

    /**
     * 平台停止作业时调用，可覆盖此方法释放自有资源。
     * @param context 平台传入的任务上下文。
     */
    default void onStop(SparkStreamingJobContext context) throws Exception {
    }
}
