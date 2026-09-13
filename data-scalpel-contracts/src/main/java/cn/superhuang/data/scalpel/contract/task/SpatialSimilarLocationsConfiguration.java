package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("有界批处理查找相似位置配置；按多个同名数值字段比较参考要素与候选要素。")
public record SpatialSimilarLocationsConfiguration(
        @JsonPropertyDescription("包含一个或多个参考位置的上游 Canvas 逻辑表名。")
        String referenceTableName,
        @JsonPropertyDescription("参考表中必填、非 Geometry、运行时非空且唯一的稳定身份字段。")
        String referenceIdColumnName,
        @JsonPropertyDescription("参考表中需要随结果返回的 XY Geometry 字段。")
        String referenceGeometryColumnName,
        @JsonPropertyDescription("可选的参考位置筛选；同一张表可通过不同筛选同时作为参考和候选。")
        CanvasFilterCondition referenceFilter,
        @JsonPropertyDescription("包含待排名位置的上游 Canvas 逻辑候选表名。")
        String candidateTableName,
        @JsonPropertyDescription("候选表中必填、非 Geometry、运行时非空且唯一的稳定身份字段；同时用于并列排序。")
        String candidateIdColumnName,
        @JsonPropertyDescription("候选表中需要随结果返回的 XY Geometry 字段；必须与参考 Geometry 的类型、CRS 和维度一致。")
        String candidateGeometryColumnName,
        @JsonPropertyDescription("可选的候选位置筛选。")
        CanvasFilterCondition candidateFilter,
        @JsonPropertyDescription("按顺序参与匹配的同名同类型数值字段，最少 1 项、最多 32 项；属性轮廓最少 2 项。")
        List<SpatialSimilarLocationsAnalysisField> analysisFields,
        @JsonPropertyDescription("只用于解释结果的候选字段，最多 64 项，不参与标准化或排名。")
        List<SpatialSimilarLocationsAppendField> appendFields,
        @JsonPropertyDescription("必填的匹配方法：标准化属性值平方差或标准化属性轮廓余弦差异。")
        SpatialSimilarLocationsMatchMethod matchMethod,
        @JsonPropertyDescription("必填的返回范围：最相似、最不相似或两端候选。")
        SpatialSimilarLocationsResultMode resultMode,
        @JsonPropertyDescription("每端返回的候选数量，必须在 1 至 10000 之间；BOTH 在候选不足时自动缩小到两端互不重叠。")
        int numberOfResults,
        @JsonPropertyDescription("当前节点产生的新 Canvas 逻辑结果表名。")
        String outputTableName,
        @JsonPropertyDescription("结果中的 XY Geometry 字段名。")
        String outputGeometryColumnName,
        @JsonPropertyDescription("结果中的位置类型字段名；值为 REFERENCE 或 CANDIDATE。")
        String locationTypeColumnName,
        @JsonPropertyDescription("候选相似度排名字段名；1 表示最相似，参考行固定为 0。")
        String similarityRankColumnName,
        @JsonPropertyDescription("候选不相似度排名字段名；-1 表示最不相似，参考行固定为 0。")
        String dissimilarityRankColumnName,
        @JsonPropertyDescription("ATTRIBUTE_VALUES 的平方差索引字段名；0 表示完全相同，其他方法中为 NULL。")
        String similarityIndexColumnName,
        @JsonPropertyDescription("ATTRIBUTE_PROFILES 的余弦差异字段名；取值 0 至 2，0 表示轮廓完全相同，其他方法中为 NULL。")
        String cosineIndexColumnName,
        @JsonPropertyDescription("用于结果渲染的有符号排名字段名；正值越大越相似，负值越小越不相似，参考行固定为 0。")
        String labelRankColumnName,
        @JsonPropertyDescription("参考身份输出字段名；候选行中为 NULL。")
        String referenceIdOutputColumnName,
        @JsonPropertyDescription("候选身份输出字段名；参考行中为 NULL。")
        String searchIdOutputColumnName
) {
    public static final int MAX_ANALYSIS_FIELDS = 32;
    public static final int MAX_APPEND_FIELDS = 64;
    public static final int MAX_NUMBER_OF_RESULTS = 10_000;

    public SpatialSimilarLocationsConfiguration {
        analysisFields = analysisFields == null ? null : List.copyOf(analysisFields);
        appendFields = appendFields == null ? null : List.copyOf(appendFields);
    }
}
