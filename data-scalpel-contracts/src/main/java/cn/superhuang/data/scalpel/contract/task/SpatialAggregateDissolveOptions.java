package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("Canvas 4.53 起的 Dissolve 输出选项。只适用于恰好一个 UNION Geometry 聚合；Canvas 4.61 可显式选择兼容的 All/List 或无字段空间连通组。")
public record SpatialAggregateDissolveOptions(
        @JsonPropertyDescription("是否执行 Dissolve 输出语义。false 时其余字段只作为非活动草稿保留，不影响空间聚合结果。")
        boolean enabled,
        @JsonPropertyDescription("true 把每组融合结果规范化为 multipart；false 使用 ST_Dump 输出单部件行。单部件结果会为每个部件重复分组字段、计数和统计。")
        boolean multipart,
        @JsonPropertyDescription("必填的组内来源要素总数字段名，输出 LONG；它独立于各统计项的 COUNT_FIELD。")
        String countOutputColumnName,
        @JsonPropertyDescription("有序标量统计数组，最多 32 项。允许为空；各项在融合前按同一组来源记录计算。")
        List<SpatialAggregateStatistic> summaryStatistics,
        @JsonPropertyDescription("Canvas 4.61 起的可选分组方式。缺失/null 等同 ALL_OR_FIELDS，保持旧空分组全局融合语义；CONNECTED_COMPONENTS 要求不配置分组字段，并按面要素相交或接触关系的传递闭包分组。")
        SpatialAggregateDissolveGroupingMode groupingMode
) {
    public static final int MAX_SUMMARY_STATISTICS = 32;

    public SpatialAggregateDissolveOptions {
        summaryStatistics = summaryStatistics == null ? null : List.copyOf(summaryStatistics);
    }

    public SpatialAggregateDissolveOptions(
            boolean enabled,
            boolean multipart,
            String countOutputColumnName,
            List<SpatialAggregateStatistic> summaryStatistics
    ) {
        this(enabled, multipart, countOutputColumnName, summaryStatistics, null);
    }

    public SpatialAggregateDissolveGroupingMode effectiveGroupingMode() {
        return groupingMode == null
                ? SpatialAggregateDissolveGroupingMode.ALL_OR_FIELDS
                : groupingMode;
    }
}
