package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("Geometry 修复配置；保留来源表全部行与字段，并用 Sedona ST_MakeValid(keepCollapsed=false) 追加修复结果。结果 CRS 和维度继承来源，但 GeometryKind 固定声明为通用 GEOMETRY，因为修复可能改变具体类型。")
public record GeometryRepairConfiguration(
        @JsonPropertyDescription("包含待修复 Geometry 的上游 Canvas 逻辑表名；必须引用进入节点前已经存在的表。")
        String sourceTableName,
        @JsonPropertyDescription("追加修复字段后的新逻辑表名；必须与进入节点时已有的所有表名不同，原来源表和原 Geometry 字段仍保留。")
        String outputTableName,
        @JsonPropertyDescription("要传给 ST_MakeValid 的 Geometry 字段名；必须具有受支持的完整空间类型定义。NULL 输入产生 NULL。")
        String geometryColumnName,
        @JsonPropertyDescription("追加的修复 Geometry 字段名；不能与来源表任何字段同名。结果保持来源 CRS/维度，具体 kind 声明为 GEOMETRY；修复失败会使节点执行失败。")
        String outputColumnName
) {
}
