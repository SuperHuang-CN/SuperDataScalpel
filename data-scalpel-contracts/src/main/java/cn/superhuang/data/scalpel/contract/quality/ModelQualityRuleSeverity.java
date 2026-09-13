package cn.superhuang.data.scalpel.contract.quality;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("质量规则业务严重程度：CRITICAL 严重、MAJOR 主要、MINOR 次要。它只用于规则分类和执行顺序，不改变质量结论算法，也不会把数据违规转换为任务技术失败。")
public enum ModelQualityRuleSeverity {
    CRITICAL,
    MAJOR,
    MINOR
}
