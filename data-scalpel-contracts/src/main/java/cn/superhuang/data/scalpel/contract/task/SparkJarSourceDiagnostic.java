package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("在线 Java 编译器返回的一条安全诊断及其源码范围；行列无法定位时使用 0 或负数，不包含异常堆栈。")
public record SparkJarSourceDiagnostic(
        @JsonPropertyDescription("诊断严重级别，区分阻断编译的错误和非阻断告警。")
        SparkJarSourceDiagnosticSeverity severity,
        @JsonPropertyDescription("便于客户端分类处理的稳定诊断编码。")
        String code,
        @JsonPropertyDescription("编译器返回的安全诊断说明。")
        String message,
        @JsonPropertyDescription("问题起始行号，从 1 开始；无法定位时为 0 或负数。")
        long line,
        @JsonPropertyDescription("问题起始列号，从 1 开始；无法定位时为 0 或负数。")
        long column,
        @JsonPropertyDescription("问题结束行号；无法定位时为 0 或负数。")
        long endLine,
        @JsonPropertyDescription("问题结束列号；无法定位时为 0 或负数。")
        long endColumn
) {
}
