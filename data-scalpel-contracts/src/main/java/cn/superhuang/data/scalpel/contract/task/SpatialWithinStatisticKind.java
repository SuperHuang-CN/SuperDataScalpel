package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("范围内统计函数。COUNT 统计相交要素行且不使用字段；COUNT_FIELD 统计所选字段非 null 值；ANY 返回任意一个非空字符串且选择顺序不稳定；SUM、MEAN、MIN、MAX、RANGE、STDDEV、VARIANCE 使用 Spark 聚合，其中 STDDEV/VARIANCE 为样本统计；LENGTH_WITHIN、AREA_WITHIN 不使用字段，分别对区域内 Intersection 片段求长度或面积并换算到配置单位。空区域两类计数为 0，其余无有效观测时通常为 null。")
public enum SpatialWithinStatisticKind {
    COUNT,
    COUNT_FIELD,
    ANY,
    SUM,
    MEAN,
    MIN,
    MAX,
    RANGE,
    STDDEV,
    VARIANCE,
    LENGTH_WITHIN,
    AREA_WITHIN
}
