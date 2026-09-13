package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.Instant;

@JsonClassDescription("仅用于 STREAMING 的 JDBC 时间游标增量输入配置；每个微批读取普通物理表中 incrementalTimeColumn > fromTime 且 <= 源数据库当前时间减可见性延迟的完整窗口。只支持 PostgreSQL、MySQL、openGauss 和 Kingbase，不捕获删除，不分页、限行或排序；输出为 UNBOUNDED，且不自动设置事件时间或 Watermark。")
public record JdbcIncrementalInputConfiguration(
        @JsonPropertyDescription("来源 JDBC 数据源 UUID 字符串；草稿可为空，编译时必须指向已启用、具有 SOURCE 用途的 PostgreSQL、MySQL、openGauss 或 Kingbase 数据源。")
        String dataSourceId,
        @JsonPropertyDescription("来源普通物理表名；视图和 TDengine 超级表不支持，且整张表不能包含 Geometry 字段。一个微批会读取命中时间窗口的全部记录。")
        String tableName,
        @JsonPropertyDescription("当前操作产生的 Canvas 逻辑表名；必须在任务定义内唯一，后续节点通过该值引用结果。")
        String outputTableName,
        @JsonPropertyDescription("增量游标字段名；必须是非 NULL 的 TIMESTAMP 或 TIMESTAMP_NTZ。查询窗口为该字段 > fromTime 且 <= toTime；缺少索引只产生全表扫描风险警告。")
        String incrementalTimeColumn,
        @JsonPropertyDescription("没有可恢复 Checkpoint 时的首次游标位置；NULL 规范化为 LATEST。EARLIEST 从无下界开始，LATEST 从启动时的源数据库安全时间开始，AT_TIME 从 startTime 的开区间之后开始。")
        JdbcIncrementalStartPosition startPosition,
        @JsonPropertyDescription("AT_TIME 必填的 UTC 时间点，作为首次查询的排他下界；其他启动策略会把该值规范化为 NULL。可恢复 Checkpoint 存在时以 Checkpoint 为准。")
        Instant startTime,
        @JsonPropertyDescription("解释 TIMESTAMP_NTZ 游标值的 IANA 时区；NULL 或空白规范化为 UTC，非法 ZoneId 编译失败。TIMESTAMP 的绝对时间语义不因此改变。")
        String cursorTimeZone,
        @JsonPropertyDescription("可见性延迟秒数，闭区间 0 到 3600，NULL 默认 30；每批上界 toTime=源数据库当前时间-该值，用于避开尚未稳定可见的近期记录。")
        Integer visibilityDelaySeconds,
        @JsonPropertyDescription("Spark Structured Streaming 微批触发间隔秒数，闭区间 1 到 300，NULL 默认 60；不限制单批读取行数。")
        Integer triggerIntervalSeconds
) {
    public static final String DEFAULT_CURSOR_TIME_ZONE = "UTC";
    public static final int DEFAULT_VISIBILITY_DELAY_SECONDS = 30;
    public static final int DEFAULT_TRIGGER_INTERVAL_SECONDS = 60;

    public JdbcIncrementalInputConfiguration {
        startPosition = startPosition == null ? JdbcIncrementalStartPosition.LATEST : startPosition;
        startTime = startPosition == JdbcIncrementalStartPosition.AT_TIME ? startTime : null;
        cursorTimeZone = cursorTimeZone == null || cursorTimeZone.isBlank()
                ? DEFAULT_CURSOR_TIME_ZONE
                : cursorTimeZone.trim();
        visibilityDelaySeconds = visibilityDelaySeconds == null
                ? DEFAULT_VISIBILITY_DELAY_SECONDS
                : visibilityDelaySeconds;
        triggerIntervalSeconds = triggerIntervalSeconds == null
                ? DEFAULT_TRIGGER_INTERVAL_SECONDS
                : triggerIntervalSeconds;
    }
}
