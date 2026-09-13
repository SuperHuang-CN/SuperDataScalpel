package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
/** Stable task definition family selected when a task is created. */
@Schema(description = "任务定义类型：LOCAL_SQL 本地 SQL；WORKFLOW 工作流；SPARK_CANVAS 批 Canvas；SPARK_STREAMING_CANVAS 流式 Canvas；SPARK_MODEL_QUALITY 模型质量；SPARK_JAR 批 JAR；SPARK_STREAMING_JAR 流式 JAR。")
public enum TaskType {
    LOCAL_SQL,
    WORKFLOW,
    SPARK_CANVAS,
    SPARK_STREAMING_CANVAS,
    SPARK_MODEL_QUALITY,
    SPARK_JAR,
    SPARK_STREAMING_JAR;

    public boolean isCanvas() {
        return this == SPARK_CANVAS || this == SPARK_STREAMING_CANVAS;
    }

    public boolean isStreaming() {
        return this == SPARK_STREAMING_CANVAS || this == SPARK_STREAMING_JAR;
    }

    public boolean isJar() {
        return this == SPARK_JAR || this == SPARK_STREAMING_JAR;
    }

    public boolean requiresComputeEngine() {
        return this != LOCAL_SQL && this != WORKFLOW;
    }
}
