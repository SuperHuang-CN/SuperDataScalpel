package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.execution.SparkJarJobMode;

import java.util.UUID;

/** Stable Admin-to-Task-Engine request for compiling one online Spark job source file. */
public record SparkJarSourceCompilationRequest(
        UUID requestId,
        String sourceCode,
        SparkJarJobMode jobMode
) {
    public SparkJarSourceCompilationRequest {
        jobMode = jobMode == null ? SparkJarJobMode.BATCH : jobMode;
    }

    public SparkJarSourceCompilationRequest(UUID requestId, String sourceCode) {
        this(requestId, sourceCode, SparkJarJobMode.BATCH);
    }
}
