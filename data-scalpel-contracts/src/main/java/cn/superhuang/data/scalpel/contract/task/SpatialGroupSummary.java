package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("空间格网或区域汇总的分类分组配置。存在时按来源字段增加组值粒度。普通格网和 Within 旧扁平模式按命中行数计算少数/多数及百分比；Within 关联表模式按相交点数、长度或面积计算组比例，并将总体主表与逐组表分开。")
public record SpatialGroupSummary(
        @JsonPropertyDescription("必填且不能为 Geometry 的来源分组字段名；结果使用同名字段输出当前组值，因此该名称也不能与其他结果字段重名。")
        String groupByColumnName,
        @JsonPropertyDescription("是否输出少数/多数信息。普通格网对所有并列最少/最多且计数大于 0 的组标记 true；Within 的 LEGACY_FLAT 模式仅在最少或最多计数没有并列时标记 true，并列全部为 false；Within LINKED_TABLES 模式改为在主表输出按正形状量排序选出的组值。")
        boolean includeMinorityMajority,
        @JsonPropertyDescription("是否输出组百分比，范围 0 至 100。普通格网和 Within LEGACY_FLAT 使用组命中行数/总命中行数；Within LINKED_TABLES 使用组相交点数、长度或面积/全部组形状量。分母为 0 时为 null。")
        boolean includeGroupPercentage,
        @JsonPropertyDescription("includeMinorityMajority=true 且结果为普通格网或 Within LEGACY_FLAT 时必填的少数组 BOOLEAN 字段名；Within LINKED_TABLES 改用 groupResult.minorityValueColumnName，本字段不输出。")
        String minorityFlagColumnName,
        @JsonPropertyDescription("includeMinorityMajority=true 且结果为普通格网或 Within LEGACY_FLAT 时必填的多数组 BOOLEAN 字段名；Within LINKED_TABLES 改用 groupResult.majorityValueColumnName，本字段不输出。")
        String majorityFlagColumnName,
        @JsonPropertyDescription("includeGroupPercentage=true 时必填的 nullable DOUBLE 百分比字段名。普通格网和 Within LEGACY_FLAT 写入主结果；Within LINKED_TABLES 写入关联组表。关闭时忽略并保留草稿值。")
        String groupPercentageColumnName
) {
}
