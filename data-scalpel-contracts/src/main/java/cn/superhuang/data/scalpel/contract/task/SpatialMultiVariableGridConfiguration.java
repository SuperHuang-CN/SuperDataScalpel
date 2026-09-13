package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("有界批处理多变量格网配置；以所有变量来源 Geometry 的共同外包范围生成统一方格或六边形，并在格网中心计算各变量。")
public record SpatialMultiVariableGridConfiguration(
        @JsonPropertyDescription("1 至 32 个有序变量；每项生成一个结果字段。")
        List<SpatialMultiVariableGridVariable> variables,
        @JsonPropertyDescription("必填的 SQUARE 或 HEXAGON 矢量格网。")
        SpatialDensityBinShape binShape,
        @JsonPropertyDescription("必填的有限正数格网大小；方格为边长，六边形为对边距离。")
        double binSize,
        @JsonPropertyDescription("binSize 的线性单位；必须能换算到所有来源共同投影 CRS 的轴单位。")
        SpatialDistanceUnit binSizeUnit,
        @JsonPropertyDescription("当前节点产生的新 Canvas 逻辑结果表名。")
        String outputTableName,
        @JsonPropertyDescription("必填且唯一的 STRING 格网 ID 字段名。")
        String binIdColumnName,
        @JsonPropertyDescription("必填且唯一的 XY Polygon 格网 Geometry 字段名。")
        String binGeometryColumnName
) {
    public static final int MAX_VARIABLES = 32;
    public static final double MAX_SEARCH_TO_BIN_RATIO = 512d;
    public static final long MAX_OUTPUT_CELLS = 1_000_000L;

    public SpatialMultiVariableGridConfiguration {
        variables = variables == null ? null : List.copyOf(variables);
    }
}
