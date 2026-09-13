package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import cn.superhuang.data.scalpel.contract.execution.SparkJarJobMode;

import java.util.UUID;

/** Stable Admin-to-Task-Engine request for compiling one online Spark job source file. */
@JsonClassDescription("在线编译一份用户 Spark 作业 Java 源码的内部稳定请求；只负责编译和安全诊断，不提交或运行作业。")
public record SparkJarSourceCompilationRequest(
        @JsonPropertyDescription("调用方生成的编译请求 UUID，用于关联异步结果与诊断。")
        UUID requestId,
        @JsonPropertyDescription("单个在线 Spark 作业 Java 源文件的完整源码；编译器按受控 SDK 类路径处理，不代表允许访问控制面内部类。")
        String sourceCode,
        @JsonPropertyDescription("要编译的作业模式：BATCH 批处理或 STREAMING 流式；为空时按 BATCH 处理。")
        SparkJarJobMode jobMode
) {
    public SparkJarSourceCompilationRequest {
        jobMode = jobMode == null ? SparkJarJobMode.BATCH : jobMode;
    }

    public SparkJarSourceCompilationRequest(UUID requestId, String sourceCode) {
        this(requestId, sourceCode, SparkJarJobMode.BATCH);
    }
}
