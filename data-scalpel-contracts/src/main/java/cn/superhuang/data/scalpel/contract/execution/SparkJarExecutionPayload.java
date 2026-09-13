package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record SparkJarExecutionPayload(
        @JsonPropertyDescription("用户 Job API 契约版本；当前支持版本为 1。")
        int jobApiVersion,
        @JsonPropertyDescription("实现 DataScalpel Job API 的用户作业完整类名。")
        String jobClass,
        @JsonPropertyDescription("传给用户 JAR 的普通名称/值参数；不得携带平台凭据。")
        List<Parameter> parameters,
        @JsonPropertyDescription("经平台白名单校验后传给 Spark 的名称/值配置快照。")
        List<SparkConfigurationEntry> sparkConf,
        @JsonPropertyDescription("用户 JAR 绑定名到平台资源及允许访问模式的快照。")
        List<ResourceBinding> resourceBindings,
        @JsonPropertyDescription("触发来源：MANUAL 手工运行或 SCHEDULED 调度运行。")
        TriggerType triggerType,
        @JsonPropertyDescription("调度定义 UUID；手工运行为空，调度运行必填。")
        UUID scheduleId,
        @JsonPropertyDescription("计划触发时间；手工运行为空，调度运行必填。")
        Instant scheduledFireAt,
        @JsonPropertyDescription("执行目的：REAL 正式执行或 TRIAL 试运行。")
        ExecutionPurpose executionPurpose
) {
    public SparkJarExecutionPayload {
        jobClass = require(jobClass, 500, "Job Class");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        sparkConf = sparkConf == null ? List.of() : List.copyOf(sparkConf);
        resourceBindings = resourceBindings == null ? List.of() : List.copyOf(resourceBindings);
        executionPurpose = executionPurpose == null ? ExecutionPurpose.REAL : executionPurpose;
        if (jobApiVersion != 1 || triggerType == null || parameters.size() > 100
                || sparkConf.size() > 100 || resourceBindings.size() > 200
                || hasDuplicate(parameters.stream().map(Parameter::name).toList())
                || hasDuplicate(sparkConf.stream().map(SparkConfigurationEntry::name).toList())
                || hasDuplicate(resourceBindings.stream().map(ResourceBinding::bindingName).toList())
                || triggerType == TriggerType.MANUAL && (scheduleId != null || scheduledFireAt != null)
                || triggerType == TriggerType.SCHEDULED && (scheduleId == null || scheduledFireAt == null)) {
            throw new IllegalArgumentException("Spark JAR 执行载荷无效");
        }
    }

    public SparkJarExecutionPayload(
            int jobApiVersion, String jobClass, List<Parameter> parameters,
            List<SparkConfigurationEntry> sparkConf, List<ResourceBinding> resourceBindings,
            TriggerType triggerType, UUID scheduleId, Instant scheduledFireAt
    ) {
        this(jobApiVersion, jobClass, parameters, sparkConf, resourceBindings, triggerType,
                scheduleId, scheduledFireAt, ExecutionPurpose.REAL);
    }

    private static boolean hasDuplicate(List<String> values) {
        return new HashSet<>(values).size() != values.size();
    }

    private static String require(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(label + "无效");
        }
        return value.trim();
    }

    public enum TriggerType {
        MANUAL,
        SCHEDULED
    }

    public enum ExecutionPurpose {
        REAL,
        TRIAL
    }

    public record Parameter(
            @JsonPropertyDescription("用户作业参数名，在单次执行载荷内唯一。")
            String name,
            @JsonPropertyDescription("用户作业参数值，原样作为字符串提供给 SDK 作业上下文。")
            String value
    ) {
        public Parameter {
            name = require(name, 100, "参数名");
            if (value == null || value.length() > 4000) {
                throw new IllegalArgumentException("参数值无效");
            }
        }
    }

    public record ResourceBinding(
            @JsonPropertyDescription("用户 JAR 使用的资源绑定名。")
            String bindingName,
            @JsonPropertyDescription("绑定的平台资源类型，决定 resourceId、topicName 和访问模式规则。")
            SparkJarResourceType resourceType,
            @JsonPropertyDescription("平台资源 UUID。")
            UUID resourceId,
            @JsonPropertyDescription("Kafka 或 TDengine 主题名。")
            String topicName,
            @JsonPropertyDescription("允许的资源访问模式。")
            SparkJarResourceAccessMode accessMode
    ) {
        public ResourceBinding {
            bindingName = require(bindingName, 100, "资源绑定名");
            topicName = resourceType == SparkJarResourceType.KAFKA_TOPIC
                    ? ExecutionContractValidation.topic(topicName) : null;
            if (resourceType == null || resourceId == null || accessMode == null
                    || resourceType == SparkJarResourceType.KAFKA_TOPIC && topicName == null) {
                throw new IllegalArgumentException("资源绑定无效");
            }
        }

        public ResourceBinding(String bindingName, SparkJarResourceType resourceType,
                               UUID resourceId, SparkJarResourceAccessMode accessMode) {
            this(bindingName, resourceType, resourceId, null, accessMode);
        }
    }
}
