package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.streaming.StreamingQuery;

/**
 * 注册流式查询，由平台统一管理名称、Checkpoint、停止和监控。
 * @apiGroup 实时处理
 * @apiMode STREAMING
 * @apiNote 同一任务内逻辑名称必须唯一；starter 必须返回已启动的查询。
 */
public interface StreamingQueries {
    /**
     * 使用平台提供的名称和 Checkpoint 启动并注册查询。
     * @param logicalName 当前任务内唯一的逻辑查询名称。
     * @param sinkType 输出目标种类。
     * @param starter 创建并启动 StreamingQuery 的回调。
     */
    StreamingQuery start(
            String logicalName,
            StreamingSinkType sinkType,
            StreamingQueryStarter starter
    ) throws Exception;
}
