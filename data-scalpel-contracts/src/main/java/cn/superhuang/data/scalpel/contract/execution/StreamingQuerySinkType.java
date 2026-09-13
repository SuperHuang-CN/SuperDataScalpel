package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Runner 上报的受管流式查询输出类型：KAFKA 主题；JDBC 数据库；CUSTOM 用户流式 JAR 自定义输出。")
public enum StreamingQuerySinkType {
    KAFKA,
    JDBC,
    CUSTOM
}
