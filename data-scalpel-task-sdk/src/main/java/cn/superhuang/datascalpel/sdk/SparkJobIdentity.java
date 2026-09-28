package cn.superhuang.datascalpel.sdk;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 查询当前任务、执行次数和触发来源，方便定位日志。
 * @apiGroup 任务信息
 */
public interface SparkJobIdentity {
    /**
     * 当前任务的唯一标识。
     */
    UUID taskId();

    /**
     * 本次运行标识。
     */
    UUID runId();

    /**
     * 本次执行实例标识。
     */
    UUID executionId();

    /**
     * 本次执行的尝试序号。
     */
    int attempt();

    /**
     * 本次执行使用的任务定义版本。
     */
    int definitionVersion();

    /**
     * 本次运行的触发来源。
     */
    TaskTriggerType triggerType();

    /**
     * 关联调度标识；非调度触发时可能为空。
     */
    Optional<UUID> scheduleId();

    /**
     * 计划触发时间；非调度触发时可能为空。
     */
    Optional<Instant> scheduledFireAt();

    /**
     * 实时部署标识；非长期实时部署时为空。
     */
    default Optional<UUID> deploymentId() {
        return Optional.empty();
    }
}
