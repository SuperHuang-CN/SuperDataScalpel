package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Optional area reconstruction; inactive buffer branches remain in the saved draft. */
@JsonClassDescription("有序轨迹的 MultiPolygon 活动区域配置。每个有效 Point 必须按字段或表达式缓冲，Polygon/MultiPolygon 可直接使用原几何；单个有效面观测也会产生结果。各观测足迹在普通 gap 或表达式端点共享之前计算，固定周期仍完全隔离。")
public record TrackAreaGeometryOptions(
        @JsonPropertyDescription("是否启用 MultiPolygon 活动区域输出；null 按启用，false 时保留其余草稿但不执行。")
        Boolean enabled,
        @JsonPropertyDescription("逐观测缓冲来源：NONE 不缓冲，FIELD 从数值字段读取，EXPRESSION 计算数值。Point 不允许 NONE；Polygon/MultiPolygon 可用 NONE。")
        TrackBufferMode bufferMode,
        @JsonPropertyDescription("FIELD 模式的数值来源字段；每个有效观测读取自己的缓冲半径，其他模式忽略。")
        String bufferField,
        @JsonPropertyDescription("EXPRESSION 模式的受控数值表达式；可引用当前来源字段和 windowBindings，必须为每个有效观测产生可用半径。其他模式忽略。")
        String bufferExpression,
        @JsonPropertyDescription("FIELD 或 EXPRESSION 结果的距离单位；PLANAR 按来源 CRS 换算，GEODESIC 按 WGS84 椭球解释。NONE 模式不使用。")
        SpatialDistanceUnit bufferUnit,
        @JsonPropertyDescription("EXPRESSION 可引用的 0～32 个包含式观测窗口统计绑定；窗口先按轨迹和固定周期计算，再执行普通 gap/表达式切分，其他缓冲模式不使用。")
        List<TrackBufferWindowBinding> windowBindings,
        @JsonPropertyDescription("GEODESIC 面轨迹的独立边界采样参数；控制输出边界离散粒度，不是位置误差保证，也不复用隐藏的线轨迹段长。")
        TrackGeodesicAreaOptions geodesicBoundary
) {
    public TrackAreaGeometryOptions {
        windowBindings = windowBindings == null ? List.of() : List.copyOf(windowBindings);
    }
    public TrackAreaGeometryOptions(Boolean enabled, TrackBufferMode bufferMode, String bufferField,
            String bufferExpression, SpatialDistanceUnit bufferUnit) {
        this(enabled, bufferMode, bufferField, bufferExpression, bufferUnit, List.of());
    }
    public TrackAreaGeometryOptions(Boolean enabled, TrackBufferMode bufferMode, String bufferField,
            String bufferExpression, SpatialDistanceUnit bufferUnit, List<TrackBufferWindowBinding> windowBindings) {
        this(enabled, bufferMode, bufferField, bufferExpression, bufferUnit, windowBindings, null);
    }
    public boolean active() { return !Boolean.FALSE.equals(enabled); }
}
