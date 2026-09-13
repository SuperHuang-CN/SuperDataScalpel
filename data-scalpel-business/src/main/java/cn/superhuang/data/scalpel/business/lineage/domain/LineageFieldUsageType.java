package cn.superhuang.data.scalpel.business.lineage.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "来源字段在任务中的非输出用途：JOIN_KEY 连接键、FILTER_CONDITION 过滤条件、GROUP_KEY 分组键、SORT_KEY 排序键、PARTITION_KEY 分区键。")
public enum LineageFieldUsageType {
    JOIN_KEY,
    FILTER_CONDITION,
    GROUP_KEY,
    SORT_KEY,
    PARTITION_KEY
}
