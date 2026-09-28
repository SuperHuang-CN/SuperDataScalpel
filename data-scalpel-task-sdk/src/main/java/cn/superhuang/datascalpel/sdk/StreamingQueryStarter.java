package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.streaming.StreamingQuery;

/**
 * 创建流式查询的回调，通常通过 Lambda 实现。
 * @apiGroup 实时处理
 * @apiMode STREAMING
 */
@FunctionalInterface
public interface StreamingQueryStarter {
    /**
     * 将平台提供的查询名称和 Checkpoint 配置到 Writer 后启动。
     * @param specification 平台分配的查询名称和 Checkpoint。
     */
    StreamingQuery start(StreamingQuerySpec specification) throws Exception;
}
