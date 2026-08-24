# Canvas `GEOMETRY_VALIDATE` Processor 设计文档

## 1. 状态、目标与范围

- 实现状态：已实现。
- 目标协议版本：Canvas `1.21`。
- 节点类型：`GEOMETRY_VALIDATE`。
- 节点类别：`PROCESSOR`。
- 执行模式：`BATCH`、`STREAMING`。
- 图规则：至少一条入边和一条出边；多个上游表 Map 先执行无覆盖合并。

`GEOMETRY_VALIDATE` 对一张逻辑表中的一个 Geometry 字段执行拓扑合法性诊断，保留全部
来源行和字段，并追加合法性布尔值及可选的无效原因字段。节点只负责观测，不过滤、修复
或拒绝无效 Geometry，从而使质量检查和数据修改保持明确边界。

需要剔除无效行时，在本节点后使用普通 `FILTER`；需要修复时使用未来独立的
`GEOMETRY_REPAIR`。本节点不把数据中的无效 Geometry 当作任务错误。

## 2. 稳定配置协议

```ts
interface GeometryValidateConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  validColumnName: string;
  reasonColumnName: string | null;
}
```

示例：

```json
{
  "sourceTableName": "land_parcels",
  "outputTableName": "land_parcels_validated",
  "geometryColumnName": "boundary",
  "validColumnName": "boundary_is_valid",
  "reasonColumnName": "boundary_invalid_reason"
}
```

协议限制：

- `sourceTableName`、`outputTableName`、`geometryColumnName`、`validColumnName` 必填。
- `reasonColumnName=null` 表示不产生原因字段；非空时必须是非空字段名。
- 输出字段名不能与来源字段或彼此重名。
- 一个节点只验证一个 Geometry 字段。
- 配置不保存修复策略、过滤策略、Geometry 值、Sedona 表达式或 UI 草稿状态。

## 3. 验证语义

Operator 使用 Sedona Column API：

- `validColumnName` 使用 `ST_IsValid(geometry)`。
- 配置原因字段时，使用 `ST_IsValidReason(geometry)` 取得诊断文本。
- 原因字段通过受控条件表达式只在 `isValid=false` 时写入；Geometry 有效或为 NULL 时
  固定返回 NULL，不保存 Sedona 的“Valid Geometry”等成功文本。

NULL 和无效值语义：

- 来源 Geometry 为 NULL 时，合法性字段为 NULL，原因字段也为 NULL。
- 非 NULL 且合法时，合法性字段为 `true`，原因字段为 NULL。
- 非 NULL 且无效时，合法性字段为 `false`，原因字段保存 Sedona 返回的拓扑诊断。
- Empty Geometry 是否有效遵循 Sedona/JTS 当前稳定语义，不在 Canvas 维护第二套判断。
- 无效 Geometry 是正常数据结果，不产生编译错误、运行失败或 WARNING。
- GeometryUDT 本身损坏、Sedona 无法执行或 Spark Analyzer 拒绝表达式时仍按执行错误处理。

Compiler 只分析零行计划，不扫描真实 Geometry，也不统计有效率、无效原因分布或空值数量。

## 4. Map、Schema 与批流传播

Map 规则：

- 从输入 Map 按 `sourceTableName` 精确查找来源表。
- 保留输入 Map 中全部表，以 `outputTableName` 追加诊断结果。
- 输出名与任一已有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。

Schema 规则：

- 来源字段和 Geometry 的 kind、CRS、dimension、nullable 及字段元数据全部保留。
- `validColumnName` 追加为 BOOLEAN；nullable 与来源 Geometry 字段一致。
- 原因字段存在时追加为 STRING，固定 nullable，length 为空。
- 两个诊断字段的 default、autoIncrement、generated 和 comment 清空。
- 输出字段顺序固定为来源字段、合法性字段、可选原因字段。
- 输出表 `origin=null`，继承来源 `datasetKind`、`eventTimeColumn` 和 `watermarkDelay`。

节点逐行执行且不改变行数、事件时间或 Watermark。BOUNDED 和 UNBOUNDED 输入复用同一
Operator，不建立流式状态，也不需要额外 Checkpoint 配置。

## 5. 校验与稳定错误码

复用错误码：

- `CONFIGURATION_REQUIRED`
- `REQUIRED_CONFIGURATION`
- `TABLE_NOT_FOUND`
- `COLUMN_NOT_FOUND`
- `DUPLICATE_TABLE_NAME`
- `DUPLICATE_COLUMN_NAME`
- `GEOMETRY_FIELD_OPERATION_UNSUPPORTED`
- `GEOMETRY_TYPE_DEFINITION_REQUIRED`
- `UNSUPPORTED_GEOMETRY_CRS`
- `UNSUPPORTED_GEOMETRY_DIMENSION`
- `SPARK_ANALYSIS_ERROR`
- `NODE_EXECUTION_MODE_NOT_SUPPORTED`
- `UPSTREAM_INVALID`

本节点不为 `isValid=false` 新增错误码，因为它是输出数据而不是任务失败。配置原因字段时，
原因字段名为空、与合法性字段重名或与来源字段重名，统一使用 `REQUIRED_CONFIGURATION`
或 `DUPLICATE_COLUMN_NAME` 指向准确配置路径。

## 6. 前端设计器

- Palette 名称为“Geometry 验证”，说明为“检查空间字段拓扑合法性并追加诊断字段”。
- 节点位于“空间处理”分组，批处理和实时模式均展示。
- 来源表和字段候选只使用 Compiler 返回的 `inputTables`，字段列表只显示 GEOMETRY。
- Inspector 配置来源表、Geometry 字段、合法性字段名和“输出无效原因”开关。
- 开启原因输出后显示原因字段名；关闭时保存 `reasonColumnName=null`。
- 面板明确提示：节点保留无效行，不会修复 Geometry；过滤和修复应使用后续节点。
- 上游表、字段或空间元数据失效时保留原配置并标红，不静默清空。
- 节点摘要显示来源表/字段、输出表和是否输出原因，不显示诊断结果或 Geometry 值。

## 7. Task Engine、Runner 与安全边界

新增唯一无状态 `GeometryValidateNodeOperator`，Compiler 与 Runner 通过内置 Registry 复用。
节点执行阶段固定为 `PROCESS`，成功消息固定为“Geometry 验证已准备”。

安全摘要只允许记录来源表、输出表、Geometry 字段和两个输出字段名。无效原因可能包含
拓扑位置或坐标，只能作为 Dataset 中的业务输出字段存在，不得进入节点摘要、生命周期
日志、执行结果、Kafka 事件或异常消息。

节点成功只表示诊断表达式已经准备，不得为了日志统计触发 `count()`、有效率计算或原因
分组。真实诊断值只在下游正常 Action 中计算。

## 8. 行为验收标准

- 带原因和不带原因的配置均可稳定 JSON 往返。
- 有效、无效、NULL 和 Empty Geometry 遵循定义语义。
- 无效 Geometry 不导致任务失败、WARNING、丢行或自动修复。
- 原 Geometry Schema 和空间元数据保持不变，诊断字段顺序和 nullable 稳定。
- 输入 Map 完整保留，输出表只追加不覆盖。
- BATCH 与 STREAMING 使用同一 Operator，并继承有界性、事件时间和 Watermark。
- Compiler 不读取真实 Geometry 或统计有效率。
- Inspector 只消费 `inputTables` 并保留失效上游值。
- 安全摘要和日志不包含无效原因、坐标或 Geometry 值。

## 9. 不在范围内

- `ST_MakeValid`、Buffer(0) 或其他自动修复。
- DROP_INVALID、ERROR_ON_INVALID、旁路错误表或质量阈值门禁。
- 有效率统计、原因聚合、样例值和地图预览。
- 同时验证多个 Geometry 字段。
- 自定义合法性算法、容差参数或 ESRI validity flag。

## 10. 协议发布

本节点已与 `GEOMETRY_CONSTRUCT`、`SPATIAL_MEASURE`、`GEOMETRY_SERIALIZE` 一起在
Canvas `1.21` 引入。当前实现兼容读取 `1.0`～`1.22`，并统一规范化写出 `1.22`。
