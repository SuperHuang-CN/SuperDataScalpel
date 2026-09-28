package cn.superhuang.datascalpel.sdk;

/**
 * 任务运行的触发来源。
 * @apiGroup 任务信息
 */
public enum TaskTriggerType {
    /**
     * 用户手动启动。
     */
    MANUAL,
    /**
     * 由调度计划启动。
     */
    SCHEDULED,
    /**
     * 启动长期实时部署。
     */
    STREAMING_START
}
