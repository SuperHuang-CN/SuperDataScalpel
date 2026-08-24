# Canvas `DERIVE_COLUMNS` Processor 开发计划

## 1. 目标与范围

`DERIVE_COLUMNS` Processor 通过稳定表达式 AST 新增或覆盖多张来源表中的字段。每张表拥有独立输出设置和规则，节点还可以配置作用于全部已选表的全局规则。

首期能力：

- 列引用、Literal、二元算术、白名单函数和 `CASE_WHEN`。
- 可在一个节点中配置全局规则和每张表的多个独立派生字段。
- 同时支持 `BATCH`、`STREAMING`。
- 表达式输出类型由实际 Spark Analyzer 推导并转换为平台类型。
- Compiler 和 Runner 使用唯一无状态 `DeriveColumnsNodeOperator`。

节点类别为 `PROCESSOR`。图规则为至少一条入边和一条出边；多个上游表 Map 先执行无覆盖合并。

## 2. 稳定配置协议

### 2.1 节点配置

```ts
interface DeriveColumnsConfiguration {
  globalDerivations: ColumnDerivation[];
  operations: DeriveColumnsOperation[];
}

interface DeriveColumnsOperation {
  operationId: string;
  sourceTableName: string;
  output: ProcessorOutput;
  derivations: ColumnDerivation[];
}

interface ColumnDerivation {
  targetColumnName: string;
  expression: CanvasExpression;
}
```

### 2.2 表达式 AST

```ts
type CanvasExpression =
  | ColumnExpression
  | LiteralExpression
  | RuntimeValueExpression
  | BinaryExpression
  | FunctionExpression
  | CaseWhenExpression;

interface ColumnExpression {
  kind: 'COLUMN';
  columnName: string;
}

interface LiteralExpression {
  kind: 'LITERAL';
  literal: CanvasLiteral;
}

interface RuntimeValueExpression {
  kind: 'RUNTIME_VALUE';
  value: 'EXECUTION_ID' | 'EXECUTION_STARTED_AT';
}

interface BinaryExpression {
  kind: 'BINARY';
  operator: 'ADD' | 'SUBTRACT' | 'MULTIPLY' | 'DIVIDE' | 'MODULO';
  left: CanvasExpression;
  right: CanvasExpression;
}

interface FunctionExpression {
  kind: 'FUNCTION';
  function:
    | 'TRIM' | 'LTRIM' | 'RTRIM'
    | 'LOWER' | 'UPPER' | 'REPLACE' | 'SUBSTRING'
    | 'COALESCE' | 'CONCAT'
    | 'DATE_FORMAT' | 'DATE_ADD' | 'DATE_SUB';
  arguments: CanvasExpression[];
}

interface CaseWhenExpression {
  kind: 'CASE_WHEN';
  branches: Array<{
    condition: CanvasFilterCondition;
    result: CanvasExpression;
  }>;
  elseExpression: CanvasExpression | null;
}
```

`CanvasLiteral` 和 `CanvasFilterCondition` 必须复用 FILTER 定义的共享稳定契约。即使先实现 DERIVE，也应把它们抽成共享 Canvas 表达式契约，不得复制第二套 Predicate 或 Literal。

`RUNTIME_VALUE` 由 Runner 以当前 Attempt 的可信执行上下文解析为 Spark Literal。Canvas JSON 只保存枚举，
不保存 execution ID、开始时间或任何凭据；`EXECUTION_ID` 输出 STRING，`EXECUTION_STARTED_AT` 输出 TIMESTAMP。

示例：

```json
{
  "globalDerivations": [
    {
      "targetColumnName": "amount_with_tax",
      "expression": {
        "kind": "BINARY",
        "operator": "MULTIPLY",
        "left": { "kind": "COLUMN", "columnName": "amount" },
        "right": {
          "kind": "LITERAL",
          "literal": { "dataType": "DECIMAL", "value": "1.06" }
        }
      }
    }
  ],
  "operations": [
    {
      "operationId": "ad71b64a-0f0d-4dd0-87ad-a64b3302470f",
      "sourceTableName": "orders",
      "output": { "mode": "CREATE_NEW_TABLE", "outputTableName": "orders_enriched" },
      "derivations": []
    }
  ]
}
```

### 2.3 求值规则

- 同一节点内所有表达式只能引用进入节点时的原始来源 Schema。全局规则必须对每张已选表均有效；全局与表级规则写入同一目标字段时拒绝配置。
- 后一项不得引用本节点前一项刚创建或覆盖的结果，避免数组顺序形成隐藏依赖。
- 目标字段存在时自动覆盖并保留原字段位置；不存在时自动新增，按全局规则后接本表规则的有效顺序追加。
- 函数参数数量与含义是稳定协议的一部分，实施时在正式协议逐项列明。
- 首期不保存 SQL、Spark Column JSON、Java 类名或任意函数名。

## 3. 输入、输出与 Schema 传播

Operator 对每个 `operations` 项查找 `sourceTableName` 后，基于该表的原始 Dataset 构造一次最终 `select`。该表有效规则固定为 `globalDerivations + operation.derivations`：

- 未覆盖字段直接投影。
- 覆盖字段在原位置使用派生表达式 alias。
- 新字段在尾部按配置顺序追加。

一次性投影可以保证所有表达式只看到原始来源列，并避免 `withColumn` 带来的顺序依赖。

Map 规则：

- `REPLACE_SOURCE` 替换对应输入 Map Key；`CREATE_NEW_TABLE` 才以该项 `outputTableName` 添加派生结果。
- 新表输出名与任一现有 Key 冲突时报 `DUPLICATE_TABLE_NAME`。
- 全局规则必须先通过全部已选表的预检；任一表失败时不生成任何处理结果。

Schema 规则：

- 未改变字段完整继承原平台元数据。
- 覆盖字段的类型与 nullable 取 Analyzer 结果；清空不再可靠的 default、autoIncrement、generated 等物理写入属性。
- 新字段类型与 nullable 取 Analyzer 结果，comment/default 等未配置元数据为空。
- `origin` 和 `datasetKind` 继承来源表。
- `eventTimeColumn` 与 `watermarkDelay` 默认继承。
- `STREAMING` 模式禁止覆盖当前事件时间字段；新增普通字段不改变事件时间语义。

## 4. 校验与稳定错误码

复用：

- `CONFIGURATION_REQUIRED`
- `REQUIRED_CONFIGURATION`
- `TABLE_NOT_FOUND`
- `COLUMN_NOT_FOUND`
- `DUPLICATE_TABLE_NAME`
- `DUPLICATE_COLUMN_NAME`
- `SPARK_ANALYSIS_ERROR`
- `NODE_EXECUTION_MODE_NOT_SUPPORTED`
- `UPSTREAM_INVALID`

新增：

| 错误码 | 条件 |
| --- | --- |
| `EMPTY_DERIVATIONS` | 没有配置派生字段 |
| `DUPLICATE_DERIVATION_TARGET` | 同一目标字段配置多次 |
| `INVALID_DERIVATION_EXPRESSION` | AST 结构、深度、节点数或 kind 无效 |
| `UNSUPPORTED_EXPRESSION_FUNCTION` | 函数不在首期白名单 |
| `INVALID_FUNCTION_ARGUMENTS` | 函数参数数量或必需结构错误 |
| `STREAM_EVENT_TIME_COLUMN_IMMUTABLE` | 流模式尝试覆盖事件时间字段 |
| `GLOBAL_DERIVATION_TARGET_CONFLICT` | 全局与本表规则使用同一目标字段 |

表达式类型兼容性只通过实际 Spark 表达式和 Analyzer 判断。服务端需要设置 AST 最大深度、总节点数、CASE 分支数和单节点 derivation 数量上限，并在实施时写入正式协议。

## 5. 前端设计器

- Palette 增加“派生字段”，说明为“通过表达式新增或覆盖字段”。
- Inspector 拆分为 `components/processors/DeriveColumnsProcessorInspector.tsx`。
- 窄 Inspector 只显示全局规则入口和已选处理表；每张表显示全局/本表规则数量及配置按钮。
- 全局和表级规则使用宽 Modal 编辑：左侧维护规则列表，右侧使用结构化表达式编辑器配置当前规则。
- 表级 Modal 顶部配置该表的输出方式及新表名；全局规则在其中只读展示，并可跳转到全局配置。
- 表达式构建器根据 AST 类型展示列、Literal、运算、函数、CASE，不提供自由 SQL 输入框。
- 来源字段候选只来自编译 `inputTables`；派生字段候选不包含本节点其他 derivation。
- 上游字段失效后保留 AST，直接在引用处显示错误。
- 流模式目标字段命中事件时间字段时即时提示，但以后端错误为准。
- 节点摘要只显示处理表数、全局规则数、目标字段和函数类别，不显示 Literal 值。

## 6. Task Engine Operator

新增唯一 `DeriveColumnsNodeOperator`：

- 支持 `BATCH`、`STREAMING`，类别为 `PROCESSOR`。
- 递归将稳定表达式 AST 转换为 Spark `Column`。
- CASE 条件调用 FILTER 的共享 Predicate 转换能力；建议抽取无状态 `CanvasPredicateExpressionBuilder`，它不是第二个节点 Operator。
- 基于原始 Dataset 构造单次 `select + alias`。
- Analyzer 成功后从最终 `StructType` 生成平台 Schema；不在协议暴露 Spark 类型。
- Analyzer 拒绝时返回 `SPARK_ANALYSIS_ERROR`。
- 成功后按每项 `ProcessorOutput` 替换来源 Map Key 或新增输出表。

安全摘要仅记录表名、目标字段名、派生数量、表达式 kind 和函数名集合；禁止记录 Literal 值、生成 SQL 或数据行。

## 7. 分阶段实施

### 阶段 1：共享表达式契约

- 确认或新增共享 `CanvasLiteral`、`CanvasFilterCondition`。
- 新增 Derive 表达式 sealed 联合、函数/运算枚举、配置和节点定义。
- 实施时将当时 Canvas minor version 增加 1，低版本携带该节点返回 `NODE_TYPE_REQUIRES_SCHEMA_VERSION`。
- 更新业务层定义、Upgrader、Validator、Manifest 和 sealed switch。

### 阶段 2：统一 Operator

- 实现 AST 结构校验和 Spark 表达式构造。
- 实现 `DeriveColumnsNodeOperator` 并注册。
- 更新图度数、模式能力、批流 Runner、安全摘要和节点结果消息。

### 阶段 3：前端

- 更新 TypeScript 类型、默认值、JSON IO、Registry、Ports 和 Palette。
- 实现独立 Inspector 与结构化表达式编辑器。
- 接入编译输入 Schema、错误路径、节点摘要和未应用配置保护。

### 阶段 4：正式文档

- 实现后更新 `canvas-task-definition.md` 的版本、AST、函数参数表和示例。
- 同步安全日志与 Task Engine 节点说明。

## 8. 行为验收标准

- 五类表达式 AST 可稳定导入、编辑、导出并保持语义一致。
- 多个 derivation 始终只引用原始来源 Schema。
- 覆盖字段保留位置，新增字段按配置顺序追加。
- 目标字段已存在时自动覆盖并保持位置；不存在时自动追加。
- Spark Analyzer 正确推导输出平台类型；协议中不出现 Spark 类型。
- 流模式不能覆盖事件时间字段。
- 输出 Map 保留来源和其他上游表，并新增输出表。
- 日志、错误和节点摘要不泄露 Literal 实际值。

## 9. 不在范围内

- 自由 SQL、JavaScript、Python、UDF/UDAF 或任意 Spark 函数。
- 本节点 derivation 间的链式引用。
- 窗口函数、聚合函数、explode 和嵌套结构操作。
- 自动解析文本表达式为 AST。
- 流式事件时间字段重定义。

## 10. 协议兼容与实施顺序

推荐先落地 FILTER 的共享 Predicate/Literal 契约，再实现 DERIVE；但 DERIVE 不应在运行时依赖 FILTER 节点存在。新增节点只提升 minor version，旧定义升级后语义不变。只有完整实现后才加入正式 Canvas 节点清单。
