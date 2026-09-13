package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("查找相似位置返回最相似、最不相似或两端候选。")
public enum SpatialSimilarLocationsResultMode {
    MOST_SIMILAR,
    LEAST_SIMILAR,
    BOTH
}
