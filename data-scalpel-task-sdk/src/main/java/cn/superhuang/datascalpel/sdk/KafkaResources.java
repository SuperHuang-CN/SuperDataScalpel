package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.streaming.DataStreamWriter;

/**
 * 按 Kafka 资源绑定读取消息或配置消息写入。
 * @apiGroup 实时处理
 * @apiMode STREAMING
 * @apiExample var input = context.kafka().readStream("source_topic", KafkaStartingOffsets.LATEST);
 *   context.queries().start("forward", StreamingSinkType.KAFKA, spec ->
 *       context.kafka().writeStream("target_topic", input)
 *           .queryName(spec.queryName())
 *           .option("checkpointLocation", spec.checkpointLocation()).start());
 * @apiNote 读取返回标准 Kafka 行，key/value 的解析由代码完成；输出需准备 Kafka 所需列，查询须交给平台注册。
 */
public interface KafkaResources {
    /**
     * 创建 Kafka 流式输入，选择首次读取的起始位置。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param startingOffsets 首次读取起点；恢复运行以 Checkpoint 为准。
     */
    Dataset<Row> readStream(String bindingName, KafkaStartingOffsets startingOffsets);

    /**
     * 创建 Kafka 流式输出配置，尚未启动查询。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param source 待写入的 Dataset，字段需要满足目标映射。
     */
    DataStreamWriter<Row> writeStream(String bindingName, Dataset<Row> source);
}
