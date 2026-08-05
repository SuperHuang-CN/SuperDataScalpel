# Canvas `AGGREGATE` Processor 开发计划

## 1. 目标与范围

新增批处理 `AGGREGATE` Processor，对一张来源表按零到多个字段分组并计算常用聚合指标。

首期目标：

- 支持 `COUNT`、`SUM`、`AVG`、`MIN`、`MAX`。
- 支持全表聚合、分组聚合和受限的 DISTINCT 聚合。
- 输出列顺序稳定。
- 只支持 `BATCH`。
- Compiler 与 Runner 使用唯一无状态 `AggregateNodeOperator`。

实时聚合涉及窗口、事件时间、Watermark、状态清理和输出模式，未来使用独立 `WINDOW_AGGREGATE`，不得扩张本节点配置。

节点类别为 `PROCESSOR`。图规则为恰好一条入边、至少一条出边。

## 2. 稳定配置协议

```ts
interface AggregateConfiguration {
  sourceTableName: string;
  outputTableName: string;
  groupByColumns: string[];
  aggregations: AggregateItem[];
}

interface AggregateItem {
  function: 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX';
  sourceColumnName: string | null;
  outputColumnName: string;
  distinct: boolean;
}
```

示例：

```json
{
  "sourceTableName": "orders",
  "outputTableName": "customer_order_metrics",
  "groupByColumns": ["customer_id"],
  "aggregations": [
    {
      "function": "COUNT",
      "sourceColumnName": null,
      "outputColumnName": "order_count",
      "distinct": false
    },
    {
      "function": "SUM",
      "sourceColumnName": "amount",
      "outputColumnName": "total_amount",
      "distinct": false
    }
  ]
}
```

协议规则：

- `groupByColumns=[]` 表示对整张表做单组聚合。
- `aggregations` 至少包含一项。
- `COUNT(*)` 固定表示为 `function=COUNT`、`sourceColumnName=null`、`distinct=false`。
- 除 `COUNT(*)` 外，所有聚合项必须指定 `sourceColumnName`。
- `COUNT(DISTINCT column)`、`SUM(DISTINCT column)`、`AVG(DISTINCT column)` 可用。
- `MIN`、`MAX` 不允许 `distinct=true`。
- 每个 `outputColumnName` 必填且全局唯一，不能与分组输出字段同名。
- 输出顺序固定为所有 `groupByColumns`，随后按 `aggregations` 配置顺序排列指标字段。

## 3. 输入、输出与 Schema 传播

Operator 查找 `sourceTableName`，构造 Spark `groupBy(...).agg(...)`；空分组使用全局聚合。

Map 规则：

- 保留输入 Map 中所有表。
- 以 `outputTableName` 添加聚合结果。
- 输出名与任一现有 Key 冲突时报 `DUPLICATE_TABLE_NAME`，不替换来源表。

输出 Schema：

- 字段顺序按协议固定。
- 类型和 nullable 以聚合表达式的 Spark Analyzer 结果为准，不另写数值/比较类型兼容矩阵。
- 分组字段可继承来源 comment 等展示元数据，但 default、autoIncrement、generated 等物理写入属性清空。
- 聚合字段不继承来源物理字段属性。
- `datasetKind` 固定为 `BOUNDED`。
- 聚合属于结构重塑，`origin` 固定为 `null`，不得暗示聚合结果仍是一张可直接回溯的物理来源表。
- `eventTimeColumn` 和 `watermarkDelay` 固定清空。

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
| `EMPTY_AGGREGATIONS` | 没有聚合项 |
| `DUPLICATE_GROUP_BY_COLUMN` | 分组字段重复 |
| `DUPLICATE_AGGREGATE_OUTPUT_COLUMN` | 聚合输出名重复 |
| `AGGREGATE_OUTPUT_COLUMN_CONFLICT` | 聚合输出名与分组字段同名 |
| `INVALID_AGGREGATE_FUNCTION` | function 未知 |
| `AGGREGATE_SOURCE_COLUMN_REQUIRED` | 非 COUNT(*) 未指定来源字段 |
| `INVALID_COUNT_STAR_CONFIGURATION` | COUNT(*) 同时设置 distinct 或其他不合法组合 |
| `AGGREGATE_DISTINCT_NOT_SUPPORTED` | MIN/MAX 配置 distinct |

函数能否作用于来源字段类型由 Spark Analyzer 判断。例如 SUM 用于不可聚合类型时返回 `SPARK_ANALYSIS_ERROR`，平台层不维护重复矩阵。

## 5. 前端设计器

- Palette 的批处理“处理器”分类增加“聚合”，说明为“按字段分组并计算统计指标”；实时模式不展示。
- Inspector 拆分为 `components/processors/AggregateProcessorInspector.tsx`。
- 顶部选择来源表、输出表；分组字段使用可排序多选。
- 聚合项使用紧凑可编辑表格：
  - 聚合函数。
  - 来源字段；COUNT 可选择“全部行（*）”。
  - DISTINCT 开关，仅对允许函数可用。
  - 输出字段名。
- 字段候选取编译 `inputTables`，上游失效时保留配置并标红。
- 展示实时模式不支持的明确原因，不能依赖 Palette 隐藏作为唯一校验。
- 节点摘要显示来源/输出表、分组字段数和聚合项数量。

## 6. Task Engine Operator

新增唯一 `AggregateNodeOperator`：

- 类别为 `PROCESSOR`，`supportedModes()` 仅含 `BATCH`。
- 按配置构造 groupBy 和聚合 Spark Column，并为指标显式 alias。
- 使用来源零行 Dataset 完成 Analyzer 预检，不触发 count、collect 等 Spark Action。
- 以分析后的 `StructType` 构建输出平台 Schema。
- 成功后复制输入 Map 并新增输出表。

节点阶段为 `PROCESS`。安全摘要只记录表名、分组字段名、函数、来源字段名、输出字段名和 distinct 标志，不记录任何分组值或聚合结果。

## 7. 分阶段实施

### 阶段 1：协议

- 新增配置、聚合项、函数枚举、节点定义和 `CanvasNodeType.AGGREGATE`。
- 加入 Java/Jackson/TypeScript 判别联合。
- 实施时将当时 Canvas minor version 增加 1；低版本携带节点必须拒绝。
- 同步业务 Validator、Upgrader、Manifest 和 sealed switch。

### 阶段 2：Operator

- 实现并注册 `AggregateNodeOperator`。
- 更新 `CanvasGraphPlan` 度数与仅批处理能力。
- 接入 Compiler、批 Runner、节点生命周期、安全摘要和结果消息。
- 流 Runner 必须在调用前稳定返回 `NODE_EXECUTION_MODE_NOT_SUPPORTED`。

### 阶段 3：前端

- 更新类型、默认配置、JSON IO、Registry、Ports 和批处理 Palette。
- 实现独立 Inspector、分组字段排序和聚合项编辑。
- 接入编译输入 Schema、错误路径和节点摘要。

### 阶段 4：正式文档

- 实现后更新 `canvas-task-definition.md` 的版本、函数矩阵、Schema 和示例。
- 明确实时聚合由未来 `WINDOW_AGGREGATE` 承担。

## 8. 行为验收标准

- 全局 COUNT(*)、多字段分组和多个聚合项可稳定配置与 JSON 往返。
- 输出字段顺序固定为分组字段后接指标字段。
- COUNT(*)、DISTINCT 和函数组合规则得到稳定校验。
- 聚合输出字段重名及与分组字段冲突时明确失败。
- Analyzer 推导输出类型，Compiler 不读取真实数据。
- 输出为 BOUNDED，origin/event time/watermark 按计划清空。
- 节点在实时任务中稳定拒绝且 Inspector 仍能利用安全输入上下文。

## 9. 不在范围内

- HAVING；使用后续 FILTER 表达。
- 窗口函数、滚动/滑动/会话窗口。
- 自定义聚合函数、百分位数、collect_list 等扩展函数。
- Grouping Sets、Rollup、Cube 和 Pivot。
- 流式状态聚合和输出模式配置。

## 10. 协议兼容

`AGGREGATE` 只在完整实现时提升当时 minor version。后续实时聚合必须新增 `WINDOW_AGGREGATE` 类型，不能给本节点追加 Watermark 或窗口可选字段从而改变既有语义。
