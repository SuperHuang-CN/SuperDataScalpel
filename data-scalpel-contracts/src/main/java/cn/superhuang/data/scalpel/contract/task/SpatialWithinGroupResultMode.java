package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("范围内分类结果组织方式。LINKED_TABLES 输出区域总体主表和逐组关联表；LEGACY_FLAT 输出每个区域/窗口/组值一行的旧单表结构。mode 为 null 时按 LINKED_TABLES 处理；groupSummary 为 null 时整个 groupResult 不生效。")
public enum SpatialWithinGroupResultMode {
    LINKED_TABLES,
    LEGACY_FLAT
}
