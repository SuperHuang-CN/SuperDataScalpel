package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("有界批处理空间中心与离散分析配置。按最多 8 个标量字段分组，使用可选非负权重执行 1 至 16 种不重复分析。ANALYSIS_TABLES 为每项生成独立表并支持点、线、面家族，线面以质心参与位置计算；LEGACY_WIDE 只支持 Point 并把所有 Geometry 放入一张宽表。结果使用来源投影 CRS 的 XY 平面距离，不支持地理 CRS。")

public record SpatialCenterDispersionConfiguration(
        @JsonPropertyDescription("要分析的上游 Canvas 逻辑表名，必须精确引用一张 BOUNDED 表；输入 Map 中其他表继续传播。")
        String sourceTableName,
        @JsonPropertyDescription("分析来源 Geometry 字段名，必须为投影 EPSG CRS 和 XY。ANALYSIS_TABLES 接受 Point/MultiPoint、Line/MultiLine、Polygon/MultiPolygon，通用 GEOMETRY 在运行时只允许这些实际类型；所有位置统计使用每个要素的质心，CENTRAL_FEATURE 返回原 Geometry。LEGACY_WIDE 只接受 POINT。NULL、Empty 或无效 Geometry 不产生有效观测，其中无效值会使任务失败。")
        String pointGeometryColumnName,
        @JsonPropertyDescription("CENTRAL_FEATURE 必填的来源标量唯一键。ANALYSIS_TABLES 运行时要求所有参与候选的值非 null 且在整个有效输入中唯一，用于稳定平局排序和回接原记录；没有 CENTRAL_FEATURE 时忽略。LEGACY_WIDE 中只在中央要素需要时强制必填。")
        String featureIdColumnName,
        @JsonPropertyDescription("有序分组字段数组，必须存在、最多 8 项，字段按大小写不敏感规则不能重复且不能是 GEOMETRY；空数组把全部有效观测作为一个组。ANALYSIS_TABLES 每个正总权重组在每张分析表输出一行。")
        List<String> groupByColumns,
        @JsonPropertyDescription("可选数值权重字段；null/空白表示每个要素权重 1，NULL 权重行不参与。ANALYSIS_TABLES 要求有限非负值：负数、NaN、Infinity 失败；零权重不影响平均、中位和离散结果，但仍可作为 CENTRAL_FEATURE 候选并计入容量限制，总权重为 0 的组不输出。LEGACY_WIDE 只显式拒绝负数，不具备新版非有限值隔离。")
        String weightColumnName,
        @JsonPropertyDescription("有序分析数组，必须包含 1 至 16 项且不能含不完整项；analysisId 必须唯一 UUID，同一 kind 最多一次。ANALYSIS_TABLES 按数组顺序向 Map 追加独立结果表；LEGACY_WIDE 按数组顺序向同一结果行追加 Geometry 字段。")
        List<SpatialCenterDispersionAnalysis> analyses,
        @JsonPropertyDescription("LEGACY_WIDE 或 resultMode=null 时必填的单一宽结果表名，必须与所有输入表不同。ANALYSIS_TABLES 忽略并保留此兼容字段，各项改用 analysis.outputTableName。")
        String outputTableName,
        @JsonPropertyDescription("结果组织模式。ANALYSIS_TABLES 使用新版数值、容量和原要素语义，每项产生独立 BOUNDED 表；LEGACY_WIDE 或 null 使用旧算法并在一张 BOUNDED 宽表输出，来源必须为 POINT。两种模式都清除 Watermark，且都不计算平均/中位/椭圆时间结果。")
        SpatialCenterResultMode resultMode
) {
    public static final int MAX_GROUP_COLUMNS = 8;
    public static final int MAX_ANALYSES = 16;

    public SpatialCenterDispersionConfiguration {
        groupByColumns = groupByColumns == null ? null : List.copyOf(groupByColumns);
        analyses = analyses == null ? null : List.copyOf(analyses);
    }
    public SpatialCenterDispersionConfiguration(String sourceTableName, String pointGeometryColumnName, String featureIdColumnName,
            List<String> groupByColumns, String weightColumnName, List<SpatialCenterDispersionAnalysis> analyses, String outputTableName) {
        this(sourceTableName, pointGeometryColumnName, featureIdColumnName, groupByColumns, weightColumnName, analyses, outputTableName, null);
    }
    public boolean separateResults() { return resultMode == SpatialCenterResultMode.ANALYSIS_TABLES; }
}
