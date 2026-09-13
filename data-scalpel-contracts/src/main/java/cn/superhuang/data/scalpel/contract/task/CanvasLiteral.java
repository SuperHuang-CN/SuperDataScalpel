package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

@JsonClassDescription("Canvas 结构化筛选、空值填充和值映射使用的平台类型常量。value 以字符串承载：BOOLEAN 仅 true/false，整数与有限浮点数使用十进制文本，BINARY 使用 Base64，DATE 使用 ISO yyyy-MM-dd，TIMESTAMP 使用带偏移量的 ISO-8601，TIMESTAMP_NTZ 使用无时区 ISO 本地日期时间；GEOMETRY 不支持。具体字段可进一步禁止 NULL。")
public record CanvasLiteral(
        @JsonPropertyDescription("常量的平台数据类型；通常必须与所比较、填充或映射字段的类型一致。GEOMETRY 不能作为 CanvasLiteral。")
        PlatformDataType dataType,
        @JsonPropertyDescription("按 dataType 解析的字符串值：BOOLEAN 为小写 true/false；BYTE/SHORT/INTEGER/LONG、有限 FLOAT/DOUBLE 和 DECIMAL 为合法数字；BINARY 为 Base64；DATE 为 yyyy-MM-dd；TIMESTAMP 为带偏移量 ISO-8601；TIMESTAMP_NTZ 为无时区 ISO 本地日期时间。多数规则要求非 NULL，SQL NULL 通常由整个 Literal 对象为空表达。")
        String value
) {
}
