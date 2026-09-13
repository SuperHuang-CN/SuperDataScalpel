package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("网络线方向值映射；缺失整个对象时所有线按双向通行处理。值本身不进入 Canvas 卡片或安全摘要。")
public record SnapTracksDirectionMatching(
        @JsonPropertyDescription("网络线中表示通行方向的非 Geometry 字段名。")
        String directionColumnName,
        @JsonPropertyDescription("允许从 fromNode 向 toNode 通行的字段值。")
        String forwardValue,
        @JsonPropertyDescription("允许从 toNode 向 fromNode 通行的字段值。")
        String backwardValue,
        @JsonPropertyDescription("允许双向通行的字段值。")
        String bothValue,
        @JsonPropertyDescription("两种方向均不允许通行的字段值；允许使用空字符串。")
        String noneValue
) {
}
