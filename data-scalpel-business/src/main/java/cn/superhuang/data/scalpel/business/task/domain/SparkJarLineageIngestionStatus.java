package cn.superhuang.data.scalpel.business.task.domain;

public enum SparkJarLineageIngestionStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    STALE
}
