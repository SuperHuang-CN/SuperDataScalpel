package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("轨迹重建的排序、分段和结果形态选项。ORDERED_SEGMENTS 使用确定性完整排序键并支持连接端点共享；缺失整个对象或显式 LEGACY_POINTS 保留旧路径。")

public record TrackReconstructOptions(
        @JsonPropertyDescription("执行语义。null 按 ORDERED_SEGMENTS；ORDERED_SEGMENTS 启用确定性排序、分段和新路径/面配置，LEGACY_POINTS 使用旧版点轨迹实现。")
        TrackReconstructSemantics semantics,
        @JsonPropertyDescription("同一轨迹内在时间字段之后依次升序比较的字段，NULL 在前；完整排序键重复会使运行失败，空列表要求同一轨迹的时间唯一。FIRST、LAST 和几何顶点均使用该顺序。")
        List<String> orderByColumns,
        @JsonPropertyDescription("普通时间/距离 gap 或表达式切分处的端点归属：GAP 不共享，FINISH_LAST 把后段首观测复制到前段，START_NEXT 把前段末观测复制到后段；null 按 GAP。固定周期边界始终 GAP。")
        TrackSplitBoundaryOption splitBoundaryOption,
        @JsonPropertyDescription("可选受控 BOOLEAN 表达式；结果 true 时从当前观测前切分，false 或 NULL 不切分。它与各边界条件按 OR 生效，并且不会跨固定周期读取窗口。")
        TrackSplitExpression splitExpression,
        @JsonPropertyDescription("线轨迹路径构造配置；仅有序且未启用 areaGeometry 时使用。为空保留旧版 LineString 构造。")
        TrackPathGeometryOptions pathGeometry,
        @JsonPropertyDescription("可选活动区域构造配置；启用后覆盖线轨迹路径选项，逐观测形成足迹并按时间顺序合成 MultiPolygon。")
        TrackAreaGeometryOptions areaGeometry
) {
    public TrackReconstructOptions {
        orderByColumns = orderByColumns == null ? List.of() : List.copyOf(orderByColumns);
    }

    public TrackReconstructOptions(TrackReconstructSemantics semantics, List<String> orderByColumns,
            TrackSplitBoundaryOption splitBoundaryOption, TrackSplitExpression splitExpression,
            TrackPathGeometryOptions pathGeometry) {
        this(semantics, orderByColumns, splitBoundaryOption, splitExpression, pathGeometry, null);
    }

    public TrackReconstructOptions(TrackReconstructSemantics semantics, List<String> orderByColumns,
            TrackSplitBoundaryOption splitBoundaryOption, TrackSplitExpression splitExpression) {
        this(semantics, orderByColumns, splitBoundaryOption, splitExpression, null, null);
    }

    public TrackSplitBoundaryOption effectiveBoundaryOption() {
        return splitBoundaryOption == null ? TrackSplitBoundaryOption.GAP : splitBoundaryOption;
    }
}
