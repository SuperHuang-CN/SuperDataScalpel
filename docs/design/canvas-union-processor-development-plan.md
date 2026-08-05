# Canvas `UNION` Processor 开发计划

## 1. 目标与范围

新增 `UNION` Processor，按字段名合并至少两张可见上游逻辑表，并以新的逻辑表名输出。

首期目标：

- 支持 `ALL` 和 `DISTINCT`。
- 只按字段名合并，不支持按位置合并。
- 输入字段集合必须完全一致，字段顺序可以不同。
- 使用第一张配置表确定输出字段顺序。
- 支持批处理，以及满足有界性和状态约束的流式场景。
- Compiler 与 Runner 使用唯一无状态 `UnionNodeOperator`。

节点类别为 `PROCESSOR`。图上允许一条或多条入边，且至少一条出边；实际参与 Union 的表由配置选择，至少两张。

## 2. 稳定配置协议

```ts
interface UnionConfiguration {
  inputTableNames: string[];
  outputTableName: string;
  mode: 'ALL' | 'DISTINCT';
}
```

示例：

```json
{
  "inputTableNames": [
    "current_orders",
    "history_orders"
  ],
  "outputTableName": "all_orders",
  "mode": "ALL"
}
```

协议规则：

- `inputTableNames` 至少包含两个不同表名。
- 数组顺序稳定：第一张表决定输出字段顺序，其余表按字段名重排。
- 每张输入表必须具有完全相同的字段名集合。
- 不支持 `BY_POSITION`、缺失字段补 null、额外字段忽略或自动字段改名。
- `ALL` 保留重复行。
- `DISTINCT` 在完整 Union 结果上做全字段去重。

## 3. 输入、输出与 Schema 传播

Operator 接收所有直接上游 Map 的正常合并结果。现有规则保持不变：不同上游 Map 出现同名 Key 时先返回 `DUPLICATE_TABLE_NAME`，UNION 不增加“同名表代表待合并”的特殊通道。

处理步骤：

1. 按 `inputTableNames` 顺序查找至少两张不同逻辑表。
2. 以第一张表字段顺序为基准，对其他表按名称重排。
3. 使用 Spark `unionByName` 依次合并。
4. `mode=DISTINCT` 时对完整结果执行 distinct。
5. 复制输入 Map，并以 `outputTableName` 新增结果。

`outputTableName` 与任一现有表名冲突时返回 `DUPLICATE_TABLE_NAME`。

### 3.1 类型和字段元数据

- 字段类型兼容与必要的 Spark 类型提升由实际 `unionByName` 和 Analyzer 决定，不维护平台兼容矩阵。
- 输出字段顺序采用第一张表。
- 输出类型和 nullable 取最终 Analyzer Schema。
- 多来源结果不继承单一物理表约束；default、autoIncrement、generated、comment 等来源专属属性清空。
- `origin` 固定为 `null`。

### 3.2 有界性和流式属性

- 所有输入 `datasetKind` 必须一致。
- 全部 `BOUNDED` 输出 `BOUNDED`。
- 全部 `UNBOUNDED` 输出 `UNBOUNDED`。
- `BOUNDED` 与 `UNBOUNDED` 混合首期拒绝，不隐式把静态表重复拼入流。
- 无界输入的 `eventTimeColumn` 和 `watermarkDelay` 必须全部一致；一致时输出继承，不一致时拒绝。
- `DISTINCT` 作用于无界输入会形成无边界状态，首期拒绝；流式全量去重未来由具有 Watermark 和状态边界的独立节点承担。
- `DISTINCT` 用于全部有界输入仍可在批任务或流任务的有界分支中执行。

## 4. 校验与稳定错误码

复用：

- `CONFIGURATION_REQUIRED`
- `REQUIRED_CONFIGURATION`
- `TABLE_NOT_FOUND`
- `DUPLICATE_TABLE_NAME`
- `DUPLICATE_COLUMN_NAME`
- `SPARK_ANALYSIS_ERROR`
- `NODE_EXECUTION_MODE_NOT_SUPPORTED`
- `UPSTREAM_INVALID`

新增：

| 错误码 | 条件 |
| --- | --- |
| `UNION_REQUIRES_MULTIPLE_TABLES` | 选择表少于两张 |
| `DUPLICATE_UNION_INPUT_TABLE` | `inputTableNames` 重复 |
| `INVALID_UNION_MODE` | mode 未知 |
| `UNION_SCHEMA_MISMATCH` | 输入字段名集合不一致 |
| `UNION_MIXED_DATASET_KIND` | 有界与无界输入混合 |
| `UNION_EVENT_TIME_MISMATCH` | 无界输入事件时间字段不一致 |
| `UNION_WATERMARK_MISMATCH` | 无界输入 Watermark 不一致 |
| `STREAMING_UNION_DISTINCT_NOT_SUPPORTED` | 对无界输入使用 DISTINCT |

字段类型能否合并由 Analyzer 决定并使用 `SPARK_ANALYSIS_ERROR`。错误路径应尽可能定位 `inputTableNames[index]`。

## 5. 前端设计器

- Palette 增加“合并数据”，说明为“按字段名纵向合并多张结构一致的表”。
- Inspector 拆分为 `components/processors/UnionProcessorInspector.tsx`。
- 输入表使用至少两项的可排序多选，候选只取编译 `inputTables`。
- 第一项标记“输出字段顺序基准”。
- 选择后展示紧凑 Schema 对比：
  - 字段集合一致显示通过。
  - 缺失/额外字段分组显示，不用大段 Alert 占据抽屉。
  - 类型差异显示为待编译分析，不由前端自行判死。
- 模式使用 ALL/DISTINCT 单选；无界输入选择 DISTINCT 时提示不支持。
- 上游表失效时保留原配置项并标记不可用。
- 节点摘要显示输入表数量、输出表和 ALL/DISTINCT。

## 6. Task Engine Operator

新增唯一 `UnionNodeOperator`：

- 类别为 `PROCESSOR`，声明支持 `BATCH`、`STREAMING`。
- 由 Operator 显式推导多输入有界性、事件时间和 Watermark，不使用“继承第一个输入”的模糊默认值。
- 使用 `unionByName` 和 Analyzer 验证类型兼容。
- 对有界结果的 DISTINCT 使用整体 `distinct`。
- 无界 DISTINCT 在构造状态计划前返回稳定错误。
- 成功后复制输入 Map 并新增输出表。

节点阶段为 `PROCESS`。安全摘要只记录输入/输出表名、输入数量、模式、字段数量和有界性，不记录数据值或重复行样本。

## 7. 分阶段实施

### 阶段 1：协议

- 增加 Union 配置、模式枚举、节点定义和 `CanvasNodeType.UNION`。
- 加入 Java/Jackson/TypeScript 判别联合。
- 实施时将当时 Canvas minor version 增加 1；Upgrader 拒绝较低版本携带该节点。
- 同步业务 Validator、Manifest 与 sealed switch。

### 阶段 2：图规则与 Operator

- 将 Union 图规则设为入边至少一条、出边至少一条，不设置固定入边上限。
- 实现并注册 `UnionNodeOperator`。
- 接入 Compiler、批流 Runner、有界性传播、安全摘要和节点结果消息。

### 阶段 3：前端

- 更新类型、默认配置、JSON IO、Registry、Ports 和 Palette。
- 实现独立 Inspector、可排序表选择和 Schema 差异展示。
- 接入编译输入 Schema、错误路径和节点摘要。

### 阶段 4：正式文档

- 实现后更新 `canvas-task-definition.md` 的版本、字段匹配、有界性和流式限制。
- 同步 Task Engine 流式状态边界说明。

## 8. 行为验收标准

- 两张及多张字段集合相同但顺序不同的表可按名称合并。
- 输出顺序稳定采用第一张配置表。
- 少于两表、重复选表、字段集合不同和输出名冲突明确失败。
- 类型兼容由 Analyzer 判断，不由前端或平台矩阵提前拒绝。
- 全有界/全无界有界性推导正确，混合输入稳定拒绝。
- 无界输入事件时间与 Watermark 不一致时稳定拒绝。
- 无界 DISTINCT 被稳定拒绝，UNION ALL 不引入状态。
- 输出 Map 保留输入表并新增 origin=null 的结果表。

## 9. 不在范围内

- BY_POSITION。
- 缺失字段自动补 null、额外字段丢弃或字段名模糊匹配。
- 同名上游 Map 的特殊合并语义。
- 有界与无界表混合 Union。
- 无界 DISTINCT、状态 TTL 或 Watermark 去重。
- 自动来源标识列。

## 10. 协议兼容

新增节点只提升当时 minor version，不改变上游 Map 同名冲突规则。若未来需要宽松 Schema 合并或无界去重，应通过新模式的大版本变更或独立节点设计，不能静默改变现有 `UNION` 语义。
