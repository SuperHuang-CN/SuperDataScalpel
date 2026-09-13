package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import cn.superhuang.data.scalpel.contract.type.CrsReference;

@JsonClassDescription("批处理坐标转换配置；在新逻辑表中以 Sedona ST_Transform 原位替换一个 Geometry 字段的坐标和 CRS 元数据，保留其他字段、GeometryKind、XY 维度和可空性。来源表仍保留。")

public record SpatialTransformConfiguration(
        @JsonPropertyDescription("包含待转换 Geometry 的上游 Canvas 逻辑表名；必须引用进入节点前已经存在的表。")
        String sourceTableName,
        @JsonPropertyDescription("坐标转换后的新逻辑表名；必须与进入节点时已有的所有表名不同，原来源表仍保留。")
        String outputTableName,
        @JsonPropertyDescription("要原位重投影的 Geometry 字段名；字段必须具有完整的 EPSG CRS 和 XY 维度，名称在结果中不变。NULL Geometry 保持 NULL。")
        String geometryColumnName,
        @JsonPropertyDescription("目标坐标参考系；当前只支持 authority=EPSG 且 code>0。与来源 CRS 相同时坐标不变并产生无效果警告。")
        CrsReference targetCrs
) {
}
