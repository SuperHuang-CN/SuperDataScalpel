# Canvas `WINDOW` Processor 设计文档

## 1. 状态、目标与范围

- 设计状态：已实现。
- 正式协议版本：Canvas `1.16`。
- 节点类型：`WINDOW`。
- 节点类别：`PROCESSOR`。
- 执行模式：仅 `BATCH`。
- 图规则：恰好一条入边，至少一条出边。

`WINDOW` 是有序分析窗口节点，在保留来源行的同时追加排名、前后行值和窗口聚合字段。首期支持：

- 排名：`ROW_NUMBER`、`RANK`、`DENSE_RANK`。
- 偏移取值：`LAG`、`LEAD`。
- 窗口聚合：`COUNT`、`SUM`、`AVG`、`MIN`、`MAX`。
- 窗口取值：`FIRST_VALUE`、`LAST_VALUE`。
- 按零到多个字段分区。
- 按一个或多个字段排序。
- 窗口聚合和取值函数使用明确的 `ROWS` Frame。

本节点不是流式事件时间窗口。流式窗口需要事件时间、Watermark、状态保留、触发器和输出模式，未来使用独立 `WINDOW_AGGREGATE` 或其他流式专用节点。

## 2. 稳定配置协议

Java 稳定定义只能位于 `data-scalpel-contracts`。节点配置、函数联合、Frame 和枚举不得在 Business 或 Task Engine 中复制。排序字段复用 `DEDUPLICATE` 已有的稳定 `SortField`。

```ts
interface WindowConfiguration {
  sourceTableName: string;
  outputTableName: string;
  partitionByColumns: string[];
  orderBy: SortField[];
  functions: WindowFunctionItem[];
}

type WindowFunctionItem =
  | RankingWindowFunction
  | OffsetWindowFunction
  | AggregateWindowFunction
  | ValueWindowFunction;

interface RankingWindowFunction {
  kind: 'ROW_NUMBER' | 'RANK' | 'DENSE_RANK';
  outputColumnName: string;
}

interface OffsetWindowFunction {
  kind: 'LAG' | 'LEAD';
  sourceColumnName: string;
  offset: number;
  defaultValue: CanvasLiteral | null;
  outputColumnName: string;
}

interface AggregateWindowFunction {
  kind: 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX';
  sourceColumnName: string | null;
  outputColumnName: string;
  frame: RowsWindowFrame;
}

interface ValueWindowFunction {
  kind: 'FIRST_VALUE' | 'LAST_VALUE';
  sourceColumnName: string;
  ignoreNulls: boolean;
  outputColumnName: string;
  frame: RowsWindowFrame;
}

interface RowsWindowFrame {
  type: 'ROWS';
  start: RowsFrameBoundary;
  end: RowsFrameBoundary;
}

type RowsFrameBoundary =
  | { kind: 'UNBOUNDED_PRECEDING' }
  | { kind: 'PRECEDING'; offset: number }
  | { kind: 'CURRENT_ROW' }
  | { kind: 'FOLLOWING'; offset: number }
  | { kind: 'UNBOUNDED_FOLLOWING' };
```

`SortField` 继续使用：

```ts
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
  "outputTableName": "orders_with_window_metrics",
  "partitionByColumns": [
    "customer_id"
  ],
  "orderBy": [
    {
      "columnName": "created_at",
      "direction": "ASC",
      "nullOrdering": "LAST"
    },
    {
      "columnName": "order_id",
      "direction": "ASC",
      "nullOrdering": "LAST"
    }
  ],
  "functions": [
    {
      "kind": "ROW_NUMBER",
      "outputColumnName": "customer_order_sequence"
    },
    {
      "kind": "LAG",
      "sourceColumnName": "amount",
      "offset": 1,
      "defaultValue": null,
      "outputColumnName": "previous_amount"
    },
    {
      "kind": "SUM",
      "sourceColumnName": "amount",
      "outputColumnName": "running_amount",
      "frame": {
        "type": "ROWS",
        "start": {
          "kind": "UNBOUNDED_PRECEDING"
        },
        "end": {
          "kind": "CURRENT_ROW"
        }
      }
    }
  ]
}
```

## 3. 配置语义

公共规则：

- `sourceTableName`、`outputTableName` 必填。
- `partitionByColumns=[]` 表示全表作为一个分区。
- `orderBy` 至少一项，字段不得重复。
- 分区字段不得重复；分区字段可以同时出现在排序字段中。
- `functions` 至少一项，最多 100 项。
- 输出字段名必填、互不重复，并且不得与来源字段同名。
- 所有函数只引用进入节点时的原始来源字段，不能引用同节点前一项生成的窗口字段。
- 输出字段按 functions 数组顺序追加在全部来源字段之后。

排名函数：

- `ROW_NUMBER` 为每个分区产生连续序号。
- `RANK` 对相同排序键产生相同排名，并在后续排名中保留间隔。
- `DENSE_RANK` 对相同排序键产生相同排名，后续排名不留间隔。
- 排名函数不配置 Frame。

偏移函数：

- `LAG` 读取当前行之前第 `offset` 行。
- `LEAD` 读取当前行之后第 `offset` 行。
- `offset` 范围固定为 `1..10000`。
- `defaultValue=null` 表示越界时返回 typed NULL。
- 非 NULL 默认值必须与来源字段 `PlatformDataType` 一致，并复用稳定 Canvas Literal 格式。
- GEOMETRY 不支持非 NULL 默认 Literal；该类型只能使用 `defaultValue=null`，实际函数可执行性继续由 Spark Analyzer 判断。
- 偏移函数不配置 Frame。

窗口聚合：

- `COUNT(*)` 固定表达为 `kind=COUNT`、`sourceColumnName=null`。
- `COUNT(column)` 和其他函数必须指定来源字段。
- 首期不支持 DISTINCT 窗口聚合。
- 函数和字段类型是否可建立 Spark 计划由 Analyzer 判断。

窗口取值：

- `FIRST_VALUE` 和 `LAST_VALUE` 必须指定来源字段及 `ignoreNulls`。
- 实际首值或末值由显式 Frame 决定，不提供隐藏默认 Frame。
- Inspector 新建 FIRST_VALUE 时可预填 `UNBOUNDED_PRECEDING -> CURRENT_ROW`。
- Inspector 新建 LAST_VALUE 时可预填 `UNBOUNDED_PRECEDING -> UNBOUNDED_FOLLOWING`，避免默认当前行导致结果总是自身。

## 4. `ROWS` Frame 规则

首期只支持基于物理行位置的 `ROWS` Frame，不支持 `RANGE`、时间间隔 Frame 或动态字段边界。

边界顺序从小到大为：

1. `UNBOUNDED_PRECEDING`
2. `PRECEDING(offset)`，offset 越大位置越靠前
3. `CURRENT_ROW`
4. `FOLLOWING(offset)`，offset 越大位置越靠后
5. `UNBOUNDED_FOLLOWING`

约束：

- Frame start 不得为 `UNBOUNDED_FOLLOWING`。
- Frame end 不得为 `UNBOUNDED_PRECEDING`。
- start 必须不晚于 end。
- PRECEDING/FOLLOWING offset 范围为 `1..1000000`。
- Frame 必须完整写入稳定定义，不能依赖 Spark 隐式默认值。
- Frame 仅存在于 AggregateWindowFunction 和 ValueWindowFunction 的判别分支中；排名和偏移配置不得携带无意义的可选 Frame。

## 5. 执行、Map 与 Schema 传播

Operator 执行步骤：

1. 按 `sourceTableName` 取得来源表。
2. 使用 `partitionByColumns` 和 `orderBy` 构建基础 Spark `WindowSpec`。
3. 为每个函数构建 Spark Column；需要 Frame 的函数在基础 WindowSpec 上应用明确 rowsBetween。
4. 以一次最终 `select` 投影全部来源字段，并按配置顺序追加窗口字段。
5. 分析最终 Dataset Schema。

Map 规则：

- 保留输入 Map 中全部表。
- 以 `outputTableName` 追加结果。
- 输出名与任一现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 不修改来源 Map、表或 Dataset。

Schema 规则：

- 来源字段保持原顺序并完整继承元数据。
- 新窗口字段的类型和 nullable 取 Spark Analyzer 结果。
- 新字段的 default、autoIncrement、generated 和 comment 清空。
- `origin` 继承来源表，表示结果仍保持来源行粒度并追加派生字段。
- 只接受 `BOUNDED` 来源，输出 `datasetKind=BOUNDED`。
- 输出 `eventTimeColumn` 和 `watermarkDelay` 清空。

Window 中的 `orderBy` 只定义窗口计算顺序，不构成后续节点或 Sink 的物理输出顺序保证。需要选取排序前 N 行时使用 `TOP_N`，但最终 Sink 仍不应依赖行写入顺序。

## 6. 校验与稳定错误码

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
| `DUPLICATE_WINDOW_PARTITION_COLUMN` | 分区字段重复 |
| `EMPTY_WINDOW_ORDER` | 没有排序字段 |
| `DUPLICATE_WINDOW_SORT_COLUMN` | 排序字段重复 |
| `EMPTY_WINDOW_FUNCTIONS` | 没有窗口函数 |
| `WINDOW_FUNCTION_LIMIT_EXCEEDED` | 函数超过 100 项 |
| `INVALID_WINDOW_FUNCTION` | 函数 kind 未知 |
| `DUPLICATE_WINDOW_OUTPUT_COLUMN` | 窗口输出字段名重复 |
| `WINDOW_OUTPUT_COLUMN_CONFLICT` | 输出字段名与来源字段同名 |
| `WINDOW_SOURCE_COLUMN_REQUIRED` | 需要来源字段的函数未配置字段 |
| `INVALID_WINDOW_COUNT_STAR` | 非法 COUNT(*) 组合 |
| `INVALID_WINDOW_OFFSET` | LAG/LEAD offset 不在允许范围 |
| `INVALID_WINDOW_DEFAULT_LITERAL` | 默认 Literal 结构或格式非法 |
| `WINDOW_DEFAULT_LITERAL_TYPE_MISMATCH` | 默认 Literal 与来源字段类型不一致 |
| `WINDOW_DEFAULT_LITERAL_TYPE_NOT_SUPPORTED` | 来源类型不支持非 NULL 默认 Literal |
| `INVALID_WINDOW_FRAME` | Frame 类型、边界组合或顺序非法 |
| `INVALID_WINDOW_FRAME_OFFSET` | Frame offset 不在允许范围 |
| `WINDOW_REQUIRES_BOUNDED_INPUT` | 来源表为 UNBOUNDED |

节点在实时任务中首先返回 `NODE_EXECUTION_MODE_NOT_SUPPORTED`，同时保留已安全获得的 `inputTables`。如果未来其他批节点能产生异常的 UNBOUNDED Schema，Operator 仍使用 `WINDOW_REQUIRES_BOUNDED_INPUT` 防御，不依赖任务模式代替有界性校验。

## 7. 前端设计器

- Palette 名称为“窗口计算”，说明为“按分区和排序追加排名、前后行值与窗口指标”。
- 仅在批处理模式展示。
- 使用独立 `WindowProcessorInspector`。
- Inspector 分为四个紧凑区域：
  1. 来源表和输出表。
  2. 可排序的分区字段。
  3. 可排序的排序规则，明确方向和 NULL 顺序。
  4. 可排序的函数列表。
- 函数行先选择函数种类，再只展示该判别分支所需字段：
  - 排名：输出字段名。
  - LAG/LEAD：来源字段、offset、可选默认值、输出字段名。
  - 聚合：来源字段或 COUNT(*)、Frame、输出字段名。
  - FIRST/LAST：来源字段、忽略 NULL、Frame、输出字段名。
- Frame 使用“起点/终点”结构化控件；只有 PRECEDING/FOLLOWING 才显示 offset。
- UI 持续提示“排序只用于窗口计算，不保证最终写出顺序”。
- 上游字段失效时保留分区、排序、函数来源字段和 Literal 配置并原位标红。
- 节点摘要显示来源/输出表、分区字段数、排序字段数和函数种类/数量，不显示默认 Literal 实际值。

## 8. Task Engine、日志与失败边界

新增唯一无状态 `WindowNodeOperator`：

- `category()` 返回 `PROCESSOR`。
- `supportedModes()` 只返回 `BATCH`。
- 使用 Spark Window API 和 Column API，不拼接用户 SQL。
- Compiler 使用零行 Dataset 构造和分析计划，不执行 Action。
- Runner 使用相同 Operator 建立实际计划。
- 内部 WindowSpec 和临时表达式不得进入稳定 Schema、Manifest、日志或结果。
- Analyzer 失败返回 `SPARK_ANALYSIS_ERROR`。
- 未分类运行时失败回退为现有 `PROCESSOR_EXECUTION_FAILED`。
- 成功节点结果消息固定为“窗口计算已准备”。

节点生命周期阶段为 `PROCESS`。安全摘要可记录来源/输出表、分区字段名、排序字段名、方向与 NULL 顺序、函数 kind、来源字段、输出字段、offset、Frame 边界和 ignoreNulls；不得记录 defaultValue、实际排名、窗口结果或数据行。

## 9. 实施记录

本节点已按以下范围完成全链路接入：

1. `data-scalpel-contracts` 已增加节点配置、函数判别联合和 Frame 类型；排序字段复用既有 Contracts。
2. `WINDOW` 的最低协议版本固定为 Canvas `1.16`；低版本携带该节点返回 `NODE_TYPE_REQUIRES_SCHEMA_VERSION`。
3. Business Validator、Upgrader、持久化和 Manifest 直接使用 Contracts 类型，没有建立第二套窗口模型。
4. Task Engine 已注册唯一 `WindowNodeOperator`，并接入图度数、仅批模式、有界性、Schema 传播、安全摘要和错误分类。
5. 前端已同步类型、JSON IO、Registry、Ports、Palette、序列化和独立 Inspector。
6. 正式配置、传播语义和错误边界已并入 `canvas-task-definition.md`。

## 10. 行为验收标准

- 排名、LAG/LEAD、窗口聚合和 FIRST/LAST 配置可稳定 JSON 往返。
- 分区、排序和函数数组顺序在导入导出后保持。
- Frame 边界和 offset 的非法组合返回准确路径错误。
- 输出保留全部来源行和字段，并按函数顺序追加新字段。
- 函数类型与 nullable 取 Analyzer 结果，不维护平台函数兼容矩阵。
- Compiler 不读取真实数据，不计算排名或触发 Spark Action。
- 实时任务和 UNBOUNDED 来源均稳定拒绝，并保留可用输入上下文。
- 日志不包含默认 Literal、窗口结果或数据行。

## 11. 测试设计

- Contracts/Jackson：四类函数联合、五类 Frame 边界、数组顺序、`1.16` 版本门槛和旧定义升级。
- Frame 校验：非法起止边界、反向范围、offset 下限/上限和不允许携带 Frame 的函数分支。
- Spark Operator：三种排名、LAG/LEAD、COUNT(*)、窗口聚合、FIRST/LAST 和多函数输出顺序。
- Schema：来源字段完整保留，新字段类型/nullable 取 Analyzer，输出不含内部表达式。
- 有界性与模式：批处理 BOUNDED 成功，实时任务和 UNBOUNDED 来源稳定拒绝。
- Compiler：类型不支持时返回 `SPARK_ANALYSIS_ERROR`，但不读取真实数据或执行 Action。
- 前端：分区/排序/函数排序、判别表单、Frame 编辑、失效字段保留、摘要和 JSON 往返。
- 日志安全：默认 Literal、窗口结果、排名和数据行不进入日志或结果。

## 12. 不在范围内

- 流式事件时间窗口、Watermark、状态 TTL、迟到数据和输出模式。
- `RANGE` Frame、时间 Interval Frame 和动态字段边界。
- DISTINCT 窗口聚合。
- 用户自定义窗口函数、百分位数、NTILE、CUME_DIST 和 PERCENT_RANK。
- 使用同节点新生成字段作为后续函数输入。
- 根据窗口排名删除行；使用 `TOP_N` 或后续 `FILTER`。
- 承诺最终 Sink 中的数据物理顺序。

## 13. 协议兼容

Canvas `1.16` 已正式引入 `WINDOW`。当前协议版本为 `1.22`，兼容读取 `1.0`～`1.22` 并统一规范化为 `1.22`；`1.0`～`1.15` 定义不得携带本节点。未来流式窗口必须新增独立节点类型，不能通过给 `WINDOW` 增加可选 Watermark、事件时间和状态字段改变本批处理协议。

新增窗口函数时，只有其配置能够纳入现有判别联合且不改变旧函数语义，才允许提升 minor version 扩展；需要新的状态、分区或输出语义时应新增节点类型。
