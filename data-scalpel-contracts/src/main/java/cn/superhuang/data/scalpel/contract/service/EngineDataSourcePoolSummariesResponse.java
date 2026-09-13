package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.UUID;

/** Read-only pool summaries; contains neither connection configuration nor SQL text. */
@JsonClassDescription("Service Engine 当前已登记数据源的连接池状态摘要；不包含连接配置、凭据或 SQL 正文。")
public record EngineDataSourcePoolSummariesResponse(
        @JsonPropertyDescription("目标 Service Engine 的稳定唯一编码。")
        String engineCode,
        @JsonPropertyDescription("服务引擎采集这些连接池摘要的 ISO-8601 UTC 时间字符串。")
        String capturedAt,
        @JsonPropertyDescription("该引擎中各已注册数据源的连接池摘要。")
        List<Entry> dataSources
) {
    @JsonClassDescription("一个已登记数据源的连接池加载状态和可选容量摘要。")
    public record Entry(
            @JsonPropertyDescription("数据源 UUID。")
            UUID dataSourceId,
            @JsonPropertyDescription("运行时连接池状态：AVAILABLE 已加载，NOT_LOADED 尚未加载，UNSUPPORTED 该数据源不提供 JDBC 池。")
            JdbcPoolMonitorStatus status,
            @JsonPropertyDescription("当前数据源连接池容量与占用摘要。")
            JdbcPoolSummary pool
    ) {
    }
}
