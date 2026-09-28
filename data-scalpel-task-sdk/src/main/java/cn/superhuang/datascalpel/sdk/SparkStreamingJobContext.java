package cn.superhuang.datascalpel.sdk;

/**
 * 实时任务上下文，同时提供通用任务上下文的全部能力。
 * @apiGroup 实时处理
 * @apiMode STREAMING
 * @apiNote context 是 start(SparkStreamingJobContext context) 的方法入参；以下调用写在 start 方法内。
 */
public interface SparkStreamingJobContext extends SparkJobContext {
    /**
     * 按任务绑定读取或写入 Kafka 消息。
     * @apiExample var kafka = context.kafka();
     *   var messages = kafka.readStream("source_topic", KafkaStartingOffsets.EARLIEST);
     */
    KafkaResources kafka();

    /**
     * 向平台注册流式查询，统一管理停止与 Checkpoint。
     * @apiExample var queries = context.queries();
     * @apiNote 取得注册器后，查看 start 方法完成查询注册；仅取得 queries 对象不会启动查询。
     */
    StreamingQueries queries();
}
