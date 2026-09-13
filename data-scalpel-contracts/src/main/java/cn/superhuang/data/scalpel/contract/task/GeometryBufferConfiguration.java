package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("逐行 Geometry 缓冲配置，支持批处理和流处理。保留来源字段并追加 MULTIPOLYGON 结果；NULL Geometry 或 NULL 动态距离返回 NULL，非空输入也可能产生 Empty，但不删除来源行。节点不转换 CRS、不合并相邻缓冲区，也不支持负距离、零距离或样式参数。Canvas 4.49 可显式配置距离单位，4.52 可从数值字段或受控表达式逐行计算距离。")
public record GeometryBufferConfiguration(
        @JsonPropertyDescription("要处理的上游 Canvas 逻辑表名，必须精确引用此前节点已经产生的可用输出表；其他上游表继续传播但不参与缓冲。")
        String sourceTableName,
        @JsonPropertyDescription("追加缓冲结果使用的 Canvas 逻辑表名，必须与当前所有上游表名不同；不会替换来源表。")
        String outputTableName,
        @JsonPropertyDescription("要生成缓冲区的来源 Geometry 字段名，必须具有完整的 EPSG CRS 定义且坐标维度为 XY；原 Geometry 字段保持不变。")
        String geometryColumnName,
        @JsonPropertyDescription("追加的缓冲 Geometry 字段名，不能与来源字段重名；结果固定经 ST_Multi 规范化为 MULTIPOLYGON，并继承来源 CRS、维度及 nullable。")
        String outputColumnName,
        @JsonPropertyDescription("固定距离草稿。distanceSource 缺失、null 或 CONSTANT 时必填且必须为有限正数；切换距离来源不会清除此值。")
        double distance,
        @JsonPropertyDescription("必填缓冲模式。PLANAR 直接使用来源 CRS 坐标空间，EPSG:4326 时距离为角度并产生警告；SPHEROID 使用 WGS84 椭球和米，只接受 EPSG:4326。")
        SpatialMeasureMode mode,
        @JsonPropertyDescription("Canvas 4.49 起可选的显式距离单位。PLANAR 将距离换算为来源投影 CRS 轴单位，地理 CRS 只接受 SOURCE_CRS_UNIT；SPHEROID 将距离换算为米且不接受 SOURCE_CRS_UNIT。缺失或 null 保持旧语义：PLANAR 使用来源 CRS 单位，SPHEROID 使用米。")
        SpatialDistanceUnit distanceUnit,
        @JsonPropertyDescription("Canvas 4.52 起可选距离来源。缺失或 null 保持旧版 CONSTANT 语义；非 null 值要求 Canvas 4.52。")
        GeometryBufferDistanceSource distanceSource,
        @JsonPropertyDescription("FIELD 距离来源使用的数值字段；其他模式下作为可恢复草稿保留但不执行。字段实际值必须为有限正数，NULL 产生 NULL Buffer。")
        String distanceFieldName,
        @JsonPropertyDescription("EXPRESSION 距离来源使用的受控确定性逐行数值表达式；不允许 SQL 语句、聚合、窗口、生成器或子查询。其他模式下作为可恢复草稿保留但不执行。")
        String distanceExpression
) {
    /** Compatibility constructor for definitions written before explicit Buffer units. */
    public GeometryBufferConfiguration(
            String sourceTableName,
            String outputTableName,
            String geometryColumnName,
            String outputColumnName,
            double distance,
            SpatialMeasureMode mode
    ) {
        this(sourceTableName, outputTableName, geometryColumnName, outputColumnName,
                distance, mode, null, null, null, null);
    }

    /** Compatibility constructor for Canvas 4.49 fixed-distance definitions. */
    public GeometryBufferConfiguration(
            String sourceTableName,
            String outputTableName,
            String geometryColumnName,
            String outputColumnName,
            double distance,
            SpatialMeasureMode mode,
            SpatialDistanceUnit distanceUnit
    ) {
        this(sourceTableName, outputTableName, geometryColumnName, outputColumnName,
                distance, mode, distanceUnit, null, null, null);
    }

    public SpatialDistanceUnit effectiveDistanceUnit() {
        if (distanceUnit != null) return distanceUnit;
        return mode == SpatialMeasureMode.SPHEROID
                ? SpatialDistanceUnit.METERS
                : SpatialDistanceUnit.SOURCE_CRS_UNIT;
    }

    public GeometryBufferDistanceSource effectiveDistanceSource() {
        return distanceSource == null ? GeometryBufferDistanceSource.CONSTANT : distanceSource;
    }
}
