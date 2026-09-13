package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "在线 Java 源码编译并应用结果。请求源码会先持久化；SUCCEEDED 时生成的 JAR 已原子替换任务当前 JAR，FAILED 时保留旧 JAR 但源码草稿仍已保存。不会发布或运行任务。")

public record SparkJarOnlineCompilationResponse(
        @Schema(description = "SUCCEEDED 表示新 JAR 已应用到任务定义；FAILED 表示编译器未生成 JAR，任务原有 JAR（如有）保持不变。")
        Status status,
        @Schema(description = "编译耗时，单位毫秒。")
        long durationMs,
        @Schema(description = "编译结束后重新读取的在线源码与当前 JAR 状态；并发修改使源码摘要变化时整个应用步骤返回冲突而不会返回本结构。")
        SparkJarOnlineSourceResponse source,
        @Schema(description = "编译器诊断；成功时也可能包含警告。")
        List<Diagnostic> diagnostics
) {
    public SparkJarOnlineCompilationResponse {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    @Schema(description = "在线源码编译应用状态：SUCCEEDED 已生成并替换当前 JAR；FAILED 编译未通过且旧 JAR 保持不变。")
    public enum Status { SUCCEEDED, FAILED }

    @Schema(description = "Java 编译诊断级别：ERROR 错误；WARNING 警告；NOTE 提示。")
    public enum Severity { ERROR, WARNING, NOTE }

    @Schema(description = "编译器诊断及其在源码中的位置。")

    public record Diagnostic(
            @Schema(description = "诊断严重级别。")
            Severity severity,
            @Schema(description = "编译器诊断码；由当前 Java 编译器提供，客户端不应依赖其跨版本不变。")
            String code,
            @Schema(description = "Java 编译器返回的诊断正文，说明错误、警告或提示的具体内容。")
            String message,
            @Schema(description = "诊断起始行号，从 1 开始；编译器无法定位时可能为负数。")
            long line,
            @Schema(description = "诊断起始列号，从 1 开始；编译器无法定位时可能为负数。")
            long column,
            @Schema(description = "诊断结束行号；无法定位时可能为负数。")
            long endLine,
            @Schema(description = "诊断结束列号；无法定位时可能为负数。")
            long endColumn
    ) {
    }
}
