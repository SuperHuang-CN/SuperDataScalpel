package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("多变量格网关联要素统计：COUNT 不读取字段；ANY 读取任一字符串；其余统计读取数值字段并输出 DOUBLE。STDDEV 与 VARIANCE 使用样本统计。")
public enum SpatialMultiVariableGridStatisticKind {
    COUNT,
    SUM,
    MEAN,
    MIN,
    MAX,
    RANGE,
    STDDEV,
    VARIANCE,
    ANY
}
