# Canvas `TOP_N` Processor 设计文档

## 1. 状态、目标与范围

- 设计状态：已实现。
- 正式协议版本：Canvas `1.17`。
- 节点类型：`TOP_N`。
- 节点类别：`PROCESSOR`。
- 执行模式：仅 `BATCH`。
- 图规则：恰好一条入边，至少一条出边。

`TOP_N` 根据明确排序选择全局或每个分组中的前 N 行。它是行筛选节点，不新增业务字段：

- `partitionByColumns=[]`：全局 Top N。
- 配置分区字段：每个分区分别取 Top N。
- `EXACT`：每组最多返回 N 行。
- `WITH_TIES`：第 N 行存在相同排序键时保留所有并列行。

节点不提供单独“排序后输出”承诺。排序仅用于决定哪些行被选中，后续 Processor 和 Sink 不保证物理行顺序。

## 2. 稳定配置协议

Java 稳定配置位于 `data-scalpel-contracts`，并复用 `DEDUPLICATE` 已有的 `SortField`。Business、Task Engine 和 Manifest 不得复制配置或排序模型。

```ts
interface TopNConfiguration {
  sourceTableName: string;
  outputTableName: string;
  partitionByColumns: string[];
  orderBy: SortField[];
  limit: number;
  tieStrategy: 'EXACT' | 'WITH_TIES';
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
  "outputTableName": "top_orders_by_customer",
  "partitionByColumns": [
    "customer_id"
  ],
  "orderBy": [
    {
      "columnName": "amount",
      "direction": "DESC",
      "nullOrdering": "LAST"
    },
    {
      "columnName": "order_id",
      "direction": "ASC",
      "nullOrdering": "LAST"
    }
  ],
  "limit": 3,
  "tieStrategy": "EXACT"
}
```

协议规则：

- `sourceTableName`、`outputTableName` 必填。
- `partitionByColumns` 可以为空，同一字段不得重复。
- `orderBy` 至少一项，同一字段不得重复。
- 分区字段可以同时出现在排序字段中，虽然通常没有必要。
- `limit` 为整数，范围固定为 `1..1000000`。
- `tieStrategy` 必须是 `EXACT` 或 `WITH_TIES`。
- 配置不保存临时排名字段、X6 状态、Spark WindowSpec 或 SQL。

## 3. 精确语义与并列处理

### 3.1 `EXACT`

- 全局模式最多返回 N 行。
- 分区模式每个分区最多返回 N 行。
- 分区模式等价于按配置排序生成 `row_number()` 并保留 `row_number <= limit`。
- 当第 N 位存在完全相同的排序键时，具体保留哪一行无法仅从 Schema 保证。需要稳定选择时，用户应在 `orderBy` 末尾增加业务唯一字段作为 tie-breaker。
- Compiler 不扫描真实数据判断排序键是否唯一，也不为每个 EXACT 配置产生无法消除的永久 Warning。

### 3.2 `WITH_TIES`

- 全局或每个分区先按配置排序。
- 使用与 SQL `FETCH FIRST N ROWS WITH TIES` 等价的排名语义。
- 实现使用 `rank()`，保留 `rank <= limit`。
- 如果第 N 行与其他行排序键完全相同，所有并列行都保留，因此结果可能超过 N 行。
- 使用 `dense_rank <= limit` 会错误地表达“前 N 个不同排序值”，不得采用。

### 3.3 NULL 与排序

- 每个排序字段必须显式配置升序/降序和 NULLS FIRST/NULLS LAST。
- 多字段按数组顺序比较。
- TOP_N 不把 NULL 自动替换为零、空字符串或其他值。

## 4. 执行、Map 与 Schema 传播

Operator 执行策略：

- 全局 `EXACT`：使用显式 orderBy 后 limit，或使用语义等价且能保持 Schema 的 Spark 计划。
- 分区 `EXACT`：使用 `Window.partitionBy(...).orderBy(...) + row_number()`。
- 全局/分区 `WITH_TIES`：使用相应 Window + `rank()`。
- 内部排名列使用不会与业务字段冲突的临时名称，并在最终单次投影中移除。
- Compiler 只构造和分析零行计划，不执行排序 Action 或读取真实数据。

Map 规则：

- 保留输入 Map 中全部表。
- 以 `outputTableName` 追加 Top N 结果。
- 输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 不修改来源 Map、Schema 或 Dataset。

Schema 规则：

- 输出字段、顺序和完整平台元数据继承来源表。
- 内部 row_number/rank 字段不得进入输出 Schema。
- `origin` 继承来源表。
- 只接受 `BOUNDED` 来源，输出 `datasetKind=BOUNDED`。
- 输出 `eventTimeColumn` 和 `watermarkDelay` 清空。

输出 Dataset 的行集合由排序决定，但 Canvas 协议不承诺后续节点或 Sink 的物理行顺序。需要数据库查询顺序时，仍应由消费方 SQL 的 ORDER BY 明确指定。

## 5. 校验与稳定错误码

复用：

- `CONFIGURATION_REQUIRED`
- `REQUIRED_CONFIGURATION`
- `TABLE_NOT_FOUND`
- `COLUMN_NOT_FOUND`
- `DUPLICATE_TABLE_NAME`
- `SPARK_ANALYSIS_ERROR`
- `NODE_EXECUTION_MODE_NOT_SUPPORTED`
- `UPSTREAM_INVALID`
- `INVALID_SORT_DIRECTION`
- `INVALID_NULL_ORDERING`

新增：

| 错误码 | 条件 |
| --- | --- |
| `DUPLICATE_TOP_N_PARTITION_COLUMN` | 分区字段重复 |
| `EMPTY_TOP_N_ORDER` | 没有排序字段 |
| `DUPLICATE_TOP_N_SORT_COLUMN` | 排序字段重复 |
| `INVALID_TOP_N_LIMIT` | limit 不是允许范围内的整数 |
| `INVALID_TOP_N_TIE_STRATEGY` | tieStrategy 未知 |
| `TOP_N_REQUIRES_BOUNDED_INPUT` | 来源表为 UNBOUNDED |

字段是否可排序由实际 Spark 表达式和 Analyzer 判断，不维护平台类型排序矩阵。节点在实时任务中返回 `NODE_EXECUTION_MODE_NOT_SUPPORTED`，并保留已安全取得的 `inputTables`。

## 6. 前端设计器

- Palette 名称为“Top N”，说明为“按排序选择全局或分组内的前 N 行”。
- 仅在批处理模式展示。
- 使用独立 `TopNProcessorInspector`。
- 顶部配置来源表和输出表。
- “范围”使用两个清晰选项：
  - 全局前 N：`partitionByColumns=[]`。
  - 每组前 N：显示可排序的分组字段选择。
- 排序规则使用紧凑可排序列表，包含字段、升降序和 NULL 位置。
- N 使用整数输入，界面同步提示允许范围。
- 并列策略使用带说明的选项：
  - 精确 N 行：并列时最多保留 N 行；需要稳定结果请添加唯一排序字段。
  - 保留并列：第 N 行的全部并列记录都会保留，结果可能超过 N。
- Inspector 持续显示“排序用于选行，不保证最终写出顺序”。
- 上游字段消失时保留分区和排序配置并原位标红。
- 节点摘要显示来源/输出表、全局/分组、N、并列策略和排序字段名，不显示任何数据值。

## 7. Task Engine、日志与失败边界

新增唯一无状态 `TopNNodeOperator`：

- `category()` 返回 `PROCESSOR`。
- `supportedModes()` 只返回 `BATCH`。
- 使用 Spark Column、Window、row_number 和 rank API，不拼接用户 SQL。
- 可与 `WINDOW`、`DEDUPLICATE` 共享内部排序与临时列工具，但不能依赖画布上必须存在 WINDOW 节点。
- Compiler 和 Runner 都通过内置 Registry 调用同一 Operator。
- Analyzer 失败返回 `SPARK_ANALYSIS_ERROR`。
- 不为日志或诊断额外执行 count、collect 或排序 Action。
- 未分类运行时失败回退为现有 `PROCESSOR_EXECUTION_FAILED`。
- 成功节点结果消息固定为“Top N 已准备”。

节点生命周期阶段为 `PROCESS`。安全摘要只允许记录来源/输出表、分区字段、排序字段、方向与 NULL 顺序、limit 和 tieStrategy。不得记录第 N 行的值、并列值、被移除行或实际输出行数。

## 8. 实施记录

本节点已按以下范围完成全链路接入：

1. `data-scalpel-contracts` 已增加 `TopNConfiguration`、并列策略枚举和节点定义，并复用稳定 `SortField`。
2. `TOP_N` 的最低协议版本固定为 Canvas `1.17`；低版本携带该节点返回 `NODE_TYPE_REQUIRES_SCHEMA_VERSION`。
3. Business Validator、Upgrader、持久化和 Manifest 直接使用 Contracts 类型，没有复制 Top N 模型。
4. Task Engine 已注册唯一 `TopNNodeOperator`，并接入图规则、仅批模式、有界性、Schema 传播、安全摘要和错误分类。
5. 前端已同步类型、默认配置、JSON IO、Registry、Ports、Palette、序列化和独立 Inspector。
6. 正式配置、传播语义和错误边界已并入 `canvas-task-definition.md`。

## 9. 行为验收标准

- 全局 EXACT、分区 EXACT、全局 WITH_TIES 和分区 WITH_TIES 可稳定 JSON 往返。
- 多字段排序方向和 NULL 顺序得到精确保留。
- EXACT 最多返回 N 行；WITH_TIES 使用 rank 语义并保留第 N 行并列记录。
- 空排序、重复字段、非法 N、未知并列策略和表名冲突返回准确错误。
- 输出 Schema 不包含内部排名字段，并完整继承来源字段。
- Compiler 不读取真实数据，不验证排序键唯一性，不触发 Spark Action。
- 实时任务和 UNBOUNDED 来源稳定拒绝，同时保留可用输入上下文。
- 日志不包含排序边界值、并列值或实际数据。

## 10. 测试设计

- Contracts/Jackson：EXACT/WITH_TIES、全局/分区配置、排序顺序、`1.17` 版本门槛和旧定义升级。
- 结构校验：空排序、重复分区/排序字段、非法 limit、未知并列策略和表名冲突。
- Spark Operator：全局 EXACT、分区 EXACT、全局 WITH_TIES、分区 WITH_TIES 及多字段 NULL 排序。
- 并列语义：EXACT 每组最多 N 行；WITH_TIES 使用 rank 而不是 dense_rank。
- Schema：来源字段和顺序完整继承，内部 row_number/rank 字段不泄漏。
- 有界性与模式：批处理 BOUNDED 成功，实时任务和 UNBOUNDED 来源稳定拒绝。
- 前端：范围切换、分区/排序编辑、并列说明、失效字段保留、摘要和 JSON 往返。
- 日志安全：第 N 行值、并列值、被移除行和实际输出数据不进入日志或结果。

## 11. 不在范围内

- Offset、分页、Bottom N、随机抽样和按比例抽样。
- 动态 N、参数占位符和基于字段值的 N。
- 流式持续 Top N、状态 TTL、Watermark 和结果撤回语义。
- 输出排名字段；需要排名字段使用 `WINDOW`。
- 承诺最终数据库或文件中的物理行顺序。
- 自动推断唯一排序键或读取数据判断并列。

## 12. 协议兼容

Canvas `1.17` 已正式引入 `TOP_N`。当前协议版本为 `1.22`，系统兼容读取 `1.0`～`1.22` 并统一规范化为 `1.22`；`1.0`～`1.16` 定义不得携带本节点。

未来需要 Bottom N 时优先通过反转排序方向表达；需要分页或抽样时应新增语义独立的 Processor，不能把 offset、percentage、random seed 等无关字段持续堆入 `TopNConfiguration`。
