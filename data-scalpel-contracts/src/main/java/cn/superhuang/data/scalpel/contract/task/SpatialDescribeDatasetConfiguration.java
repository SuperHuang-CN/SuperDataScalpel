package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("有界批处理数据集剖析配置；输出字段统计、结构化描述以及可选样本和空间范围。")
public record SpatialDescribeDatasetConfiguration(
        @JsonPropertyDescription("需要剖析的上游 Canvas 逻辑表名。")
        String sourceTableName,
        @JsonPropertyDescription("可选的 Geometry 字段；配置后用于空间统计与可选范围 Polygon。")
        String geometryColumnName,
        @JsonPropertyDescription("字段统计结果的 Canvas 逻辑表名。")
        String statisticsTableName,
        @JsonPropertyDescription("数据集描述及 description_json 结果的 Canvas 逻辑表名。")
        String descriptionTableName,
        @JsonPropertyDescription("样本行数量，0 表示不生成样本表，最大 10000。")
        int sampleSize,
        @JsonPropertyDescription("样本结果表名；仅在 sampleSize 大于 0 时必填。")
        String sampleTableName,
        @JsonPropertyDescription("是否输出所选 Geometry 的 XY Envelope Polygon。")
        boolean extentOutput,
        @JsonPropertyDescription("空间范围结果表名；仅在 extentOutput 为 true 时必填。")
        String extentTableName
) {
    public static final int MAX_SAMPLE_SIZE = 10_000;
}
