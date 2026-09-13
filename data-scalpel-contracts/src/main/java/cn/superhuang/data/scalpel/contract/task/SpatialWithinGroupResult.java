package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("范围内分类统计的关联结果配置。仅在 groupSummary 存在且 mode 为 LINKED_TABLES 或 null 时生效：节点输出一张区域总体主表和一张区域键+可选窗口+组值粒度的关联组表。组表不复制区域 Geometry；真实 null 组值可以保留，空区域不会生成虚假 null 组。LEGACY_FLAT 时整对象只作为未激活草稿保存。")
public record SpatialWithinGroupResult(
        @JsonPropertyDescription("AREA_TABLE 模式下稳定标识区域行的来源标量字段名，真实执行时必须对参与区域非 null 且唯一；主表与组表通过该键及可选窗口关联。PLANAR_GRID 模式自动改用生成的稳定格网 ID。")
        String areaKeyColumnName,
        @JsonPropertyDescription("区域键在主表和关联组表中的输出字段名，类型继承区域键；必须与各自表中的其他输出字段按大小写不敏感规则保持唯一。")
        String areaKeyOutputColumnName,
        @JsonPropertyDescription("关联组表逻辑表名，必须与所有输入表和主结果 outputTableName 不同；该表每个区域键、可选时间窗和真实组值输出一行。")
        String outputTableName,
        @JsonPropertyDescription("groupSummary.groupByColumnName 的组值在关联组表中的输出字段名；它是输出别名，不是另一个输入字段。真实 null 组值仍可作为一组。")
        String groupValueColumnName,
        @JsonPropertyDescription("groupSummary.includeMinorityMajority=true 时主表中的少数组值字段名；按正形状量选择，形状量并列时按组值升序选一个且 null 排最后，无正形状量时为 null。")
        String minorityValueColumnName,
        @JsonPropertyDescription("groupSummary.includeMinorityMajority=true 时主表中的多数组值字段名；按正形状量选择，形状量并列时按组值升序选一个且 null 排最后，无正形状量时为 null。")
        String majorityValueColumnName,
        @JsonPropertyDescription("同时启用少数/多数与组百分比时，主表中少数组的形状量百分比字段名；否则忽略。比例为该组相交点数/长度/面积占所有组形状量之和的 0 至 100 值。")
        String minorityPercentageColumnName,
        @JsonPropertyDescription("同时启用少数/多数与组百分比时，主表中多数组的形状量百分比字段名；否则忽略。无正形状总量时为 null。")
        String majorityPercentageColumnName,
        @JsonPropertyDescription("结果组织方式。LINKED_TABLES 或 null 在启用 groupSummary 时输出总体主表和关联组表；LEGACY_FLAT 只输出按区域、窗口、组值平铺的一张主结果表，并忽略本对象的其他字段。")
        SpatialWithinGroupResultMode mode
) {
    public SpatialWithinGroupResult(String areaKeyColumnName, String areaKeyOutputColumnName,
            String outputTableName, String groupValueColumnName, String minorityValueColumnName,
            String majorityValueColumnName, String minorityPercentageColumnName, String majorityPercentageColumnName) {
        this(areaKeyColumnName, areaKeyOutputColumnName, outputTableName, groupValueColumnName,
                minorityValueColumnName, majorityValueColumnName, minorityPercentageColumnName, majorityPercentageColumnName, null);
    }
}
