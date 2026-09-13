package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("逐行 Geometry 拆分配置，支持批处理和流处理。使用外层展开把 MultiGeometry 或 GeometryCollection 的部件拆成多行并复制全部来源字段；单部件输入产生一行，NULL 或 Empty 输入也保留一行且部件为空。节点可能增加行数，不设置每行展开上限。")
public record GeometryExplodeConfiguration(
        @JsonPropertyDescription("要拆分的上游 Canvas 逻辑表名，必须精确引用此前节点已经产生的可用输出表；其他上游表继续传播但不参与拆分。")
        String sourceTableName,
        @JsonPropertyDescription("追加拆分结果使用的 Canvas 逻辑表名，必须与当前所有上游表名不同；不会替换来源表。")
        String outputTableName,
        @JsonPropertyDescription("要传给 ST_Dump 的来源 Geometry 字段名，必须具有完整的 EPSG CRS 定义且坐标维度为 XY；原 Geometry 字段会复制到每个结果行。")
        String geometryColumnName,
        @JsonPropertyDescription("追加的部件 Geometry 字段名，不能与来源字段或 partIndexColumnName 重名。MultiPoint、MultiLineString、MultiPolygon 分别声明 Point、LineString、Polygon；通用或集合输入声明 GEOMETRY；字段始终 nullable。")
        String outputColumnName,
        @JsonPropertyDescription("可选部件序号字段名。null 表示不输出；非 null 时必须为非空且不重名，追加 nullable INTEGER，正常部件按 ST_Dump 顺序从 0 开始，NULL 或 Empty 输入时为 null。")
        String partIndexColumnName
) {
}
