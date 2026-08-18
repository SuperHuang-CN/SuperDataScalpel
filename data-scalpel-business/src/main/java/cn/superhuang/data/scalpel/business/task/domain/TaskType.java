package cn.superhuang.data.scalpel.business.task.domain;

/** Stable task definition family selected when a task is created. */
public enum TaskType {
    LOCAL_SQL,
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
        return this != LOCAL_SQL;
    }
}
