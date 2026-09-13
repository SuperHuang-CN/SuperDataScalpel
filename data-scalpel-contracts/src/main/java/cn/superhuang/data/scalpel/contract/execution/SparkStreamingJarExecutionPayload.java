package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record SparkStreamingJarExecutionPayload(
        @JsonPropertyDescription("用户 Job API 契约版本；当前支持版本为 1。")
        int jobApiVersion,
        @JsonPropertyDescription("实现 DataScalpel Job API 的用户作业完整类名。")
        String jobClass,
        @JsonPropertyDescription("传给用户流式 JAR 的普通名称/值参数；名称必须唯一且不得携带平台凭据。")
        List<SparkJarExecutionPayload.Parameter> parameters,
        @JsonPropertyDescription("经平台白名单校验后传给 Spark 的名称/值配置快照。")
        List<SparkConfigurationEntry> sparkConf,
        @JsonPropertyDescription("用户 JAR 绑定名到平台资源及允许访问模式的快照。")
        List<SparkJarExecutionPayload.ResourceBinding> resourceBindings,
        @JsonPropertyDescription("实时作业必须进入已启动状态的最长等待秒数。")
        int startupTimeoutSeconds,
        @JsonPropertyDescription("执行目的：REAL 正式执行或 TRIAL 试运行。")
        SparkJarExecutionPayload.ExecutionPurpose executionPurpose,
        @JsonPropertyDescription("实时任务部署 UUID，用于关联启动、进度、停止和 Checkpoint。")
        UUID deploymentId,
        @JsonPropertyDescription("该实时部署在对象存储中的受控 Checkpoint Key 前缀。")
        String checkpointKeyPrefix,
        @JsonPropertyDescription("Checkpoint 使用方式：新建、复用或从另一部署恢复。")
        StreamingCheckpointMode checkpointMode,
        @JsonPropertyDescription("恢复 Checkpoint 的来源实时部署 UUID；不从其他部署恢复时为空。")
        UUID checkpointSourceDeploymentId
) {
    public SparkStreamingJarExecutionPayload {
        jobClass = ExecutionContractValidation.required(jobClass, 500, "Streaming Job Class");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        sparkConf = sparkConf == null ? List.of() : List.copyOf(sparkConf);
        resourceBindings = resourceBindings == null ? List.of() : List.copyOf(resourceBindings);
        executionPurpose = executionPurpose == null
                ? SparkJarExecutionPayload.ExecutionPurpose.REAL : executionPurpose;
        if (jobApiVersion != 1 || startupTimeoutSeconds < 1 || startupTimeoutSeconds > 86400
                || parameters.size() > 100 || sparkConf.size() > 100 || resourceBindings.size() > 200
                || deploymentId == null || checkpointMode == null
                || checkpointKeyPrefix == null || checkpointKeyPrefix.isBlank()
                || checkpointMode == StreamingCheckpointMode.FRESH && checkpointSourceDeploymentId != null
                || checkpointMode == StreamingCheckpointMode.CONTINUE && checkpointSourceDeploymentId == null
                || duplicate(parameters.stream().map(SparkJarExecutionPayload.Parameter::name).toList())
                || duplicate(sparkConf.stream().map(SparkConfigurationEntry::name).toList())
                || duplicate(resourceBindings.stream().map(SparkJarExecutionPayload.ResourceBinding::bindingName).toList())) {
            throw new IllegalArgumentException("Streaming JAR 执行载荷无效");
        }
    }

    private static boolean duplicate(List<String> values) {
        return new HashSet<>(values).size() != values.size();
    }
}
