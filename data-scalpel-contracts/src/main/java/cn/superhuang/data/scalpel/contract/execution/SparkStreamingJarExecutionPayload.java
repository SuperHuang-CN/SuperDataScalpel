package cn.superhuang.data.scalpel.contract.execution;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record SparkStreamingJarExecutionPayload(
        int jobApiVersion,
        String jobClass,
        List<SparkJarExecutionPayload.Parameter> parameters,
        List<SparkConfigurationEntry> sparkConf,
        List<SparkJarExecutionPayload.ResourceBinding> resourceBindings,
        int startupTimeoutSeconds,
        UUID deploymentId,
        String checkpointKeyPrefix,
        StreamingCheckpointMode checkpointMode,
        UUID checkpointSourceDeploymentId
) {
    public SparkStreamingJarExecutionPayload {
        jobClass = ExecutionContractValidation.required(jobClass, 500, "Streaming Job Class");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        sparkConf = sparkConf == null ? List.of() : List.copyOf(sparkConf);
        resourceBindings = resourceBindings == null ? List.of() : List.copyOf(resourceBindings);
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
