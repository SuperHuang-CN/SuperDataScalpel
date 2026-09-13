# Canvas Describe Dataset Processor 设计

## 1. 定位与官方边界

`SPATIAL_DESCRIBE_DATASET` 对齐 ArcGIS Enterprise 11.3 GeoAnalytics Server
[Describe Dataset](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/describe-dataset/)
的核心剖析能力：返回字段统计、数据集描述，以及可选的样本和空间范围结果。

DataScalpel 的输入是 Canvas 逻辑表而不是 Feature Service。一个逻辑表可能包含多个 Geometry，
也可能完全不包含 Geometry，因此空间范围使用显式 Geometry 字段，不隐式猜测。CRS 转换继续由上游
`SPATIAL_TRANSFORM` 完成；本节点不修改来源数据、Geometry 或时间字段。

首版只支持有界批处理，不新增外部服务、生产依赖、HTTP API、Manifest 或任务结果协议。

## 2. Canvas 4.76 配置

```ts
interface SpatialDescribeDatasetConfiguration {
  sourceTableName: string;
  geometryColumnName: string;
  statisticsTableName: string;
  descriptionTableName: string;
  sampleSize: number;
  sampleTableName: string;
  extentOutput: boolean;
  extentTableName: string;
}
```

- `sourceTableName`、`statisticsTableName`、`descriptionTableName` 必填。
- `geometryColumnName` 可空；配置后必须引用来源表中的 Geometry 字段。
- `sampleSize` 为 `0..10000`。`0` 表示不生成样本表；大于 0 时 `sampleTableName` 必填。
- `extentOutput=true` 时 Geometry 和 `extentTableName` 必填；没有 Geometry 的非空间数据集仍可做字段剖析。
- 所有结果表名必须大小写不敏感唯一，且不得占用任意入口逻辑表名。
- 空值允许作为未完成草稿保存；Compiler 是业务配置的权威校验边界。

节点只支持 `BATCH`，输入必须为 `BOUNDED`。入口 Map 保持原顺序，输出按“字段统计、数据集描述、
样本、范围”的固定顺序追加。所有输出均为 `BOUNDED`，不传播事件时间或 Watermark；只有样本表保留
来源 Schema、事件时间字段和 Watermark 元数据。

## 3. 字段统计表

每个普通字段输出一行，Geometry 和 Binary 不进入字段统计。字段行严格沿用来源 Schema 顺序，固定字段为：

| 字段 | 类型 | 语义 |
| --- | --- | --- |
| `field_name` | STRING | 来源字段名 |
| `field_type` | STRING | 平台字段类型 |
| `non_null_count` | LONG | 非空数量 |
| `null_count` | LONG | NULL 数量 |
| `any_value` | STRING | STRING/BOOLEAN 的确定性代表值；取最小非空字符串 |
| `numeric_sum` | DOUBLE | 数值字段 Sum |
| `numeric_mean` | DOUBLE | 数值字段 Mean |
| `numeric_minimum` | DOUBLE | 数值字段 Min |
| `numeric_maximum` | DOUBLE | 数值字段 Max |
| `numeric_range` | DOUBLE | Max - Min |
| `numeric_standard_deviation` | DOUBLE | 总体标准差 `stddev_pop` |
| `numeric_variance` | DOUBLE | 总体方差 `var_pop` |
| `temporal_minimum` | STRING | DATE/TIMESTAMP/TIMESTAMP_NTZ 最小值的 Spark 稳定字符串表示 |
| `temporal_maximum` | STRING | DATE/TIMESTAMP/TIMESTAMP_NTZ 最大值的 Spark 稳定字符串表示 |
| `temporal_range_millis` | LONG | 最大、最小时间差的毫秒数 |

不适用的统计输出 NULL。数值统一转为 DOUBLE 后汇总，这是 DataScalpel 的稳定结果 Schema 裁决，
不承诺保留超出 DOUBLE 精度的 DECIMAL 尾数。`any_value` 使用确定性的最小非空字符串，避免
Spark 分区与输入顺序改变结果；它对齐 ArcGIS 的 Any 能力，但不是对 ArcGIS 内部任意值选择算法的猜测。

空来源表仍为每个可统计字段输出一行，计数为 0，其余统计为 NULL。Compiler 仅构造包含全部统计表达式的
单个惰性聚合计划，不扫描数据。

## 4. 数据集描述表

描述表始终输出一行，固定字段为：

- 数据集：`dataset_name`、`record_count`、`field_count`。
- Geometry：`geometry_column_name`、`geometry_kind`、`geometry_crs`、`geometry_dimension`、
  `geometry_non_empty_count`、`geometry_null_or_empty_count`、`extent_x_minimum`、`extent_y_minimum`、
  `extent_x_maximum`、`extent_y_maximum`。
- 时间：`event_time_column_name`、`event_time_non_null_count`、`event_time_null_count`、
  `event_time_minimum`、`event_time_maximum`、`event_time_range_millis`。
- `description_json`：以上字段组成的结构化 JSON，供文件输出、审计和后续自动化消费。

未配置 Geometry 或来源没有事件时间元数据时，对应名称、类型、范围和计数均为 NULL。Geometry 为 NULL
或 Empty 的记录统一计入 `geometry_null_or_empty_count`；空间范围只聚合非空且非 Empty 的 Geometry。
事件时间范围只读取 `CanvasTableSchema.eventTimeColumn`，不把其他 DATE/TIMESTAMP 字段猜成数据集时间。

空来源表仍输出一行，`record_count=0`，范围为空。描述 JSON 不包含样本值、Geometry 坐标数组或完整数据行。

## 5. 可选样本和范围

### 样本表

- `sampleSize > 0` 时使用 Spark `limit(sampleSize)` 生成。
- 完整保留来源字段顺序、类型、Geometry 定义、事件时间与 Watermark 元数据。
- Spark 的 `limit` 在输入重分区、文件切分或来源物理顺序变化后不保证返回相同记录；本节点不增加隐藏排序、
  随机种子或全表 ID 要求。需要可重复样本时，应先用其他 Processor 建立确定次序。

### 范围表

- `extentOutput=true` 时输出 0 或 1 行，字段为 `geometry`。
- 存在至少一个非空非 Empty Geometry 时，输出其 XY Envelope 的单个 Polygon；否则输出空表。
- Polygon 继承所选 Geometry 的 CRS，坐标维度固定为 XY；范围不是凸包，也不保留 Z/M。

## 6. Inspector 与 Canvas UI

```text
┌ 描述数据集 ─────────────────────────────┐
│ 来源表          [city_events          ▾] │
│ Geometry（可选）[shape · POLYGON      ▾] │
│                                            │
│ 必选结果                                   │
│ 字段统计表      [city_field_statistics ] │
│ 数据集描述表    [city_description      ] │
│                                            │
│ 可选结果                                   │
│ 样本 [开]  数量 [100]  表名 [city_sample] │
│ 范围 [开]              表名 [city_extent] │
└────────────────────────────────────────────┘
```

- 表和字段候选只来自 Compiler `inputTables`；失效值保留并标红，不自动清空。
- Geometry 仅建议来源表中的 Geometry 字段，同时展示 Kind、CRS 和维度。
- 样本与范围使用紧凑开关；关闭时保留用户已填写的隐藏草稿，重新打开可恢复。
- 主面板说明字段统计的适用类型、样本非确定性和范围为 XY Envelope；长说明放邻近帮助 Popover。
- 普通业务错误不阻止“应用并继续”。
- Canvas 卡片显示“来源 → 2/3/4 个结果”、可统计字段数、样本数量和范围状态；不显示统计结果、样本值、
  Geometry 坐标或 `description_json` 内容。

## 7. 稳定失败和安全摘要

配置错误复用 `REQUIRED_CONFIGURATION`、`TABLE_NOT_FOUND`、`COLUMN_NOT_FOUND`、
`DUPLICATE_TABLE_NAME`、`GEOMETRY_FIELD_OPERATION_UNSUPPORTED` 和 `BOUNDED_INPUT_REQUIRED`，新增：

| 错误码 | 含义 |
| --- | --- |
| `INVALID_DESCRIBE_DATASET_SAMPLE_SIZE` | 样本数量不在 0..10000 |
| `DESCRIBE_DATASET_SAMPLE_TABLE_REQUIRED` | 已启用样本但未设置样本表名 |
| `DESCRIBE_DATASET_EXTENT_GEOMETRY_REQUIRED` | 已启用范围但未选择 Geometry |
| `DESCRIBE_DATASET_EXTENT_TABLE_REQUIRED` | 已启用范围但未设置范围表名 |

Spark/Sedona 实际扫描、统计或范围构造失败由 Runner 归为 `SPATIAL_DESCRIBE_DATASET_FAILED`，阶段为
`PROCESS`。安全摘要只记录来源表、Geometry 字段、输出表名、样本数量和范围开关；节点日志、Result 和
异常摘要不得记录统计值、样本数据、范围坐标或描述 JSON。

## 8. 验收边界

已完成的本地证据：

- 标量、日期时间、NULL、空表、Geometry Empty、可选样本与范围的实际 Spark 结果；统计字段顺序、
  输出 Map 顺序、样本元数据和范围 CRS/XY 维度均已覆盖。
- Compiler Preview 为字段统计、数据集描述和范围使用零行集合依赖计划，样本保持直接投影；四张结果表
  全部达到 `FIELD_COMPLETE` 且不存在未知来源，分析阶段不提交 Spark Job。
- 20,000 行完成分布式字段统计和数据集描述，样本严格限制为 10,000 行，并输出单个范围 Polygon；
  Engine `SpatialDescribeDatasetNodeOperatorSparkTest` 5 项通过。
- 真实页面已验证必选结果、样本/范围开关、隐藏字段草稿、无效配置应用和紧凑问题详情，且修复了
  开关关闭时未注册字段导致状态无法写入 Form 的问题；验收定义未保存。

仍待与 ArcGIS Enterprise 11.3 对照字段统计、范围和样本能力，以及真实上游字段选择和生产容量。
确定性 Any、DOUBLE 聚合和 Canvas 多 Geometry 适配均不描述成 Esri 内部实现；路线图勾选只表示
DataScalpel 本地实现已完成收口。
