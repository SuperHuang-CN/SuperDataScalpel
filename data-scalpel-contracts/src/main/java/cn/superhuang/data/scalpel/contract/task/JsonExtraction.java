package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

@JsonClassDescription("从来源 JSON 中按 Spark VARIANT Path 读取一个值并转换为新的 nullable 平台标量字段；不支持复杂结构或 Geometry，真实 Path 命中率和数据可转换性不会在编译时扫描。")
public record JsonExtraction(
        @JsonPropertyDescription("必填的 Spark VARIANT Path，必须以 $ 开头且最长 512 个字符；完整语法由 Spark Analyzer 校验。该值可能暴露载荷结构，不进入日志或安全摘要。")
        String jsonPath,
        @JsonPropertyDescription("必填的新输出字段名；不得与任何来源字段或同一操作的其他提取项重名。字段按 extractions 数组顺序追加。")
        String outputColumnName,
        @JsonPropertyDescription("必填的目标平台标量类型；STRING length 与 DECIMAL precision/scale 遵循 PlatformTypeDefinition，但 STRING length 不会在提取时截断真实值。GEOMETRY 不支持，输出固定 nullable。")
        PlatformTypeDefinition targetType
) {
}
