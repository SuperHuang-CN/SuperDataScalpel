package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("一对一空间连接保留记录策略。FIRST 完全按 stableOrder 取第一项；LARGEST/SMALLEST 对数值 orderByColumnName 排序；NEWEST/OLDEST 对日期或时间字段排序；后四种再用 stableOrder 消除主值并列。")
public enum SpatialJoinKeepStrategy {
    FIRST,
    LARGEST,
    SMALLEST,
    NEWEST,
    OLDEST
}
