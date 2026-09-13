package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.UUID;

/** Read-only, bounded in-memory diagnostics. SQL previews are normalized by API Studio. */
@JsonClassDescription("Service Engine 中一个数据源连接池的只读有界诊断快照；SQL 仅返回去字面量后的安全预览。")
public record EngineDataSourcePoolMonitorResponse(
        @JsonPropertyDescription("目标 Service Engine 的稳定唯一编码。")
        String engineCode,
        @JsonPropertyDescription("数据源 UUID。")
        UUID dataSourceId,
        @JsonPropertyDescription("运行时连接池状态：AVAILABLE 已加载，NOT_LOADED 尚未加载，UNSUPPORTED 该数据源不提供 JDBC 池。")
        JdbcPoolMonitorStatus status,
        @JsonPropertyDescription("服务引擎采集该连接池快照的 ISO-8601 UTC 时间字符串。")
        String capturedAt,
        @JsonPropertyDescription("当前数据源连接池容量与占用摘要。")
        JdbcPoolSummary pool,
        @JsonPropertyDescription("按持有时长聚合的主要 API/操作连接消费者。")
        List<SqlConsumer> topConsumers,
        @JsonPropertyDescription("当前借出连接的有界诊断列表。")
        List<ActiveConnection> activeConnections,
        @JsonPropertyDescription("按最近执行时间返回的脱敏 SQL 统计。")
        List<RecentSql> recentSql,
        @JsonPropertyDescription("当前连接池饱和、长查询或长持有事件摘要。")
        List<SaturationIncident> incidents
) {
    @JsonClassDescription("按规范化 SQL 和操作类型聚合的当前连接持有方。")
    public record SqlConsumer(
            @JsonPropertyDescription("移除 SQL 字面量后的规范化指纹，用于聚合同类语句。")
            String fingerprint,
            @JsonPropertyDescription("移除字面量并裁剪后的安全 SQL 预览。")
            String sqlPreview,
            @JsonPropertyDescription("持有连接的受控操作类型，例如 QUERY、UPDATE 或 TRANSACTION。")
            String operation,
            @JsonPropertyDescription("该消费者当前持有的连接数。")
            Integer connectionCount,
            @JsonPropertyDescription("该消费者当前连接累计持有时长，单位毫秒。")
            Long totalHeldMs,
            @JsonPropertyDescription("该消费者单个当前连接的最长持有时长，单位毫秒。")
            Long maxHeldMs,
            @JsonPropertyDescription("该消费者当前 SQL 的最长执行时长，单位毫秒。")
            Long maxExecutingMs
    ) {
    }

    @JsonClassDescription("当前借出连接的有界诊断条目，用于定位长查询、长事务和连接泄漏迹象。")
    public record ActiveConnection(
            @JsonPropertyDescription("连接池内部的安全连接标识。")
            String connectionId,
            @JsonPropertyDescription("连接池报告的当前连接状态，例如借出、执行 SQL 或事务中。")
            String state,
            @JsonPropertyDescription("连接最近从池中借出的 ISO-8601 UTC 时间字符串。")
            String borrowedAt,
            @JsonPropertyDescription("连接当前连续持有时长，单位毫秒。")
            Long heldMs,
            @JsonPropertyDescription("持有连接的引擎工作线程名称。")
            String threadName,
            @JsonPropertyDescription("连接当前是否处于活动事务中。")
            boolean transactionActive,
            @JsonPropertyDescription("当前 SQL 移除字面量后的规范化指纹；没有活动 SQL 时为空。")
            String fingerprint,
            @JsonPropertyDescription("移除字面量并裁剪后的安全 SQL 预览。")
            String sqlPreview,
            @JsonPropertyDescription("当前持有连接的受控操作类型；无法关联时为空。")
            String operation,
            @JsonPropertyDescription("当前 SQL 开始执行的 ISO-8601 UTC 时间字符串；没有活动 SQL 时为空。")
            String sqlStartedAt,
            @JsonPropertyDescription("当前 SQL 已执行时长，单位毫秒。")
            Long executingMs,
            @JsonPropertyDescription("该连接最近一条脱敏 SQL 预览；没有时为空。")
            String lastSqlPreview,
            @JsonPropertyDescription("持有连接的数据服务或引擎 API 标识；无法关联时为空。")
            String apiId,
            @JsonPropertyDescription("持有连接的服务路由；无法关联时为空。")
            String apiPath,
            @JsonPropertyDescription("关联任务的外部执行 UUID 字符串；非任务调用或无法关联时为空。")
            String executionId,
            @JsonPropertyDescription("当前 SQL 是否超过长查询阈值。")
            boolean longRunning,
            @JsonPropertyDescription("当前连接是否超过长时间持有阈值。")
            boolean longHeld
    ) {
    }

    /** Aggregates over the retained recent execution window, not lifetime counters. */
    @JsonClassDescription("保留统计窗口内一类规范化 SQL 的执行次数、失败数和耗时聚合。")
    public record RecentSql(
            @JsonPropertyDescription("移除 SQL 字面量后的规范化指纹，用于聚合同类语句。")
            String fingerprint,
            @JsonPropertyDescription("移除字面量并裁剪后的安全 SQL 预览。")
            String sqlPreview,
            @JsonPropertyDescription("该 SQL 对应的受控操作类型。")
            String operation,
            @JsonPropertyDescription("该脱敏 SQL 在统计窗口内的执行次数。")
            Integer executions,
            @JsonPropertyDescription("该脱敏 SQL 在统计窗口内的失败次数。")
            Integer failures,
            @JsonPropertyDescription("统计窗口内累计执行时长，单位毫秒。")
            Long totalDurationMs,
            @JsonPropertyDescription("统计窗口内单次最大执行时长，单位毫秒。")
            Long maxDurationMs,
            @JsonPropertyDescription("该规范化 SQL 最近一次执行的 ISO-8601 UTC 时间字符串。")
            String lastExecutedAt
    ) {
    }

    @JsonClassDescription("连接池近期发生的一次饱和、连接获取超时或容量相关事件。")
    public record SaturationIncident(
            @JsonPropertyDescription("连接池饱和或超时事件发生的 ISO-8601 UTC 时间字符串。")
            String occurredAt,
            @JsonPropertyDescription("连接池饱和、连接获取超时或其他容量事件的具体原因。")
            String reason,
            @JsonPropertyDescription("触发连接池事件时请求的脱敏 SQL 预览。")
            String requestedSqlPreview,
            @JsonPropertyDescription("触发事件的安全执行标识；无法关联时为空。")
            String requestedExecutionId,
            @JsonPropertyDescription("当前数据源连接池容量与占用摘要。")
            JdbcPoolSummary pool,
            @JsonPropertyDescription("按持有时长聚合的主要 API/操作连接消费者。")
            List<SqlConsumer> topConsumers
    ) {
    }
}
