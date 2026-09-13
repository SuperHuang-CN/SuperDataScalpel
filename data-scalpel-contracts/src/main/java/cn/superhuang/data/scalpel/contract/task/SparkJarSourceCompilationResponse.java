package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import java.util.List;
import java.util.UUID;

/** Spark-free compiler response. jarBytes is populated only for a successful compilation. */
@JsonClassDescription("在线 Spark Java 源码编译结果；成功时返回 JAR 字节和摘要，失败时通过 diagnostics 返回安全编译问题且 jarBytes 为空。该结果不表示作业已提交或执行。")
public record SparkJarSourceCompilationResponse(
        @JsonPropertyDescription("请求 UUID。")
        UUID requestId,
        @JsonPropertyDescription("源码是否通过预处理与 Java 编译并成功生成 JAR；true 才会返回 jarSha256 和 jarBytes。")
        boolean successful,
        @JsonPropertyDescription("耗时，单位毫秒。")
        long durationMs,
        @JsonPropertyDescription("用户作业源代码的 SHA-256。")
        String sourceSha256,
        @JsonPropertyDescription("成功生成 JAR 内容的小写十六进制 SHA-256；失败时为空。")
        String jarSha256,
        @JsonPropertyDescription("成功生成的 JAR 原始字节，在 JSON 中按 Base64 表示；失败时为空。")
        byte[] jarBytes,
        @JsonPropertyDescription("编译诊断列表。")
        List<SparkJarSourceDiagnostic> diagnostics
) {
    public SparkJarSourceCompilationResponse {
        jarBytes = jarBytes == null ? null : jarBytes.clone();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    @Override
    public byte[] jarBytes() {
        return jarBytes == null ? null : jarBytes.clone();
    }
}
