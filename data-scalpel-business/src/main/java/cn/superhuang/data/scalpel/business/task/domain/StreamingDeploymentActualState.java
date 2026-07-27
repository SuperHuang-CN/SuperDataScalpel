package cn.superhuang.data.scalpel.business.task.domain;

public enum StreamingDeploymentActualState {
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED;

    public boolean active() {
        return this == STARTING || this == RUNNING || this == STOPPING;
    }
}
