package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Coordinates are in the source projected CRS, independently of the bin size display unit. */
@JsonClassDescription("Canvas 4.38 平面格网对齐与业务范围；坐标均使用来源投影 CRS 单位，与 binSize 的显示单位无关。方格原点是 (0,0) 索引单元左下角，flat-top 六边形原点是轴向索引 (0,0) 的中心。")
public record SpatialPlanarGridOptions(
        @JsonPropertyDescription("必填的有限原点 X 坐标，使用来源投影 CRS 单位；与实际格网边长相加后必须仍可由 Double 区分。")
        Double originX,
        @JsonPropertyDescription("必填的有限原点 Y 坐标，使用来源投影 CRS 单位；与实际格网边长相加后必须仍可由 Double 区分。")
        Double originY,
        @JsonPropertyDescription("可选的格网处理范围；NULL 与 DATA_BOUNDS 都按参与点的占用索引包络，EXPLICIT_BOUNDS 使用配置矩形过滤点并决定可补齐的单元。")
        Extent extent
) {
    @JsonClassDescription("平面格网范围方式：DATA_BOUNDS 根据实际参与点的索引包络；EXPLICIT_BOUNDS 使用来源 CRS 中的显式半开矩形。")
    public enum ExtentMode { DATA_BOUNDS, EXPLICIT_BOUNDS }
    @JsonClassDescription("平面格网范围；DATA_BOUNDS 忽略四个坐标草稿，EXPLICIT_BOUNDS 要求有限矩形并只接纳 [minX,maxX) × [minY,maxY) 内的点。补空时保留与该矩形有正面积相交的完整格网，不裁剪单元 Geometry。")
    public record Extent(
            @JsonPropertyDescription("必填的范围方式：DATA_BOUNDS 从参与点推导；EXPLICIT_BOUNDS 使用下列四个来源 CRS 坐标。")
            ExtentMode mode,
            @JsonPropertyDescription("EXPLICIT_BOUNDS 必填的有限最小 X，必须严格小于 maxX；DATA_BOUNDS 忽略。")
            Double minX,
            @JsonPropertyDescription("EXPLICIT_BOUNDS 必填的有限最小 Y，必须严格小于 maxY；DATA_BOUNDS 忽略。")
            Double minY,
            @JsonPropertyDescription("EXPLICIT_BOUNDS 必填的有限最大 X；该边界不包含点，DATA_BOUNDS 忽略。")
            Double maxX,
            @JsonPropertyDescription("EXPLICIT_BOUNDS 必填的有限最大 Y；该边界不包含点，DATA_BOUNDS 忽略。")
            Double maxY
    ) { }

    public boolean usesExplicitBounds() {
        return extent != null && extent.mode() == ExtentMode.EXPLICIT_BOUNDS;
    }
}
