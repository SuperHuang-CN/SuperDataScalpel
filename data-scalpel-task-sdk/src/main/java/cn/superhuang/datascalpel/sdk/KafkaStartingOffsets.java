package cn.superhuang.datascalpel.sdk;

/**
 * 没有可恢复 Checkpoint 时，Kafka 首次读取的起点。
 * @apiGroup 实时处理
 * @apiMode STREAMING
 * @apiNote 恢复运行时以 Checkpoint 记录的进度为准。
 */
public enum KafkaStartingOffsets {
    /**
     * 从当前仍可用的最早消息开始。
     */
    EARLIEST,
    /**
     * 从启动时的最新位置开始，读取后续新消息。
     */
    LATEST
}
