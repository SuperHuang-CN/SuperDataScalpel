package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("多变量格网变量类型：到最近要素的距离、最近要素属性，或与格网/中心搜索范围关联的要素属性汇总。")
public enum SpatialMultiVariableGridVariableKind {
    DISTANCE_TO_NEAREST,
    ATTRIBUTE_OF_NEAREST,
    ATTRIBUTE_SUMMARY_OF_RELATED
}
