package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record SparkJarExecutionPayload(
        int jobApiVersion,
        String jobClass,
        List<Parameter> parameters,
        List<SparkConfigurationEntry> sparkConf,
        List<ResourceBinding> resourceBindings,
        TriggerType triggerType,
        UUID scheduleId,
        Instant scheduledFireAt
) {
    public SparkJarExecutionPayload {
        jobClass = require(jobClass, 500, "Job Class");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        sparkConf = sparkConf == null ? List.of() : List.copyOf(sparkConf);
        resourceBindings = resourceBindings == null ? List.of() : List.copyOf(resourceBindings);
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

    public record Parameter(String name, String value) {
        public Parameter {
            name = require(name, 100, "参数名");
            if (value == null || value.length() > 4000) {
                throw new IllegalArgumentException("参数值无效");
            }
        }
    }

    public record ResourceBinding(
            String bindingName,
            SparkJarResourceType resourceType,
            UUID resourceId,
            String topicName,
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
