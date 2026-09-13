# JDBC_INPUT · JDBC 表输入

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

JDBC 表输入配置；从一个已启用且具有 SOURCE 用途的数据源全量读取一张或多张物理表或视图，每个选择产生一张以原始 tableName 命名的 BOUNDED 逻辑表。流任务中也只在启动时读取为静态有界表。

## 模式与连线

- 模式：BATCH / STREAMING；类别：INPUT。
- 无入边，至少一条出边；不读取上游 Map。
- 输入有界性见下文。

## 关键配置与行为

- 支持物理表或视图，readOptions 仅允许当前契约白名单；分区读取选项不是业务过滤或通用增量游标。
- 实时中也是启动时的静态表，不持续轮询、不随库中变化自动刷新。

配置定位（只列关键语义，完整字段读取实时契约）：

- `tables`：要读取的物理表或视图；至少一项，tableName 在本数组中精确匹配后不能重复，且都必须属于 dataSourceId 固定的数据库和 Schema。每项独立应用 readOptions。

## 逻辑表 Map 与字段

每个 tables 选择以原 tableName 为逻辑表名输出 BOUNDED 表；下游必须使用完整名称而非显示别名。

## 最小配置示例

前提：UUID 为已启用、SOURCE 用途的 JDBC 数据源；tableName 必须采用该源元数据接口返回的完整表标识，快照含 columns/uniqueKeys。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "dataSourceId": "11111111-1111-4111-8111-111111111111",
  "tables": [
    {
      "tableName": "public.orders",
      "readOptions": []
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `DATA_SOURCE_UNAVAILABLE` | 核对连接类型、SOURCE 用途及启用状态。 |
| `TABLE_NOT_FOUND` | 补齐准确表标识的元数据，勿以空 Schema 继续。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
