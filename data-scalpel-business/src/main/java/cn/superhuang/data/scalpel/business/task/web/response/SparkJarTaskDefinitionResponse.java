package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceAccessMode;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import cn.superhuang.data.scalpel.contract.execution.SparkJarJobMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SparkJarTaskDefinitionResponse(
        UUID taskId,
        boolean configured,
        int definitionVersion,
        SparkJarJobMode jobMode,
        Jar jar,
        List<Entry> parameters,
        List<Entry> sparkConf,
        List<ResourceBinding> resourceBindings,
        int timeoutSeconds,
        Instant updatedAt
) {
    public record Jar(String fileName, String sha256, long sizeBytes, String jobClass,
                      int jobApiVersion, SparkJarJobMode jobMode) {}
    public record Entry(String name, String value) {}
    public record ResourceBinding(String bindingName, SparkJarResourceType resourceType, UUID resourceId,
                                  String resourceName, String topicName,
                                  SparkJarResourceAccessMode accessMode) {}
}
