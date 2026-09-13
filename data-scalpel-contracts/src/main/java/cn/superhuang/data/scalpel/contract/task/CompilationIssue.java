package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("任务或节点编译期间发现的结构化问题；ERROR 阻止生成可执行计划，WARNING 表示允许继续但存在降级。")
public record CompilationIssue(
        @JsonPropertyDescription("稳定编译问题码，用于客户端分类和定位修复建议。")
        String code,
        @JsonPropertyDescription("问题严重级别：ERROR 阻止编译，WARNING 允许继续但结果可能降级。")
        CompilationSeverity severity,
        @JsonPropertyDescription("说明任务定义为何无法编译或为何可能降级的可读信息。")
        String message,
        @JsonPropertyDescription("问题关联的 Canvas 节点 ID；画布级问题为空。")
        String nodeId,
        @JsonPropertyDescription("问题在任务定义中的受控字段路径；无法定位到具体字段时为空。")
        String path
) {
    public static CompilationIssue canvas(String code, String message, String path) {
        return new CompilationIssue(code, CompilationSeverity.ERROR, message, null, path);
    }

    public static CompilationIssue node(String code, String message, String nodeId, String path) {
        return new CompilationIssue(code, CompilationSeverity.ERROR, message, nodeId, path);
    }

    public static CompilationIssue warning(String code, String message, String nodeId, String path) {
        return new CompilationIssue(code, CompilationSeverity.WARNING, message, nodeId, path);
    }
}
