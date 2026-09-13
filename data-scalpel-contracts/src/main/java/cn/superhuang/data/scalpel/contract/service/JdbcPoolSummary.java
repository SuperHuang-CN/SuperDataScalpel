package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** In-memory JDBC pool sample. Null metrics mean unavailable, never zero. */
@JsonClassDescription("一个数据服务版本的进程内 JDBC 连接池观测快照；空指标表示当前无法获取，不等同于零。")
public record JdbcPoolSummary(
        @JsonPropertyDescription("引擎内该数据源连接池的稳定名称。")
        String poolName,
        @JsonPropertyDescription("连接池允许的最大连接数。")
        Integer maximum,
        @JsonPropertyDescription("当前已创建的连接总数。")
        Integer total,
        @JsonPropertyDescription("当前已借出并被业务占用的连接数。")
        Integer active,
        @JsonPropertyDescription("当前池中空闲连接数。")
        Integer idle,
        @JsonPropertyDescription("当前等待获取连接的请求数。")
        Integer waiting,
        @JsonPropertyDescription("连接池当前利用率百分比，范围 0 到 100。")
        Double utilizationPercent,
        @JsonPropertyDescription("累计获取连接超时次数。")
        Long acquisitionTimeoutCount,
        @JsonPropertyDescription("最近一次连接池饱和时间的 ISO-8601 UTC 字符串；尚未饱和过时为空。")
        String lastSaturationAt,
        @JsonPropertyDescription("当前正在执行 SQL 的借出连接数。")
        Integer executingConnections,
        @JsonPropertyDescription("当前处于事务中但未执行 SQL 的连接数。")
        Integer idleInTransactionConnections,
        @JsonPropertyDescription("已借出但既无活动 SQL 也无活动事务的连接数。")
        Integer borrowedIdleConnections,
        @JsonPropertyDescription("超过引擎长查询阈值的当前 SQL 数量。")
        Integer longRunningQueryCount,
        @JsonPropertyDescription("超过引擎长时间持有阈值的当前借出连接数。")
        Integer longHeldConnectionCount
) {
}
