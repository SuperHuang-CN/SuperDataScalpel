package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("有界批处理空间密度配置；以投影 XY Point 为输入，在方格或六边形中心计算搜索半径内的点数及可选数量字段密度，并输出矢量面格网。")
public record SpatialDensityConfiguration(
        @JsonPropertyDescription("必填的有界上游 Canvas 逻辑点表名。")
        String sourceTableName,
        @JsonPropertyDescription("必填的投影 CRS XY Point Geometry 字段名；NULL/Empty 点不参与。")
        String pointGeometryColumnName,
        @JsonPropertyDescription("0 至 32 个可选数值数量字段；定义顺序就是结果字段顺序。点数密度不在此数组中并始终输出。")
        List<SpatialDensityField> fields,
        @JsonPropertyDescription("必填的密度权重；UNIFORM 为半径内常量贡献，KERNEL 为随距离平滑衰减的四次核。")
        SpatialDensityWeighting weighting,
        @JsonPropertyDescription("必填的 SQUARE 或 HEXAGON 矢量格网。")
        SpatialDensityBinShape binShape,
        @JsonPropertyDescription("必填的有限正数格网大小；方格为边长，六边形为对边距离。")
        double binSize,
        @JsonPropertyDescription("binSize 的线性单位；必须能换算到来源投影 CRS 的轴单位。")
        SpatialDistanceUnit binSizeUnit,
        @JsonPropertyDescription("必填的有限正数搜索半径；换算后必须严格大于 binSize，且半径/格网大小比最多为 512。")
        double radius,
        @JsonPropertyDescription("radius 的线性单位；必须能换算到来源投影 CRS 的轴单位。")
        SpatialDistanceUnit radiusUnit,
        @JsonPropertyDescription("必填的密度面积单位；只缩放密度数值，不改变格网、半径或成员关系。")
        SpatialAreaUnit areaUnit,
        @JsonPropertyDescription("可选的左闭右开固定或日历时间切片；仅 TIMESTAMP 瞬时时间可参与，NULL 时间排除。")
        SpatialTemporalSlicing temporalSlicing,
        @JsonPropertyDescription("当前节点产生的新 Canvas 逻辑结果表名。")
        String outputTableName,
        @JsonPropertyDescription("必填且唯一的 STRING 格网 ID 输出字段名。")
        String binIdColumnName,
        @JsonPropertyDescription("必填且唯一的 XY Polygon 格网 Geometry 输出字段名。")
        String binGeometryColumnName,
        @JsonPropertyDescription("必填且唯一的 DOUBLE 点数密度输出字段名。")
        String countDensityColumnName
) {
    public static final int MAX_FIELDS = 32;
    public static final double MAX_RADIUS_TO_BIN_RATIO = 512d;

    public SpatialDensityConfiguration {
        fields = fields == null ? null : List.copyOf(fields);
    }
}
