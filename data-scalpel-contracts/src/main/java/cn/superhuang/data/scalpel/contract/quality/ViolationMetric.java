package cn.superhuang.data.scalpel.contract.quality;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("行级规则的违规容忍单位：COUNT 按违规记录数比较非负整数阈值；PERCENT 按违规记录数占目标模型本次全量检查行数的百分比比较 0 到 100 的阈值。")
public enum ViolationMetric {
    COUNT,
    PERCENT
}
