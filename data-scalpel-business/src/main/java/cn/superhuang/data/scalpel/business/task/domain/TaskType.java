package cn.superhuang.data.scalpel.business.task.domain;

/** Stable task definition family selected when a task is created. */
public enum TaskType {
    LOCAL_SQL,
    SPARK_CANVAS,
    SPARK_STREAMING_CANVAS;

    public boolean isCanvas() {
        return this == SPARK_CANVAS || this == SPARK_STREAMING_CANVAS;
    }

    public boolean isStreaming() {
        return this == SPARK_STREAMING_CANVAS;
    }
}
