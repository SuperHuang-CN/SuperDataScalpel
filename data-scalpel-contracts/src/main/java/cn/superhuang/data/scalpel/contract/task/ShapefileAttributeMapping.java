package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("Shapefile 输出中的一个来源字段到 DBF 属性字段映射；数组顺序决定 DBF 字段顺序。支持 BOOLEAN、整数、DECIMAL、STRING 和 DATE，不支持 FLOAT、DOUBLE、时间戳、BINARY 或 Geometry。")
public record ShapefileAttributeMapping(
        @JsonPropertyDescription("来源表中存在的非 Geometry 字段名；同一来源字段在映射列表中只能出现一次。")
        String sourceColumnName,
        @JsonPropertyDescription("DBF 目标字段名，匹配 [A-Za-z_][A-Za-z0-9_]{0,9}，最多 10 个 ASCII 字符，并按大小写不敏感规则唯一；运行时不自动截断或改名。")
        String targetFieldName,
        @JsonPropertyDescription("STRING 来源必填的 DBF Character UTF-8 字节宽度，范围 1..254；其他来源类型必须为 null。实际字符串超过该字节宽度时运行失败，不自动截断。")
        Integer targetStringByteLength
) {
}
