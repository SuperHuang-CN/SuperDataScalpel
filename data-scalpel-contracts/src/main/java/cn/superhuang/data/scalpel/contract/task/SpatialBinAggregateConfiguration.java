package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("有界批处理空间格网聚合配置；把 XY Point 分配到投影方格、投影六边形或 WGS84 H3 单元，再按格网、可选时间窗口和可选分组输出 1 至 32 项统计。结果为不带事件时间和 Watermark 的新有界表；该能力是点聚合，不是带邻域半径的密度计算。")

public record SpatialBinAggregateConfiguration(
        @JsonPropertyDescription("必填的有界上游 Canvas 逻辑表名；流式输入不受支持。")
        String sourceTableName,
        @JsonPropertyDescription("必填的 XY Point Geometry 字段名；SQUARE/HEXAGON 要求投影 CRS，H3 要求 EPSG:4326。显式平面格网与 H3 会排除 NULL/Empty 点，其他无效实际点在惰性执行时失败。")
        String pointGeometryColumnName,
        @JsonPropertyDescription("必填的格网形状：SQUARE 和 HEXAGON 构造来源投影 CRS 下的平面 Polygon；H3 使用原生球面单元并输出 EPSG:4326 XY MultiPolygon。")
        SpatialBinShape binShape,
        @JsonPropertyDescription("平面格网必填的有限正数大小；方格始终表示边长，六边形含义由 binSizeSemantics 决定。H3 APPROXIMATE_SIZE 将其视为期望平均对边距离，H3 RESOLUTION 忽略。")
        double binSize,
        @JsonPropertyDescription("binSize 的单位；平面格网必须能换算为来源投影 CRS 单位，H3 APPROXIMATE_SIZE 必须是明确线性单位。H3 RESOLUTION 忽略。")
        SpatialDistanceUnit binSizeUnit,
        @JsonPropertyDescription("是否补齐平面范围内没有参与点的格网；true 可能显著扩展结果，显式范围最多生成 100 万候选单元。H3 只支持 false；有时间切片时只组合实际出现的有效窗口，不凭空生成时间范围。")
        boolean includeEmptyBins,
        @JsonPropertyDescription("必填的 1 至 32 项格网统计，至少包含一个 COUNT；每项 ID 必须是唯一 UUID，所有统计输出名还必须与格网、时间和分组输出名保持唯一。")
        List<SpatialBinStatistic> statistics,
        @JsonPropertyDescription("可选的分类分组；存在时结果粒度变为每个格网/时间窗口/分组值一行，并可追加少数、多数和百分比字段。NULL 表示不按业务字段分组。")
        SpatialGroupSummary groupSummary,
        @JsonPropertyDescription("可选的左闭右开固定或日历时间窗口；重叠窗口会让一条观测进入多行结果，留空窗口会排除落在间隙中的观测，NULL 时间不参与。")
        SpatialTemporalSlicing temporalSlicing,
        @JsonPropertyDescription("当前操作产生的 Canvas 逻辑表名；必须在任务定义内唯一，后续节点通过该值引用结果。")
        String outputTableName,
        @JsonPropertyDescription("必填且不能与其他结果字段重名的 STRING 格网 ID 输出字段名；H3 输出原生 Cell ID，旧平面配置输出 SHAPE:q:r，显式 planarGrid 使用包含形状、CRS、实际边长和原点指纹的新命名空间。")
        String binIdColumnName,
        @JsonPropertyDescription("必填且不能与其他结果字段重名的格网 Geometry 输出字段名；平面格网为来源 CRS 的 XY Polygon，H3 为 EPSG:4326 XY MultiPolygon。")
        String binGeometryColumnName,
        @JsonPropertyDescription("平面六边形尺寸语义；NULL 按 LEGACY_SIDE_LENGTH 兼容旧定义。方格在两种语义下都使用边长；显式非 NULL 值要求 Canvas 4.21 或更高版本。H3 不使用该字段。")
        SpatialBinSizeSemantics binSizeSemantics,
        @JsonPropertyDescription("H3 分辨率配置；binShape=H3 时必须存在并选择 mode，其他形状下作为非活动草稿保留。H3 形状或任意非 NULL h3 对象要求 Canvas 4.33 或更高版本。")
        SpatialH3Options h3,
        @JsonPropertyDescription("Canvas 4.38 平面格网的显式原点与范围；仅 SQUARE/HEXAGON 执行，H3 下作为非活动草稿保留。NULL 使用旧版原点 (0,0)、数据索引包络及旧 ID。")
        SpatialPlanarGridOptions planarGrid
) {
    public static final int MAX_STATISTICS = 32;

    public SpatialBinAggregateConfiguration {
        statistics = statistics == null ? null : List.copyOf(statistics);
    }

    public SpatialBinSizeSemantics effectiveBinSizeSemantics() {
        return binSizeSemantics == null ? SpatialBinSizeSemantics.LEGACY_SIDE_LENGTH : binSizeSemantics;
    }

    public SpatialBinAggregateConfiguration(
            String sourceTableName, String pointGeometryColumnName, SpatialBinShape binShape, double binSize,
            SpatialDistanceUnit binSizeUnit, boolean includeEmptyBins, List<SpatialBinStatistic> statistics,
            SpatialGroupSummary groupSummary, SpatialTemporalSlicing temporalSlicing, String outputTableName,
            String binIdColumnName, String binGeometryColumnName, SpatialBinSizeSemantics binSizeSemantics, SpatialH3Options h3
    ) {
        this(sourceTableName, pointGeometryColumnName, binShape, binSize, binSizeUnit, includeEmptyBins,
                statistics, groupSummary, temporalSlicing, outputTableName, binIdColumnName, binGeometryColumnName,
                binSizeSemantics, h3, null);
    }

    public SpatialBinAggregateConfiguration(
            String sourceTableName, String pointGeometryColumnName, SpatialBinShape binShape, double binSize,
            SpatialDistanceUnit binSizeUnit, boolean includeEmptyBins, List<SpatialBinStatistic> statistics,
            SpatialGroupSummary groupSummary, SpatialTemporalSlicing temporalSlicing, String outputTableName,
            String binIdColumnName, String binGeometryColumnName, SpatialBinSizeSemantics binSizeSemantics
    ) {
        this(sourceTableName, pointGeometryColumnName, binShape, binSize, binSizeUnit, includeEmptyBins,
                statistics, groupSummary, temporalSlicing, outputTableName, binIdColumnName, binGeometryColumnName, binSizeSemantics, null);
    }

    public SpatialBinAggregateConfiguration(
            String sourceTableName, String pointGeometryColumnName, SpatialBinShape binShape, double binSize,
            SpatialDistanceUnit binSizeUnit, boolean includeEmptyBins, List<SpatialBinStatistic> statistics,
            SpatialGroupSummary groupSummary, SpatialTemporalSlicing temporalSlicing, String outputTableName,
            String binIdColumnName, String binGeometryColumnName
    ) {
        this(sourceTableName, pointGeometryColumnName, binShape, binSize, binSizeUnit, includeEmptyBins,
                statistics, groupSummary, temporalSlicing, outputTableName, binIdColumnName, binGeometryColumnName, null);
    }
}
