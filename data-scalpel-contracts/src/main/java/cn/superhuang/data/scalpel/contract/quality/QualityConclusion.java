package cn.superhuang.data.scalpel.contract.quality;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("一次成功完成的质检的数据质量结论：PASSED 没有已执行规则超过阈值，FAILED 至少一条已执行规则超过阈值；跳过规则本身不导致 FAILED，二者均对应 TaskRun 技术状态 SUCCESS。")
public enum QualityConclusion {
    PASSED,
    FAILED
}
