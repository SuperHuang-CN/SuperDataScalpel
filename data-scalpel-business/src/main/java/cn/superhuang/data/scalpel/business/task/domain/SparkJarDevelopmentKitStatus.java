package cn.superhuang.data.scalpel.business.task.domain;

public enum SparkJarDevelopmentKitStatus {
    QUEUED, RUNNING, SUCCEEDED, FAILED, EXPIRED;

    public boolean unfinished() { return this == QUEUED || this == RUNNING; }
}
