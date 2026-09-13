package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("实时 Canvas 输入来源：JDBC_INCREMENTAL 按时间游标轮询 JDBC 表；TDENGINE_TMQ 从 TDengine TMQ 主题消费 VGroup。")
public enum StreamingSourceKind {
    JDBC_INCREMENTAL,
    TDENGINE_TMQ
}
