package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/**
 * One Spark JDBC read option configured for a selected physical table.
 *
 * <p>Values deliberately remain strings because Spark JDBC options and JDBC driver properties
 * share the same string-based option boundary.</p>
 */
@JsonClassDescription("一项逐表 Spark JDBC 读取参数。它在受保护连接和数据源 JDBC Properties 之后应用，但平台最后仍会覆盖 dbtable；Compiler 只校验配置，不连接数据库或执行 sessionInitStatement。")
public record JdbcInputReadOption(
        @JsonPropertyDescription("参数名，长度 1..128，匹配 [A-Za-z][A-Za-z0-9._-]{0,127}，在同一表内忽略大小写唯一。连接、凭据、目标表、写入参数和 partitionColumn/lowerBound/upperBound/numPartitions 等受保护名称禁止使用；未知非敏感名称交给 Spark 或驱动解释。")
        String name,
        @JsonPropertyDescription("参数字符串值，最长 4096 个字符且允许多行。fetchsize/queryTimeout 必须为非负整数；pushDownPredicate、pushDownAggregate、pushDownLimit、pushDownOffset、pushDownTableSample、pushDownJoin、preferTimestampNTZ 必须为 true 或 false；sessionInitStatement 不能空白。")
        String value
) {
}
