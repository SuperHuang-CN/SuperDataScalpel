package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("返回一个非 NULL、非 Geometry 的平台类型常量；具体文本格式由 CanvasLiteral 规定，类型兼容性最终由 Spark Analyzer 判断。")
public record LiteralExpression(
        @JsonPropertyDescription("必填且可解析的 CanvasLiteral；dataType 和 value 都不能为空，GEOMETRY 不支持。")
        CanvasLiteral literal
) implements CanvasExpression {
}
