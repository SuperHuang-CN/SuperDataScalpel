package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("用户 JAR SDK 指标类型：COUNTER 非负累计整数；GAUGE 有限当前数值；TIMER 次数、最近耗时、累计耗时和最大耗时。")
public enum UserJobMetricKind {
    COUNTER,
    GAUGE,
    TIMER
}
