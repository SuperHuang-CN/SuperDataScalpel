# JOIN · 批处理关联

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

批处理等值 Join 配置；连接两张不同的上游逻辑表，使用一个或多个 AND 条件匹配，再按 outputColumns 显式选择、重命名和排列结果字段。相同 Key 在任一侧出现多行时会产生所有匹配组合，不做隐式去重。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- INNER/LEFT/RIGHT/FULL；只支持普通字段等值 AND 条件，不支持任意 SQL 条件或 Geometry 比较。
- outputColumns 显式选择 included 字段、别名和顺序；左右多行同键会乘法扩张，不隐式去重。

配置定位（只列关键语义，完整字段读取实时契约）：

- `joinType`：必填 Join 类型：INNER 只保留匹配记录，LEFT/RIGHT 保留对应一侧全部记录，FULL 保留两侧全部记录；外连接未匹配侧的输出字段为 NULL。
- `conditions`：至少一个左右字段等值条件，数组中的条件全部使用 AND 组合。使用 Spark SQL 普通等号语义，因此任一侧为 NULL 都不匹配，包括 NULL 与 NULL。重复的同一字段对无效。
- `outputColumns`：结果字段投影；至少一项 included=true。启用项按数组顺序输出，可选择左右同名字段并分别改名；最终输出名按大小写不敏感规则唯一，同一侧同一来源字段最多配置一次。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 输出仅含投影字段；外连接未匹配侧字段变为可空。

## 最小配置示例

前提：Map 中有 BOUNDED orders(id:LONG,customer_id:LONG) 和 customers(id:LONG,name:STRING)。

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
| `INVALID_JOIN_TABLE` | 指定两张不同且真实存在的逻辑表。 |
| `DUPLICATE_JOIN_CONDITION` | 消除重复条件并检查类型兼容。 |
| `GEOMETRY_FIELD_OPERATION_UNSUPPORTED` | 空间关系使用空间关联节点。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
