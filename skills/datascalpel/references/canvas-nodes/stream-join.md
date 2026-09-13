# STREAM_JOIN · 流与静态表关联

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

流式 Stream-Static Join 配置；使用 AND 组合的普通等值条件，把左侧 UNBOUNDED 流与右侧 BOUNDED 静态表连接，再显式投影结果字段。静态表只在流任务启动时读取，不会自动刷新；当前不支持两个无界流连接。

## 模式与连线

- 模式：STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- 仅 INNER/LEFT，左流右静态；不能交换后仍期待同一语义，不支持双流 JOIN。
- 静态表启动时加载，不持续刷新；业务要求动态维表时报告能力限制。显式投影字段与等值条件同 JOIN。

配置定位（只列关键语义，完整字段读取实时契约）：

- `joinType`：必填 Stream Join 类型：INNER 只输出匹配的流记录，LEFT 还保留未匹配的左侧流记录并把右侧输出字段设为 NULL；不支持 RIGHT 或 FULL。
- `conditions`：至少一个左右字段等值条件，全部使用 AND 组合。使用 Spark SQL 普通等号，因此任一侧为 NULL 都不匹配；不支持范围、OR、时间区间或 Stream-Stream 状态条件。
- `outputColumns`：结果字段投影；至少一项 included=true，启用项按数组顺序输出。最终名称按大小写不敏感规则唯一，同一侧同一来源字段最多配置一次；右侧静态表的事件时间信息不会传播。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 新表 UNBOUNDED；事件时间传播取决于左侧字段是否在结果中保留，核对 outputTables。

## 最小配置示例

前提：orders 是 UNBOUNDED 流，customers 是 BOUNDED 静态表，字段与批 Join 示例一致。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "leftTableName": "orders",
  "rightTableName": "customers",
  "outputTableName": "enriched",
  "joinType": "LEFT",
  "conditions": [
    {
      "leftColumnName": "customer_id",
      "operator": "EQUALS",
      "rightColumnName": "id"
    }
  ],
  "outputColumns": [
    {
      "sourceSide": "LEFT",
      "sourceColumnName": "id",
      "outputColumnName": "order_id",
      "included": true
    },
    {
      "sourceSide": "RIGHT",
      "sourceColumnName": "name",
      "outputColumnName": "customer_name",
      "included": true
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `STREAM_JOIN_LEFT_MUST_BE_UNBOUNDED` | 更正输入方向或来源，不能只篡改快照有界性。 |
| `STREAM_JOIN_RIGHT_MUST_BE_BOUNDED` | 改用有界静态来源；双流需求需重新设计。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
