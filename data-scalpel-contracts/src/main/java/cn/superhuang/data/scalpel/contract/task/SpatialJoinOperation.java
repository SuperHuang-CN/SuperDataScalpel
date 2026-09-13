package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间连接结果粒度。JOIN_ONE_TO_MANY 输出每个匹配的左右记录组合；Canvas 4.58 起 JOIN_ONE_TO_ONE 按显式汇总或确定性保留规则为每个目标要素最多输出一条结果。")
public enum SpatialJoinOperation {
    JOIN_ONE_TO_MANY,
    JOIN_ONE_TO_ONE
}
