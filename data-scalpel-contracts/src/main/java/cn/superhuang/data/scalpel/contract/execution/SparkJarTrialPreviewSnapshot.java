package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import java.time.Instant;
import java.util.UUID;

/** Immutable, attempt-scoped snapshot used while a Spark JAR trial run is still executing. */
@JsonClassDescription("Spark JAR 试运行仍在执行时写入对象存储的尝试级预览快照；由平台内部传输，管理 API 会转换为 SparkJarTrialPreviewResponse。")
public record SparkJarTrialPreviewSnapshot(
        @JsonPropertyDescription("试运行预览快照协议版本；当前固定为 1，不是数据模型 Schema 版本。")
        int schemaVersion,
        @JsonPropertyDescription("外部执行 UUID。")
        UUID executionId,
        @JsonPropertyDescription("任务运行 UUID。")
        UUID runId,
        @JsonPropertyDescription("本次任务运行的执行尝试序号，从 1 开始。")
        int attempt,
        @JsonPropertyDescription("当前执行尝试内的预览快照序号，从 1 开始；用户 Job 每次提交新预览时递增，用于判断读取结果的新旧。")
        long revision,
        @JsonPropertyDescription("该预览由 Runner 接收并形成快照的时间，ISO-8601 UTC 时间戳。")
        Instant capturedAt,
        @JsonPropertyDescription("用户 Job 本次提交的试运行预览，快照存在时始终有值；尚未生成预览时不会创建该快照。")
        SparkJarTrialPreview preview
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public SparkJarTrialPreviewSnapshot {
        if (schemaVersion != CURRENT_SCHEMA_VERSION || executionId == null || runId == null
                || attempt < 1 || revision < 1 || capturedAt == null || preview == null) {
            throw new IllegalArgumentException("Spark JAR 试运行预览快照无效");
        }
    }
}
