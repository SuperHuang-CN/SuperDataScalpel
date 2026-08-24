# Canvas `DEDUPLICATE` Processor 开发计划

## 1. 目标与范围

新增批处理 `DEDUPLICATE` Processor，按全字段或业务键删除重复行，并可按明确排序保留第一条或最后一条。

首期目标：

- `ANY`：任意保留一条重复记录。
- `FIRST`：按配置排序保留第一条。
- `LAST`：按配置排序保留最后一条。
- 只支持 `BATCH`。
- 不触发额外 Spark Action。
- Compiler 与 Runner 使用唯一无状态 `DeduplicateNodeOperator`。

实时去重必须处理事件时间、Watermark、状态保留和迟到数据，未来使用独立 `STREAM_DEDUPLICATE`。

节点类别为 `PROCESSOR`。图规则为至少一条入边和一条出边；多个上游表 Map 先执行无覆盖合并。

## 2. 稳定配置协议

```ts
interface DeduplicateConfiguration {
  sourceTableName: string;
  outputTableName: string;
  keyColumns: string[];
  keepStrategy: 'ANY' | 'FIRST' | 'LAST';
  orderBy: SortField[];
}

interface SortField {
  columnName: string;
  direction: 'ASC' | 'DESC';
  nullOrdering: 'FIRST' | 'LAST';
}
```

示例：

```json
{
  "sourceTableName": "orders",
  "outputTableName": "latest_orders",
  "keyColumns": ["order_id"],
  "keepStrategy": "FIRST",
  "orderBy": [
    {
      "columnName": "updated_at",
      "direction": "DESC",
      "nullOrdering": "LAST"
    },
    {
      "columnName": "ingested_at",
      "direction": "DESC",
      "nullOrdering": "LAST"
    }
  ]
}
```

协议规则：

- `keyColumns=[]` 表示按全部字段去重，只允许 `ANY`。
- `ANY` 使用 Spark `dropDuplicates`；`orderBy` 必须为空。
- `FIRST`、`LAST` 必须配置至少一个 key 字段和至少一个排序字段。
- key 字段不得重复；排序字段不得重复。
- `FIRST` 取配置排序下的第一行。
- `LAST` 通过反转每个排序字段的方向和 null 顺序后取第一行：
  - `ASC NULLS FIRST` 反转为 `DESC NULLS LAST`。
  - `ASC NULLS LAST` 反转为 `DESC NULLS FIRST`，其余类推。
- 多个排序字段依次解决并列；无法从 Schema 证明最终唯一时不生成永久 Warning。

## 3. 输入、输出与 Schema 传播

Operator 查找 `sourceTableName`：

- `ANY + keyColumns=[]` 对全部字段 `dropDuplicates()`。
- `ANY + keyColumns!=[]` 按 key 字段 `dropDuplicates(keyColumns)`。
- `FIRST/LAST` 使用 `Window.partitionBy(keyColumns).orderBy(...)` 与 `row_number()`，保留序号 1 并移除内部临时列。

Map 规则：

- 保留输入 Map 中所有表。
- 以 `outputTableName` 新增去重结果。
- 输出名与任一现有 Key 冲突时报 `DUPLICATE_TABLE_NAME`，不替换来源表。

Schema 规则：

- 输出字段、顺序和完整平台元数据继承来源表。
- `origin` 继承来源表，表示字段仍来自同一物理来源。
- `datasetKind` 固定为 `BOUNDED`。
- `eventTimeColumn`、`watermarkDelay` 在批处理输出中为空。
- 内部 `row_number` 临时字段不得进入输出 Schema、Canvas 定义、日志或下游 Map。

## 4. 校验与稳定错误码

复用：

- `CONFIGURATION_REQUIRED`
- `REQUIRED_CONFIGURATION`
- `TABLE_NOT_FOUND`
- `COLUMN_NOT_FOUND`
- `DUPLICATE_TABLE_NAME`
- `SPARK_ANALYSIS_ERROR`
- `NODE_EXECUTION_MODE_NOT_SUPPORTED`
- `UPSTREAM_INVALID`

新增：

| 错误码 | 条件 |
| --- | --- |
| `INVALID_DEDUPLICATE_KEEP_STRATEGY` | keepStrategy 未知 |
| `DUPLICATE_DEDUPLICATE_KEY_COLUMN` | key 字段重复 |
| `DUPLICATE_DEDUPLICATE_SORT_COLUMN` | 排序字段重复 |
| `DEDUPLICATE_KEYS_REQUIRED` | FIRST/LAST 未配置 key |
| `DEDUPLICATE_ORDER_REQUIRED` | FIRST/LAST 未配置 orderBy |
| `DEDUPLICATE_ORDER_NOT_ALLOWED` | ANY 配置了 orderBy |
| `FULL_ROW_DEDUPLICATE_REQUIRES_ANY` | keyColumns 为空但策略不是 ANY |
| `INVALID_SORT_DIRECTION` | direction 未知 |
| `INVALID_NULL_ORDERING` | nullOrdering 未知 |

排序字段是否可由 Spark 排序以 Analyzer 为准。配置排序不能从 Schema 保证绝对唯一时不报错，也不为了判断并列读取真实数据。

## 5. 前端设计器

- Palette 的批处理“处理器”分类增加“去重”，说明为“按业务键保留任意、第一条或最后一条记录”；实时模式不展示。
- Inspector 拆分为 `components/processors/DeduplicateProcessorInspector.tsx`。
- 顶部选择来源表和输出表。
- 去重键使用可搜索多选，并提供“按全部字段去重”的清晰开关或空 key 语义说明。
- 保留策略使用三个带说明的选项：
  - 任意一条：最快，不保证具体记录。
  - 第一条：按下方排序取第一。
  - 最后一条：按下方排序取最后。
- FIRST/LAST 时展示可排序的排序规则表格；ANY 时隐藏并清空需由用户确认，不能静默丢弃未应用配置。
- 每个排序项编辑字段、升降序、NULL 在前/后。
- 上游字段消失时保留旧 key/order 项并标红。
- 节点摘要显示来源/输出表、key 数量、策略和排序字段数量。

## 6. Task Engine Operator

新增唯一 `DeduplicateNodeOperator`：

- 类别为 `PROCESSOR`，`supportedModes()` 只含 `BATCH`。
- ANY 使用 Dataset `dropDuplicates`。
- FIRST/LAST 使用 Window + `row_number`，不得使用 collect、count 或额外数据扫描来选择记录。
- 内部临时列名使用不可与业务字段冲突的生成策略，并在最终单次投影中移除。
- Analyzer 失败返回 `SPARK_ANALYSIS_ERROR`。
- 成功后复制输入 Map 并新增输出表。

节点阶段为 `PROCESS`。安全摘要只记录来源/输出表名、key 字段名、策略、排序字段与排序方向，不记录被保留或被删除的实际值。

## 7. 分阶段实施

### 阶段 1：协议

- 增加配置、策略/排序枚举、节点定义和 `CanvasNodeType.DEDUPLICATE`。
- 加入 Java/Jackson/TypeScript 判别联合。
- 实施时将当时 Canvas minor version 增加 1；低版本携带节点稳定拒绝。
- 同步业务 Validator、Upgrader、Manifest 和 sealed switch。

### 阶段 2：Operator

- 实现并注册 `DeduplicateNodeOperator`。
- 更新图度数和仅批处理能力。
- 接入 Compiler、批 Runner、安全摘要、节点结果消息和统一错误分类。
- 流 Runner 必须在 Operator 前返回 `NODE_EXECUTION_MODE_NOT_SUPPORTED`。

### 阶段 3：前端

- 更新类型、默认配置、JSON IO、Registry、Ports 和批处理 Palette。
- 实现独立 Inspector、key 选择和排序规则编辑器。
- 接入编译输入 Schema、错误路径、上游失效保留和节点摘要。

### 阶段 4：正式文档

- 实现后更新 `canvas-task-definition.md` 的版本、配置、LAST 反转语义和示例。
- 明确未来流式版本使用独立 `STREAM_DEDUPLICATE`。

## 8. 行为验收标准

- 全字段 ANY、按 key ANY、按排序 FIRST 和 LAST 可稳定 JSON 往返。
- FIRST/LAST 对方向与 NULL 顺序的语义明确且可复现。
- 策略与 key/order 组合不合法时返回稳定错误。
- key 和排序字段重复、不存在时定位到准确数组项。
- 输出 Schema 不包含内部 row number 字段，并完整保留来源字段顺序。
- 预检不执行 Spark Action，不读取真实数据判断并列。
- 实时任务稳定拒绝该节点，提示未来使用流式专用节点。

## 9. 不在范围内

- 流式去重、Watermark、状态 TTL 和迟到数据策略。
- 基于数据内容自动选择“最佳”记录。
- 字段合并、非空优先或多行聚合。
- 输出重复记录旁路表或数据质量统计。
- 对排序最终唯一性的真实数据扫描。

## 10. 协议兼容

新增节点只提升当时 minor version。未来 `STREAM_DEDUPLICATE` 必须是独立节点类型和配置，不能向本节点追加可选 Watermark/状态字段而改变批处理定义的既有语义。
