package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "保存在线源码、编译临时 JAR 并尝试创建隔离试运行的组合结果。临时 JAR 只附着到本次运行，不替换任务当前生产 JAR，也不发布任务。")

public record SparkJarTrialRunResponse(
        @Schema(description = "COMPILE_FAILED 表示源码草稿已保存但编译失败，HTTP 为 200且未创建运行；QUEUED 表示已创建试运行并返回 202。其他准备或提交错误通过 ProblemDetail 返回。")
        Status status,
        @Schema(description = "在线源码编译耗时，单位毫秒。")
        long compilationDurationMs,
        @Schema(description = "操作结束后重新读取的在线源码与生产 JAR 哈希状态；试运行临时 JAR 不写入 currentJar，因此成功排队后也不会把 hasUncompiledChanges 改为 false。")
        SparkJarOnlineSourceResponse source,
        @Schema(description = "编译器诊断；成功时也可能包含警告。")
        List<SparkJarOnlineCompilationResponse.Diagnostic> diagnostics,
        @Schema(description = "已排队的隔离试运行；COMPILE_FAILED 时为空。批 JAR 使用定义 timeoutSeconds，流式 JAR 试运行固定最多 30 分钟并使用 FRESH Checkpoint。")
        TaskRunResponse run
) {
    public SparkJarTrialRunResponse {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    @Schema(description = "在线源码试运行提交结果：COMPILE_FAILED 已保存源码但未创建运行；QUEUED 已创建隔离试运行。")
    public enum Status {
        COMPILE_FAILED,
        QUEUED
    }
}
