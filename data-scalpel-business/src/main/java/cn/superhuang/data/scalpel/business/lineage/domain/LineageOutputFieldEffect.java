package cn.superhuang.data.scalpel.business.lineage.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "任务对输出字段的结果：DERIVED 有明确来源派生，WRITTEN_UNKNOWN_SOURCE 已写入但来源未知，CONSTANT 常量，DEFAULT_VALUE 默认值，NULL_FILLED 补空值，PRESERVED 保留原值，NOT_WRITTEN 未写入。")
public enum LineageOutputFieldEffect {
    DERIVED,
    WRITTEN_UNKNOWN_SOURCE,
    CONSTANT,
    DEFAULT_VALUE,
    NULL_FILLED,
    PRESERVED,
    NOT_WRITTEN
}
