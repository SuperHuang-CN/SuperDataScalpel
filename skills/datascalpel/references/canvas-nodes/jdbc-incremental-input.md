# JDBC_INCREMENTAL_INPUT · JDBC 时间游标增量输入

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

仅用于 STREAMING 的 JDBC 时间游标增量输入配置；每个微批读取普通物理表中 incrementalTimeColumn > fromTime 且 <= 源数据库当前时间减可见性延迟的完整窗口。当前支持 PostgreSQL、HighGo、MySQL、openGauss、Kingbase、达梦、Oracle 和 SQL Server，不捕获删除，不分页、限行或排序；输出为 UNBOUNDED，且不自动设置事件时间或 Watermark。

## 模式与连线

- 模式：STREAMING；类别：INPUT。
- 无入边，至少一条出边；不读取上游 Map。
- 输入有界性见下文。

## 关键配置与行为

- 当前支持 PostgreSQL、HighGo、MySQL、openGauss、人大金仓、达梦、Oracle 和 SQL Server；不用于视图、超级表或 Geometry，不捕获删除。部署环境仍需核对实时能力。
- 每个微批读取 (fromTime, 源库当前时间−可见性延迟] 的完整窗口；不添加分页、排序、限行。
- LATEST 从当前边界开始，EARLIEST 从最早边界开始，AT_TIME 才配置 startTime；默认 UTC、延迟 30 秒、触发 60 秒。业务需保证时间字段更新和可见性，游标不等于 CDC。
- 当前图必须且只能连接一个终端输出节点；它是整个实时图唯一的无界输入，不能并列另一个 Kafka/TMQ/增量输入。
- 本手册按当前 Operator 的数据库能力分支校对；若部署环境仍是旧版本或契约说明与实际能力冲突，先报告差异并获取完整契约，不强行提交不支持的配置。

配置定位（只列关键语义，完整字段读取实时契约）：

- `tableName`：来源普通物理表名；视图和 TDengine 超级表不支持，且整张表不能包含 Geometry 字段。一个微批会读取命中时间窗口的全部记录。
- `incrementalTimeColumn`：增量游标字段名；必须是非 NULL 的 TIMESTAMP 或 TIMESTAMP_NTZ。查询窗口为该字段 > fromTime 且 <= toTime；缺少索引只产生全表扫描风险警告。
- `startPosition`：没有可恢复 Checkpoint 时的首次游标位置；NULL 规范化为 LATEST。EARLIEST 从无下界开始，LATEST 从启动时的源数据库安全时间开始，AT_TIME 从 startTime 的开区间之后开始。
- `cursorTimeZone`：解释 TIMESTAMP_NTZ 游标值的 IANA 时区；NULL 或空白规范化为 UTC，非法 ZoneId 编译失败。TIMESTAMP 的绝对时间语义不因此改变。

## 逻辑表 Map 与字段

追加一张 UNBOUNDED 表，继承物理表字段；不自动设置事件时间或 Watermark。

## 最小配置示例

前提：UUID 为支持的 JDBC 来源；普通物理表含非空 TIMESTAMP 增量时间字段 updated_at，快照中的类型和可空性可信。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "dataSourceId": "11111111-1111-4111-8111-111111111111",
  "tableName": "public.orders",
  "outputTableName": "order_events",
  "incrementalTimeColumn": "updated_at",
  "startPosition": "LATEST",
  "cursorTimeZone": "UTC",
  "visibilityDelaySeconds": 30,
  "triggerIntervalSeconds": 60
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `JDBC_INCREMENTAL_TABLE_REQUIRED` | 换成当前支持的普通物理表。 |
| `INVALID_CURSOR_TIME_ZONE` | 提供真实时区 ID，检查时间字段含义。 |
| `JDBC_INCREMENTAL_GEOMETRY_UNSUPPORTED` | 报告来源结构限制，不静默删字段。 |
| `JDBC_INCREMENTAL_REQUIRES_SINGLE_OUTPUT` | 把批准的输出设计收敛为当前支持的一个终端输出；范围变化需重新确认。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
