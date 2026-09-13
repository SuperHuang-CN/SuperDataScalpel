package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("范围内汇总的区域来源。null 或 AREA_TABLE 使用上游 Polygon/MultiPolygon 区域表；PLANAR_GRID 在被汇总 Geometry 的投影 CRS 中生成稳定方格或六边形，再复用相同相交与统计流程。非活动的表或格网配置仍作为草稿保存，不参与执行。")
public record SpatialWithinRegions(
        @JsonPropertyDescription("必填区域模式。AREA_TABLE 使用 configuration.areaTableName；PLANAR_GRID 只需被汇总表并生成平面格网。对象本身为 null 时兼容旧定义并按 AREA_TABLE；对象存在但 mode=null 是无效草稿。")
        Mode mode,
        @JsonPropertyDescription("PLANAR_GRID 必填格网形状，仅支持 SQUARE 或 HEXAGON；H3 不受支持。SQUARE 的 binSize 为边长，HEXAGON 为对边距离。AREA_TABLE 模式忽略。")
        SpatialBinShape binShape,
        @JsonPropertyDescription("PLANAR_GRID 必填的有限正格网大小；与 binSizeUnit 一起换算到被汇总 Geometry 的 CRS 轴单位。切换单位不会自动改写该数值。")
        Double binSize,
        @JsonPropertyDescription("PLANAR_GRID 必填大小单位。格网只接受可解析线性单位的投影 CRS，不允许地理角度单位；系统不自动选择或转换投影。")
        SpatialDistanceUnit binSizeUnit,
        @JsonPropertyDescription("PLANAR_GRID 必填的原点与范围配置。方格原点是某格左下角，六边形原点是中心；DATA_BOUNDS 从参与要素惰性求范围，EXPLICIT_BOUNDS 使用配置范围且可能输出超出边界的完整格子。")
        SpatialPlanarGridOptions planarGrid,
        @JsonPropertyDescription("PLANAR_GRID 主结果中的非空 STRING 格网 ID 字段名，必须与 Geometry 及其他输出字段不同。ID 由形状、CRS、大小、原点和格子索引决定，不随查询范围、分组或窗口变化。")
        String binIdColumnName,
        @JsonPropertyDescription("PLANAR_GRID 主结果中的非空 POLYGON Geometry 字段名，CRS 为被汇总要素 CRS、维度为 XY；输出完整格子，不按显式范围裁剪格子。")
        String binGeometryColumnName
) {
    @JsonClassDescription("范围内汇总区域模式：AREA_TABLE 读取上游面区域；PLANAR_GRID 根据被汇总要素的投影 CRS 和配置范围生成方格或六边形。")
    public enum Mode { AREA_TABLE, PLANAR_GRID }
    public boolean usesGrid() { return mode == Mode.PLANAR_GRID; }
}
