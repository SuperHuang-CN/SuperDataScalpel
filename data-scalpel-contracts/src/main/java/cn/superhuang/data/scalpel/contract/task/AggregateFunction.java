package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("分组聚合函数：COUNT 统计全部行或来源字段的非 NULL 值数量；SUM 求和；AVG 求平均值；MIN/MAX 取最小值或最大值。COUNT、SUM、AVG 可对来源字段先去重，MIN/MAX 不接受 distinct=true；具体字段类型能否执行由 Spark Analyzer 判断。")
public enum AggregateFunction {
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX
}
