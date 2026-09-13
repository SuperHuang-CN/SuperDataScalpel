package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** A single read-only Spark SQL query over the current Canvas table map. */
@JsonClassDescription("受控 Spark SQL 转换配置；在隔离的子 Session 中把当前 Canvas 表注册为临时视图，解析一条只读 SELECT 或 WITH…SELECT，并把惰性结果计划作为新的有界逻辑表返回。不能访问 Catalog 表、外部关系或表值函数。")
public record SqlTransformConfiguration(
        @JsonPropertyDescription("当前操作产生的 Canvas 逻辑表名；必须在任务定义内唯一，后续节点通过该值引用结果。")
        String outputTableName,
        @JsonPropertyDescription("1～100000 字符的单条 Spark SQL SELECT 或 WITH…SELECT。关系只能是当前上游逻辑表或本查询 CTE；禁止命令、写入、多语句、Catalog 限定关系和表值函数。含点号等特殊字符的逻辑表名须按 Spark 标识符规则用反引号引用。查询必须至少产生一个字段，且字段名不可重复、类型必须能映射为平台类型。")
        String sql
) {
    public SqlTransformConfiguration {
        outputTableName = outputTableName == null ? "" : outputTableName;
        sql = sql == null ? "" : sql;
    }
}
