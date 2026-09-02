package cn.superhuang.data.scalpel.business.task.web.response;

import java.util.UUID;

public record SparkJarOnlineSourceResponse(
        UUID taskId,
        int definitionVersion,
        String sourceCode,
        String sourceSha256,
        String compiledSourceSha256,
        boolean persisted,
        boolean hasUncompiledChanges,
        JarOrigin currentJarOrigin,
        SparkJarTaskDefinitionResponse.Jar currentJar
) {
    public enum JarOrigin {
        UPLOADED,
        ONLINE_COMPILED
    }
}
