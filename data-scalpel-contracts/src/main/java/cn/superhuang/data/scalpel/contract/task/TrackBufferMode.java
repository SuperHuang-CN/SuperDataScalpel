package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("轨迹活动区域的逐观测缓冲来源。NONE 保留 Polygon/MultiPolygon 原形且不允许 Point；FIELD 从数值字段读取半径；EXPRESSION 以受控数值表达式计算半径。")
public enum TrackBufferMode {
    NONE, FIELD, EXPRESSION
}
