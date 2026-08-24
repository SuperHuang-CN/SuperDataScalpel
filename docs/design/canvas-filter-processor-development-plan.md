# Canvas `FILTER` Processor 开发计划

## 1. 目标与范围

新增 `FILTER` Processor，用结构化条件筛选一张上游逻辑表，并以新的逻辑表名输出结果。

首期目标：

- 同一配置同时支持 `BATCH`、`STREAMING`。
- 条件保存为稳定 AST，不保存 SQL、Spark 表达式或 Java 类名。
- 支持条件组嵌套，组内条件按 `AND` 或 `OR` 组合。
- 保留输入表的字段结构、物理来源、有界性、事件时间和 Watermark。
- 预检与 Runner 使用同一个无状态 `FilterNodeOperator`。

节点类别为 `PROCESSOR`。图规则为至少一条入边和一条出边；多个上游表 Map 先执行无覆盖合并。

## 2. 稳定配置协议

### 2.1 节点配置

```ts
interface FilterConfiguration {
  sourceTableName: string;
  outputTableName: string;
  condition: CanvasFilterCondition;
}

type CanvasFilterCondition =
  | CanvasFilterGroup
  | CanvasFieldPredicate;

interface CanvasFilterGroup {
  kind: 'GROUP';
  operator: 'AND' | 'OR';
  children: CanvasFilterCondition[];
}

interface CanvasFieldPredicate {
  kind: 'PREDICATE';
  columnName: string;
  operator: FilterOperator;
  values: CanvasLiteral[];
}

interface CanvasLiteral {
  dataType: PlatformDataType;
  value: string | null;
}

type FilterOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'GREATER_THAN'
  | 'GREATER_THAN_OR_EQUALS'
  | 'LESS_THAN'
  | 'LESS_THAN_OR_EQUALS'
  | 'IN'
  | 'NOT_IN'
  | 'IS_NULL'
  | 'IS_NOT_NULL'
  | 'CONTAINS'
  | 'STARTS_WITH'
  | 'ENDS_WITH';
```

示例：

```json
{
  "sourceTableName": "orders",
  "outputTableName": "paid_orders",
  "condition": {
    "kind": "GROUP",
    "operator": "AND",
    "children": [
      {
        "kind": "PREDICATE",
        "columnName": "status",
        "operator": "IN",
        "values": [
          { "dataType": "STRING", "value": "PAID" },
          { "dataType": "STRING", "value": "SHIPPED" }
        ]
      },
      {
        "kind": "PREDICATE",
        "columnName": "amount",
        "operator": "GREATER_THAN",
        "values": [
          { "dataType": "DECIMAL", "value": "100.00" }
        ]
      }
    ]
  }
}
```

### 2.2 Literal 规则

- `CanvasLiteral` 是 FILTER 与后续表达式节点共享的稳定契约，放入 `data-scalpel-contracts`，不得在前端和 Task Engine 各定义一份。
- `value` 使用与语言和数据库无关的字符串表示：Boolean 为 `true/false`，日期为 `yyyy-MM-dd`，Timestamp 使用 ISO-8601，数值使用十进制字符串。
- `IS_NULL`、`IS_NOT_NULL` 的 `values` 必须为空。
- `IN`、`NOT_IN` 至少包含一个值；其他操作符恰好包含一个值。
- 首期不支持字段与字段比较、SQL 片段、脚本函数、子查询或动态运行参数。
- Literal 的解析和字段比较可执行性最终由实际 Spark 表达式和 Analyzer 判断，不维护平台类型兼容矩阵。

## 3. 输入、输出与 Schema 传播

Operator 接收已经合并的直接上游 `Map<tableName, SparkCanvasTable>`：

1. 按 `sourceTableName` 精确查找来源表。
2. 在来源 Dataset 上构造 Spark Column 条件并执行 `filter`。
3. 复制输入 Map，保留其中所有表。
4. 以 `outputTableName` 添加筛选结果。

`outputTableName` 必须与输入 Map 中所有现有 Key 不同，包括 `sourceTableName`。冲突返回 `DUPLICATE_TABLE_NAME`，不覆盖、不自动改名。

输出表：

- 字段及顺序完全继承来源表。
- `origin`、`datasetKind`、`eventTimeColumn`、`watermarkDelay` 原样继承。
- 不修改上游 `SparkCanvasTable`、Schema 或 Map。

## 4. 校验与稳定错误码

复用通用错误码：

- `CONFIGURATION_REQUIRED`
- `REQUIRED_CONFIGURATION`
- `TABLE_NOT_FOUND`
- `COLUMN_NOT_FOUND`
- `DUPLICATE_TABLE_NAME`
- `SPARK_ANALYSIS_ERROR`
- `NODE_EXECUTION_MODE_NOT_SUPPORTED`
- `UPSTREAM_INVALID`

新增节点专属错误码：

| 错误码 | 条件 |
| --- | --- |
| `INVALID_FILTER_CONDITION` | 条件节点 kind 未知、结构不完整或超过服务端安全深度/数量限制 |
| `EMPTY_FILTER_GROUP` | GROUP 没有子条件 |
| `INVALID_FILTER_OPERATOR` | 操作符不在稳定枚举中 |
| `INVALID_FILTER_OPERAND_COUNT` | values 数量与操作符不匹配 |
| `INVALID_FILTER_LITERAL` | Literal 类型、空值或稳定字符串表示无效 |

服务端应设置明确但宽松的 AST 深度与节点数量上限，防止异常定义导致递归或计划膨胀；具体上限在实施时写入正式协议。字段存在性、表名冲突属于业务校验；类型比较能否建立 Spark 计划由 Analyzer 决定。

## 5. 前端设计器

- Palette 在“处理器”分类增加“筛选”，说明为“按组合条件筛选一张表的数据行”。
- 默认配置只创建空字符串、空条件组等强类型草稿，不使用 `any`。
- Inspector 拆分为 `components/processors/FilterProcessorInspector.tsx`，避免继续扩大 `CanvasNodeInspector.tsx`。
- 来源表和字段只使用编译结果的 `inputTables`；编译暂不可用时展示等待/失败状态。
- 条件编辑器采用树形分组：
  - 支持添加条件、添加条件组、切换 AND/OR、删除和调整同组顺序。
  - 根据操作符控制值输入数量。
  - 根据字段平台类型选择合适的紧凑输入控件，但最终仍写出稳定字符串。
- 上游字段消失时保留原字段名和 Literal，并在对应行标记错误，不静默清空。
- 节点卡片摘要显示来源表、输出表、条件数量和顶层 AND/OR，不显示实际条件值。
- JSON 导入、序列化和 X6 Runtime 转换必须保留 AST 语义，不写入组件状态或 X6 字段。

## 6. Task Engine Operator

新增唯一 `FilterNodeOperator`：

- `category()` 返回 `PROCESSOR`。
- `supportedModes()` 返回 `BATCH`、`STREAMING`。
- 递归将稳定 AST 转为 Spark `Column`，所有字段通过安全标识符引用。
- 通过来源的零行 Dataset 构造与分析计划；预检不读取真实数据。
- Analyzer 失败统一转换为节点级 `SPARK_ANALYSIS_ERROR`，同时保留安全诊断信息。
- `apply` 成功后返回包含原输入表和新增输出表的 Map。

节点生命周期阶段使用 `PROCESS`。安全摘要仅记录来源表、输出表、条件叶子数量、分组数量和操作符集合，不记录 Literal 实际值。运行失败使用现有统一异常分类器和诊断 ID。

## 7. 分阶段实施

### 阶段 1：协议与版本

- 在 `data-scalpel-contracts` 增加 Filter 节点定义、配置、谓词 AST、Literal 和枚举。
- 将节点加入 `CanvasNodeDefinition` 判别联合与 `CanvasNodeType`。
- 实施时把当时的 `schemaMinorVersion` 增加 1；不得在本计划中预占固定版本号。
- Upgrader 拒绝较低 minor version 携带 `FILTER`，旧定义升级后语义不变。
- 同步业务层保存模型、Validator、Manifest 转换和所有 sealed switch。

### 阶段 2：统一 Operator

- 实现 `FilterNodeOperator` 并注册到内置 Registry。
- 更新 `CanvasGraphPlan` 节点度数和模式能力校验。
- 接入 Compiler、批 Runner、流 Runner 的统一生命周期、安全摘要和结果消息。

### 阶段 3：前端设计器

- 增加 TypeScript 判别联合、默认配置、Registry、Ports、Palette、JSON IO。
- 实现独立 Filter Inspector 和结构化条件编辑器。
- 接入编译错误、输入 Schema、节点摘要和未应用修改保护。

### 阶段 4：正式文档

- 将已实现节点及其版本、配置、传播、错误码写入 `canvas-task-definition.md`。
- 同步 Task Engine 执行与安全日志文档。

## 8. 行为验收标准

- 单条件、嵌套 AND/OR、IN、NULL 和字符串操作符可正确往返 JSON。
- 批处理和流处理使用同一配置与 Operator。
- 缺表、缺字段、空分组、错误操作数数量和无效 Literal 定位到准确配置路径。
- 输出表保留来源 Schema、Origin、有界性和事件时间属性，同时不移除来源表。
- 输出表名与任一可见表同名时稳定失败。
- 上游变化后 Inspector 保留失效配置并显示服务端诊断。
- 预检不访问真实数据，日志和结果不包含条件实际值。

## 9. 不在范围内

- SQL 文本、自定义脚本、正则表达式和用户自定义函数。
- 字段与字段比较、子查询、跨表条件。
- 运行时参数占位符和可视化数据预览。
- 基于数据分布的条件优化或选择率估算。

## 10. 协议兼容与依赖

FILTER 的谓词 AST 和 `CanvasLiteral` 应作为后续 `DERIVE_COLUMNS` 中 `CASE_WHEN` 条件的唯一共享契约。实现 DERIVE 时不得复制另一套谓词模型。只有节点真正实现并完成全链路支持时，才更新正式 Canvas 节点清单。
