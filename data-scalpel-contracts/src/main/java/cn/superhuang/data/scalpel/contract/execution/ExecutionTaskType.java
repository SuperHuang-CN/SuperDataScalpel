package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("执行协议中的计算任务类型：SPARK_CANVAS 批 Canvas；SPARK_STREAMING_CANVAS 实时 Canvas；SPARK_MODEL_QUALITY 模型质检；SPARK_JAR 批 JAR；SPARK_STREAMING_JAR 实时 JAR。")
public enum ExecutionTaskType {
    SPARK_CANVAS,
    SPARK_STREAMING_CANVAS,
    SPARK_MODEL_QUALITY,
    SPARK_JAR,
    SPARK_STREAMING_JAR
}
