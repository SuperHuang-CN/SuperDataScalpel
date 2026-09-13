package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("查找相似位置的属性匹配方法；均使用参考与候选全集进行标准化。")
public enum SpatialSimilarLocationsMatchMethod {
    ATTRIBUTE_VALUES,
    ATTRIBUTE_PROFILES
}
