# Canvas UNION Merge Layers 设计

## 1. 状态与官方参照

- 当前接入版本：Canvas `4.62`。
- 复用节点：`UNION`，不新增一个只有空间名称不同的 Processor。
- 官方参照：[ArcGIS GeoAnalytics Merge Layers](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/merge-layers/)。
- 当前实现覆盖字段 Match/Rename/Remove、缺失字段补 NULL、数值 Match 转换以及空间和时间约束。
- 真实 ArcGIS 服务结果、Esri 字段类型细分、容差、容量和性能尚未完成对照，因此不宣称数值或规模等价。

## 2. 用户语义

`UNION` 的第一张输入表是基准层，决定初始输出字段和顺序；其余输入按数组顺序逐层合并。节点保留
`ALL/DISTINCT` 行合并模式，并通过 `mergingTables` 区分两种字段语义：

- `null` 或缺失：旧版严格同 Schema Union。所有输入字段集合必须相同。
- 空数组：启用默认 Merge Layers。合并层同名字段写入已有字段，非同名字段按原名追加。
- 非空数组：只记录偏离默认行为的合并层和字段规则。

默认 Merge Layers 保留基准层全部字段。某张输入缺少最终输出字段时，该行对应字段为 NULL；基准层
缺少后续追加字段时同样补 NULL。

## 3. 稳定配置

```json
{
  "inputTableNames": ["current_orders", "history_orders", "archive_orders"],
  "outputTableName": "all_orders",
  "mode": "ALL",
  "mergingTables": [
    {
      "tableName": "history_orders",
      "fieldRules": [
        {
          "sourceColumnName": "legacy_status",
          "action": "MATCH",
          "targetColumnName": "status"
        },
        {
          "sourceColumnName": "temporary_note",
          "action": "REMOVE",
          "targetColumnName": null
        },
        {
          "sourceColumnName": "legacy_owner",
          "action": "RENAME",
          "targetColumnName": "history_owner"
        }
      ]
    }
  ]
}
```

动作语义：

| 动作 | 目标字段 | 行为 |
| --- | --- | --- |
| `MATCH` | 必须是当时已存在的输出字段 | 来源值写入该字段，必要时执行受控数值 Cast |
| `RENAME` | 必须是尚不存在的新字段 | 以指定名称把来源字段追加到输出 Schema |
| `REMOVE` | 必须为 `null` | 该来源字段不进入合并结果 |

`RENAME` 不能占用已有输出字段；要写入已有字段必须明确使用 `MATCH`。后续合并层可以 Match 之前层已经
追加的字段，因此输入表顺序属于稳定语义。

## 4. 类型、Geometry 与时间规则

- 普通字段 Match 支持相同 `PlatformDataType`。
- BYTE/SHORT/INTEGER/LONG/FLOAT/DOUBLE/DECIMAL 之间允许 Match，并显式 Cast 到目标字段类型。
- STRING 与数值、日期与字符串等其他跨类型 Match 在 Compiler 阶段拒绝。
- 输入字段名、规则来源字段和最终输出字段按大小写不敏感判重，避免 Spark Analyzer 产生歧义。
- 所有输入必须都是属性表，或都具有相同数量的 Geometry 字段。
- 每个合并层 Geometry 必须 Match 到基准层中类型、CRS 和维度完全一致的 Geometry；不能 Rename 或
  Remove。平台支持多 Geometry 表时，每个字段仍必须建立唯一 Match。
- 有界和无界表不能混合。无界表的来源事件时间名称可以不同，但必须 Match 到基准层事件时间字段；
  Watermark 必须一致。无界输入继续只支持 `ALL`。

## 5. 编译与运行

Operator 按以下顺序建立惰性 Spark 计划：

```text
校验全部输入与字段规则
→ 按输入顺序扩展最终输出 Schema
→ 每张表 select + alias + cast，并为缺失字段构造 typed NULL
→ unionByName(..., allowMissingColumns=true)
→ 可选 DISTINCT
→ 追加 outputTableName 到上游表 Map
```

Compiler 和 Runner 使用同一个 `UnionNodeOperator`。Compiler 只针对零行 Schema 建立计划，不读取真实
数据、不触发 Spark Action。任一输入或规则无效时节点整体无效，不传播部分 Union 结果。

## 6. Inspector 与 Canvas

- Inspector 的输入表列表可排序，第一项明确标记为顺序基准。
- “灵活对齐/严格同 Schema”切换只改变字段合并语义，不改变 `ALL/DISTINCT`。
- 灵活模式下，每张非基准表提供字段设置按钮；Modal 使用单行表格逐字段配置动作和目标。
- Match 下拉只开放同类型、数值兼容类型或 Geometry 定义一致的目标。
- 切换到 Match 时先保留当前合法目标，再找同名目标；只有一个兼容目标时才自动选择，否则留空让用户
  明确决定。
- 字段 Modal 提供确认式“重建建议”，按当前基准层和前序合并层重新生成 Match/Rename 建议。
- 更换基准层或删除带自定义规则的输入表前必须确认；新基准层不再适用的字段规则会删除，其余规则保留
  并交由 Task Engine 重新校验。保存时只写入相对默认行为有变化的规则。
- 上游表或字段失效时保留配置并就地标红，允许保存草稿。
- Canvas 卡片预览前两张输入，显示 `Merge Layers/严格 Schema`、表数、模式和结果字段数，不展示数据值。

## 7. 安全摘要与兼容性

- Runner 摘要只记录输入/输出安全表名、模式、是否启用 Merge Layers、配置层数和规则数。
- 摘要、日志、错误和 Canvas 卡片不记录字段实际值、缺失位置内容或样例数据。
- `mergingTables` 从 Canvas `4.62` 引入；低版本携带任何非 null 值时返回
  `UNION_MERGE_LAYERS_REQUIRE_SCHEMA_VERSION`。
- 旧定义缺失该字段时保持严格同 Schema 语义；读取后保存或导出规范化为当前小版本，但不自动启用
  Merge Layers。
