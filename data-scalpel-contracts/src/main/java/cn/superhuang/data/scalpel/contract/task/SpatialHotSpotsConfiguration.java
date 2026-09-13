package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("有界批处理热点分析配置；将投影 XY Point 聚合到完整方格，并在固定距离邻域内计算 Getis-Ord Gi*。")
public record SpatialHotSpotsConfiguration(
        @JsonPropertyDescription("必填的有界上游 Canvas 逻辑点表名。")
        String sourceTableName,
        @JsonPropertyDescription("必填的投影 CRS XY Point Geometry 字段名；NULL/Empty 点不参与。")
        String pointGeometryColumnName,
        @JsonPropertyDescription("必填的分析数值来源；POINT_COUNT 与 ArcGIS GeoAnalytics Find Hot Spots 一致，FIELD_SUM 是显式平台扩展。")
        SpatialHotSpotAnalysisSource analysisSource,
        @JsonPropertyDescription("FIELD_SUM 时必填的数值字段；NULL 贡献 0，非有限值安全失败。POINT_COUNT 时作为非活动草稿保留并忽略。")
        String analysisColumnName,
        @JsonPropertyDescription("必填的有限正数方格边长。")
        double binSize,
        @JsonPropertyDescription("binSize 的线性单位；必须能换算到来源投影 CRS 的轴单位。")
        SpatialDistanceUnit binSizeUnit,
        @JsonPropertyDescription("必填的有限正数固定距离邻域半径；换算后必须严格大于 binSize，且比例最多为 64。")
        double neighborhoodDistance,
        @JsonPropertyDescription("neighborhoodDistance 的线性单位；必须能换算到来源投影 CRS 的轴单位。")
        SpatialDistanceUnit neighborhoodDistanceUnit,
        @JsonPropertyDescription("可选的左闭右开固定或日历时间切片；各切片独立计算 Gi* 和多重检验。")
        SpatialTemporalSlicing temporalSlicing,
        @JsonPropertyDescription("必填的置信分级策略；NONE 使用原始双侧 p 值，FDR_BH 使用每个时间片内的 Benjamini-Hochberg 调整值。")
        SpatialHotSpotMultipleTesting multipleTesting,
        @JsonPropertyDescription("当前节点产生的新 Canvas 逻辑结果表名。")
        String outputTableName,
        @JsonPropertyDescription("必填且唯一的 STRING 格网 ID 字段名。")
        String binIdColumnName,
        @JsonPropertyDescription("必填且唯一的 XY Polygon 格网 Geometry 字段名。")
        String binGeometryColumnName,
        @JsonPropertyDescription("必填且唯一的 LONG 格网点数字段名。")
        String pointCountColumnName,
        @JsonPropertyDescription("必填且唯一的 DOUBLE Gi* 分析值字段名；为点数或数值字段和。")
        String analysisValueColumnName,
        @JsonPropertyDescription("必填且唯一的 DOUBLE Gi* z-score 字段名。")
        String zScoreColumnName,
        @JsonPropertyDescription("必填且唯一的 DOUBLE 原始双侧 p-value 字段名。")
        String pValueColumnName,
        @JsonPropertyDescription("必填且唯一的 DOUBLE 多重检验后 p-value 字段名；NONE 时等于原始 p-value。")
        String adjustedPValueColumnName,
        @JsonPropertyDescription("必填且唯一的 INTEGER 置信分级字段名；取值 -3 至 3，0 表示不显著。")
        String confidenceBinColumnName
) {
    public static final double MAX_NEIGHBORHOOD_TO_BIN_RATIO = 64d;
    public static final long MAX_OUTPUT_CELLS = 1_000_000L;
}
