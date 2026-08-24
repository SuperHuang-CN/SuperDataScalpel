# Canvas `SPATIAL_AGGREGATE` Processor 开发文档

## 1. 状态、目标与范围

- 实现状态：已实现并通过统一验收。
- 目标协议版本：Canvas `1.23`。
- 节点类型：`SPATIAL_AGGREGATE`。
- 节点类别：`PROCESSOR`。
- 执行模式：仅 `BATCH`。
- 图规则：至少一条入边和一条出边；多个上游表 Map 先执行无覆盖合并。

`SPATIAL_AGGREGATE` 对一张有界逻辑表按普通标量字段分组，并对 Geometry 执行空间聚合。
首版提供 `UNION`、`INTERSECTION`、`COLLECT`、`ENVELOPE` 四种固定 Sedona 聚合，不接受
任意 SQL 或表达式。

节点适用于行政区 dissolve、轨迹/要素集合汇总、组内公共区域和空间范围框生成。它与普通
`AGGREGATE` 分离，避免把 Geometry 特例和空间元数据传播塞入标量聚合协议。

## 2. 稳定配置协议

```ts
interface SpatialAggregateConfiguration {
  sourceTableName: string;
  outputTableName: string;
  groupByColumns: string[];
  aggregations: SpatialAggregation[];
}

type SpatialAggregationKind =
  | 'UNION'
  | 'INTERSECTION'
  | 'COLLECT'
  | 'ENVELOPE';

interface SpatialAggregation {
  kind: SpatialAggregationKind;
  geometryColumnName: string;
  outputColumnName: string;
}
```

示例：

```json
{
  "sourceTableName": "parcels",
  "outputTableName": "district_geometry",
  "groupByColumns": ["district_code"],
  "aggregations": [
    {
      "kind": "UNION",
      "geometryColumnName": "boundary",
      "outputColumnName": "district_boundary"
    },
    {
      "kind": "ENVELOPE",
      "geometryColumnName": "boundary",
      "outputColumnName": "district_envelope"
    }
  ]
}
```

协议限制：

- 来源表、输出表、`groupByColumns` 数组和 `aggregations` 数组必须存在。
- `groupByColumns` 可以为空，表示全局聚合；字段不能重复，也不能是 Geometry。
- 聚合项数量固定为 `1..32`，按配置顺序执行和输出。
- 每项的 kind、Geometry 字段和输出字段名必填。
- 聚合来源必须是带完整空间定义的平台 `GEOMETRY`，首版只支持 `EPSG + XY`。
- 输出字段名之间不能重复，也不能与任一分组字段同名。
- 不同聚合项可以使用不同 Geometry 字段和不同 CRS；每个输出独立继承自身来源定义。
- 配置不保存 `DISTINCT`、过滤条件、SQL、Geometry 值或聚合实现参数。

## 3. 聚合函数与 NULL/Empty 语义

| kind | Sedona API | 语义 |
| --- | --- | --- |
| `UNION` | `ST_Union_Agg` | 合并组内 Geometry 覆盖范围 |
| `INTERSECTION` | `ST_Intersection_Agg` | 计算组内所有 Geometry 的公共部分 |
| `COLLECT` | `ST_Collect_Agg` | 收集组内 Geometry，不执行拓扑融合 |
| `ENVELOPE` | `ST_Envelope_Agg` | 计算组内全部非空 Geometry 的总包络 |

四种聚合均忽略 NULL；一个组中全部值为 NULL 时输出 NULL。Empty 不是 NULL，按函数定义处理：

- `UNION`：Empty 作为 union 恒等输入参与；全 Empty 组输出 Empty Geometry。
- `INTERSECTION`：Empty 作为交集吸收输入参与，组内存在 Empty 时结果为 Empty Geometry。
- `COLLECT`：Empty 作为集合成员交给 Sedona/JTS，不预先删除。
- `ENVELOPE`：忽略 Empty；组内只有 NULL/Empty 时输出 NULL。

所有非 NULL 结果使用 `ST_SetSRID` 显式恢复该聚合项来源字段的 EPSG code。实际结果可能因
输入形态发生升维或降维，因此四种 kind 的稳定输出 GeometryKind 均声明为通用
`GEOMETRY`，包括可能返回 Point/LineString 的退化 Envelope。

非法 Geometry 或拓扑计算失败时任务失败，不置 NULL、不跳过错误行。Compiler 只构造零行
计划，不读取真实值，也不判断组大小、有效性或实际输出 kind。

## 4. 分组、Map 与 Schema

分组语义：

- `groupByColumns=[]` 时执行全局聚合；空来源表产生一行，所有聚合结果为 NULL。
- 配置分组字段时使用 Spark 标准 `groupBy`；空来源表产生零行。
- 不承诺组间或组内顺序，不提供排序语义。

Map 规则：

- 保留输入 Map 中全部表。
- 以 `outputTableName` 追加聚合结果表。
- 输出表名与任一输入表同名时返回 `DUPLICATE_TABLE_NAME`。

Schema 顺序固定为：全部分组字段（按配置顺序）→ 全部空间聚合字段（按配置顺序）。

- 分组字段保留来源平台类型、参数、nullable 和 comment，清空物理 origin/default/generated。
- 聚合字段平台类型为 `GEOMETRY`、kind 为 `GEOMETRY`、nullable 固定为 true。
- 每个聚合字段独立继承其来源 Geometry 的 CRS 和 dimension。
- 输出表 `origin=null`、`datasetKind=BOUNDED`，事件时间和 Watermark 清空。

节点只接受 BOUNDED 来源。空间聚合是有界的批处理 shuffle，不在首版支持流式状态、
Watermark 或增量输出。

## 5. 校验与稳定错误码

复用必填项、表不存在、字段不存在、重复表名、执行模式、Geometry 定义、CRS、dimension、
普通分组字段和 Spark Analyzer 错误。新增：

| 错误码 | 阶段 | 条件 |
| --- | --- | --- |
| `SPATIAL_AGGREGATE_REQUIRES_BOUNDED_INPUT` | Compiler | 来源不是 BOUNDED |
| `EMPTY_SPATIAL_AGGREGATIONS` | Compiler | aggregations 为空 |
| `SPATIAL_AGGREGATION_LIMIT_EXCEEDED` | Compiler | 聚合项超过 32 个 |
| `INVALID_SPATIAL_AGGREGATION_KIND` | Compiler | 聚合 kind 缺失或未知 |
| `DUPLICATE_SPATIAL_AGGREGATE_OUTPUT_COLUMN` | Compiler | 空间聚合输出字段名重复 |
| `SPATIAL_AGGREGATE_OUTPUT_COLUMN_CONFLICT` | Compiler | 输出字段与分组字段同名 |
| `SPATIAL_AGGREGATE_FAILED` | Runner | 真实 Geometry 空间聚合失败 |

错误路径必须定位到具体分组字段或聚合项。真实值导致的拓扑错误只在 Runner 分类，不把
Geometry、坐标、WKT/WKB 或数据行放入错误消息。

## 6. 前端设计器

- Palette 名称为“空间聚合”，说明为“按字段分组并汇总 Geometry”。
- 位于“空间处理”分组，在空间裁剪之后、空间 Join 之前。
- Inspector 依次配置来源/输出表、可排序的分组字段和可排序的聚合项。
- 分组字段候选排除 Geometry；聚合字段候选只显示 Geometry。
- 聚合项用紧凑卡片编辑 kind、来源字段和输出字段，支持增删与上下移动，最多 32 项。
- 每项展示所选字段的 kind、CRS、dimension；不同项 CRS 不一致时不报错，并提示各输出独立
  继承。
- 上游变化导致选项失效时保留原值并标红。
- 面板提示“全局聚合”“NULL/Empty 行为”“输出顺序”和“仅批处理”。
- 节点摘要显示来源表、分组字段数量、聚合类型列表、聚合项数量和输出表；不显示 Geometry
  或聚合结果。

## 7. Task Engine、Runner 与安全边界

新增唯一无状态配置 Operator `SpatialAggregateNodeOperator`。它在 Spark 计划中构造有界
shuffle 聚合，Compiler 与 Runner 共享同一实现。节点阶段为 `PROCESS`，成功消息为
“空间聚合已准备”。

安全摘要只记录表名、分组字段名、Geometry 字段名、聚合 kind、输出字段名、CRS 和规则数量。
不得记录 Geometry、坐标、组大小、结果范围、面积、WKT/WKB 或数据行；成功日志不得为了统计
组数额外触发 Spark Action。

## 8. 验收标准

- 两个配置类型和四种 kind 能够严格 JSON 往返，并受 Canvas `1.23` 门槛约束。
- 全局和分组聚合计划正确，字段顺序稳定，聚合项上限有效。
- 四种函数普通值、NULL、Empty、全 NULL/Empty 的真实 Spark 行为符合本文档。
- 每个输出独立传播来源 CRS/dimension，通用 GeometryKind 和 nullable 正确。
- BOUNDED 限制、字段类型、重复分组、输出名冲突和上游失效校验准确。
- Registry、图规则、Inspector、摘要、生命周期和失败分类完整接入。
- 日志、错误和结果不包含空间数据值。

## 9. 不在范围内

- 流式空间聚合、窗口、Watermark、增量更新和状态清理。
- 标量聚合混用、过滤聚合、DISTINCT、HAVING 或任意表达式。
- `CENTROID`、`CONVEX_HULL`、`CONCAVE_HULL`、cluster 或轨迹构造。
- 自动修复、自动 CRS 转换、跨字段 CRS 统一或输出 kind 强制转换。
- 聚合内存参数、分区数、顺序保证或大组拆分策略。
