package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("空间中心与离散的一项分析。MEAN_CENTER、MEDIAN_CENTER 输出 POINT；STANDARD_DISTANCE、DIRECTIONAL_ELLIPSE 输出 POLYGON；CENTRAL_FEATURE 在独立模式返回选中的原始 Geometry。独立模式每个正总权重组在本项表中输出一行，退化离散结果可以是 Empty Polygon。")
public record SpatialCenterDispersionAnalysis(
        @JsonPropertyDescription("分析项稳定 UUID，必须可解析为 UUID 且在 analyses 内唯一；排序调整后保持不变，用于编辑、诊断和血缘定位。")
        String analysisId,
        @JsonPropertyDescription("必填分析类型，同一节点中每种最多一次。MEAN_CENTER 为加权平均位置；MEDIAN_CENTER 最小化加权距离和；CENTRAL_FEATURE 从原始候选中选加权总距离最小者；STANDARD_DISTANCE 为平台扩展圆；DIRECTIONAL_ELLIPSE 为方向离散椭圆。")
        SpatialCenterDispersionKind kind,
        @JsonPropertyDescription("本项结果 Geometry 字段名。LEGACY_WIDE 中须与分组字段及其他分析输出按大小写不敏感规则唯一；ANALYSIS_TABLES 中只须在本项表内不与投影字段重名，不同分析表可复用同名 Geometry 字段。")
        String outputColumnName,
        @JsonPropertyDescription("STANDARD_DISTANCE 或 DIRECTIONAL_ELLIPSE 必填的倍数，只允许 1、2、3；其他 kind 必须为 null。它按当前人口加权矩放大半径/半轴，不表示结果必然覆盖固定百分比的任意数据。")
        Integer standardDeviations,
        @JsonPropertyDescription("ANALYSIS_TABLES 模式必填的本项结果逻辑表名，必须与所有输入表及其他分析结果表不同；LEGACY_WIDE 忽略并保留该草稿值。")
        String outputTableName,
        @JsonPropertyDescription("仅 ANALYSIS_TABLES 的 CENTRAL_FEATURE 使用的可选原记录字段投影。null 使用兼容输出：分组字段、未重复的 featureId 和结果 Geometry；显式空数组只输出结果 Geometry；非空数组只按顺序输出 included=true 的字段再追加结果 Geometry。其他 kind 或 LEGACY_WIDE 忽略并保留草稿。")
        java.util.List<SpatialCenterFeatureColumn> centralFeatureColumns
) {
    public SpatialCenterDispersionAnalysis {
        if (centralFeatureColumns != null) centralFeatureColumns = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(centralFeatureColumns));
    }
    public SpatialCenterDispersionAnalysis(String analysisId, SpatialCenterDispersionKind kind, String outputColumnName, Integer standardDeviations, String outputTableName) {
        this(analysisId, kind, outputColumnName, standardDeviations, outputTableName, null);
    }
    public SpatialCenterDispersionAnalysis(String analysisId, SpatialCenterDispersionKind kind, String outputColumnName, Integer standardDeviations) {
        this(analysisId, kind, outputColumnName, standardDeviations, null);
    }
}
