# UNION · 纵向合并

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

按字段名纵向合并逻辑表的配置；至少选择两张字段名集合完全一致的表，按配置顺序执行 unionByName。第一张表决定输出字段顺序，不支持按位置合并、缺失字段补 NULL、忽略额外字段或自动改名。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- ALL 保留全部行；DISTINCT 仅允许 BOUNDED 输入，无界表不能去重。限制依据所选表的有界性，不仅依据任务名称。
- 至少两张表，按名称对齐，字段名称集合必须一致；第一张表决定结果字段顺序，不自动补缺失列。所有输入有界性相同；无界表还必须有相同事件时间字段与 Watermark。

配置定位（只列关键语义，完整字段读取实时契约）：

- `inputTableNames`：按顺序参与 Union 的不同上游逻辑表名；至少两项且不能重复。第一项决定输出字段顺序，其余表按字段名重排后合并。
- `mode`：必填合并模式。ALL 保留所有输入行及重复行；DISTINCT 对完整合并结果按全部字段去重。包含 Geometry 字段时不能使用 DISTINCT，无界输入也不能使用 DISTINCT。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 结果字段顺序取第一张表，兼容类型由分析确认；不能混合 BOUNDED 与 UNBOUNDED。同一无界输入的分支可合并，但不允许为此新增第二个无界输入节点。

## 最小配置示例

前提：两张上游表字段名称集合与对应类型兼容；批模式均 BOUNDED，流模式须满足 Engine 的有界性组合限制。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "inputTableNames": [
    "orders",
    "archive_orders"
  ],
  "outputTableName": "all_orders",
  "mode": "ALL"
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `UNION_SCHEMA_MISMATCH` | 先统一各输入字段名集合及类型。 |
| `UNION_MIXED_DATASET_KIND` | 不能混合静态表和无界流。 |
| `UNION_WATERMARK_MISMATCH` | 核对各流分支的事件时间与 Watermark。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
