package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("Geometry 有效性校验配置；保留来源表全部行与字段，并追加 Sedona/JTS 有效性布尔值及可选无效原因。它只诊断，不过滤、修复或替换来源 Geometry。")
public record GeometryValidateConfiguration(
        @JsonPropertyDescription("包含待校验 Geometry 的上游 Canvas 逻辑表名；必须引用进入节点前已经存在的表。")
        String sourceTableName,
        @JsonPropertyDescription("追加诊断字段后的新逻辑表名；必须与进入节点时已有的所有表名不同，原来源表仍保留。")
        String outputTableName,
        @JsonPropertyDescription("要调用 ST_IsValid 的 Geometry 字段名；必须具有受支持的完整空间类型定义。NULL 输入的合法性结果为 NULL。")
        String geometryColumnName,
        @JsonPropertyDescription("追加的 BOOLEAN 结果字段名，不能与任何来源字段或 reasonColumnName 同名。true 表示有效，false 表示无效，来源 Geometry 为 NULL 时为 NULL。")
        String validColumnName,
        @JsonPropertyDescription("可选追加的 STRING 原因字段名；传 null 表示不生成。仅在合法性结果为 false 时写 ST_IsValidReason，几何有效或为 NULL 时结果为 NULL；空字符串无效。")
        String reasonColumnName
) {
}
