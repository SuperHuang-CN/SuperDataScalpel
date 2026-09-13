# Canvas 节点索引

当前覆盖 55 种 CanvasNodeType，对照 Canvas 4.47 的 Contracts 与 Engine Operator。先读[Canvas 共用规则](../task-canvas.md)，按模式和用途选择本次实际需要的节点手册；无需加载全部文件。运行时以 MCP 最新契约和预校验结果为准，协议允许保存的草稿形态不等于 Engine 已支持执行。

类别决定图度数，逻辑表 Map 决定实际取表；一条边可以传多张表。节点手册的 JSON 都是 configuration 片段，须放入具有稳定 ID、type 和连线的完整定义。以下模式来自当前 Operator，不凭名称推断。

## 输入

| 节点编码 | 中文用途 | 类别 | 支持模式 | 手册 |
| --- | --- | --- | --- | --- |
| `MODEL_INPUT` | 模型输入 | INPUT | BATCH | [model-input](model-input.md) |
| `JDBC_INPUT` | JDBC 表输入 | INPUT | BATCH / STREAMING | [jdbc-input](jdbc-input.md) |
| `JDBC_INCREMENTAL_INPUT` | JDBC 时间游标增量输入 | INPUT | STREAMING | [jdbc-incremental-input](jdbc-incremental-input.md) |
| `JDBC_QUERY_INPUT` | JDBC 查询输入 | INPUT | BATCH / STREAMING | [jdbc-query-input](jdbc-query-input.md) |
| `FILE_DATASET_INPUT` | 文件数据集输入 | INPUT | BATCH | [file-dataset-input](file-dataset-input.md) |
| `HTTP_API_INPUT` | HTTP API 资源输入 | INPUT | BATCH | [http-api-input](http-api-input.md) |
| `SPATIAL_SERVICE_INPUT` | 空间服务资源输入 | INPUT | BATCH | [spatial-service-input](spatial-service-input.md) |
| `KAFKA_INPUT` | Kafka 输入 | INPUT | STREAMING | [kafka-input](kafka-input.md) |
| `TDENGINE_TMQ_INPUT` | TDengine TMQ 输入 | INPUT | STREAMING | [tdengine-tmq-input](tdengine-tmq-input.md) |

## 通用转换

| 节点编码 | 中文用途 | 类别 | 支持模式 | 手册 |
| --- | --- | --- | --- | --- |
| `JOIN` | 批处理关联 | PROCESSOR | BATCH | [join](join.md) |
| `RENAME` | 表与字段重命名 | PROCESSOR | BATCH / STREAMING | [rename](rename.md) |
| `FILTER` | 行筛选 | PROCESSOR | BATCH / STREAMING | [filter](filter.md) |
| `SQL_TRANSFORM` | Spark SQL 转换 | PROCESSOR | BATCH | [sql-transform](sql-transform.md) |
| `SELECT_COLUMNS` | 字段选择 | PROCESSOR | BATCH / STREAMING | [select-columns](select-columns.md) |
| `DERIVE_COLUMNS` | 派生字段 | PROCESSOR | BATCH / STREAMING | [derive-columns](derive-columns.md) |
| `TYPE_CAST` | 类型转换 | PROCESSOR | BATCH / STREAMING | [type-cast](type-cast.md) |
| `AGGREGATE` | 分组聚合 | PROCESSOR | BATCH | [aggregate](aggregate.md) |
| `UNION` | 纵向合并 | PROCESSOR | BATCH / STREAMING | [union](union.md) |
| `DEDUPLICATE` | 去重 | PROCESSOR | BATCH | [deduplicate](deduplicate.md) |
| `NULL_HANDLING` | 空值处理 | PROCESSOR | BATCH / STREAMING | [null-handling](null-handling.md) |
| `VALUE_MAPPING` | 值映射 | PROCESSOR | BATCH / STREAMING | [value-mapping](value-mapping.md) |
| `MASK_FIELDS` | 字段脱敏 | PROCESSOR | BATCH / STREAMING | [mask-fields](mask-fields.md) |
| `JSON_EXTRACT` | JSON 字段提取 | PROCESSOR | BATCH / STREAMING | [json-extract](json-extract.md) |
| `WINDOW` | 批处理窗口函数 | PROCESSOR | BATCH | [window](window.md) |
| `TOP_N` | 分组前 N 行 | PROCESSOR | BATCH | [top-n](top-n.md) |

## 流式处理

| 节点编码 | 中文用途 | 类别 | 支持模式 | 手册 |
| --- | --- | --- | --- | --- |
| `STREAM_JOIN` | 流与静态表关联 | PROCESSOR | STREAMING | [stream-join](stream-join.md) |

## 空间/轨迹

| 节点编码 | 中文用途 | 类别 | 支持模式 | 手册 |
| --- | --- | --- | --- | --- |
| `GEOMETRY_CONSTRUCT` | 构造几何 | PROCESSOR | BATCH / STREAMING | [geometry-construct](geometry-construct.md) |
| `SPATIAL_TRANSFORM` | 坐标转换 | PROCESSOR | BATCH | [spatial-transform](spatial-transform.md) |
| `GEOMETRY_VALIDATE` | 几何有效性标记 | PROCESSOR | BATCH / STREAMING | [geometry-validate](geometry-validate.md) |
| `GEOMETRY_REPAIR` | 几何修复 | PROCESSOR | BATCH / STREAMING | [geometry-repair](geometry-repair.md) |
| `GEOMETRY_DERIVE` | 几何派生 | PROCESSOR | BATCH / STREAMING | [geometry-derive](geometry-derive.md) |
| `GEOMETRY_SIMPLIFY` | 几何简化 | PROCESSOR | BATCH / STREAMING | [geometry-simplify](geometry-simplify.md) |
| `SPATIAL_NEAREST` | 最近要素 | PROCESSOR | BATCH | [spatial-nearest](spatial-nearest.md) |
| `SPATIAL_SUMMARIZE_WITHIN` | 范围内汇总 | PROCESSOR | BATCH | [spatial-summarize-within](spatial-summarize-within.md) |
| `SPATIAL_OVERLAY` | 空间叠加 | PROCESSOR | BATCH | [spatial-overlay](spatial-overlay.md) |
| `TRACK_RECONSTRUCT` | 轨迹重建 | PROCESSOR | BATCH | [track-reconstruct](track-reconstruct.md) |
| `TRACK_MOTION_STATISTICS` | 轨迹运动统计 | PROCESSOR | BATCH | [track-motion-statistics](track-motion-statistics.md) |
| `TRACK_FIND_DWELL` | 轨迹驻留识别 | PROCESSOR | BATCH | [track-find-dwell](track-find-dwell.md) |
| `TRACK_DETECT_INCIDENTS` | 轨迹事件检测 | PROCESSOR | BATCH | [track-detect-incidents](track-detect-incidents.md) |
| `SPATIAL_BIN_AGGREGATE` | 空间格网聚合 | PROCESSOR | BATCH | [spatial-bin-aggregate](spatial-bin-aggregate.md) |
| `SPATIAL_POINT_CLUSTER` | 空间点聚类 | PROCESSOR | BATCH | [spatial-point-cluster](spatial-point-cluster.md) |
| `SPATIAL_CENTER_DISPERSION` | 空间中心与离散分析 | PROCESSOR | BATCH | [spatial-center-dispersion](spatial-center-dispersion.md) |
| `GEOMETRY_BUFFER` | 几何缓冲 | PROCESSOR | BATCH / STREAMING | [geometry-buffer](geometry-buffer.md) |
| `GEOMETRY_EXPLODE` | 几何部件拆分 | PROCESSOR | BATCH / STREAMING | [geometry-explode](geometry-explode.md) |
| `SPATIAL_MEASURE` | 空间量测 | PROCESSOR | BATCH / STREAMING | [spatial-measure](spatial-measure.md) |
| `GEOMETRY_SERIALIZE` | 几何序列化 | PROCESSOR | BATCH / STREAMING | [geometry-serialize](geometry-serialize.md) |
| `SPATIAL_CLIP` | 空间裁剪 | PROCESSOR | BATCH | [spatial-clip](spatial-clip.md) |
| `SPATIAL_AGGREGATE` | 空间分组聚合 | PROCESSOR | BATCH | [spatial-aggregate](spatial-aggregate.md) |
| `SPATIAL_JOIN` | 空间关联 | PROCESSOR | BATCH | [spatial-join](spatial-join.md) |

## 输出

| 节点编码 | 中文用途 | 类别 | 支持模式 | 手册 |
| --- | --- | --- | --- | --- |
| `MODEL_OUTPUT` | 模型输出 | OUTPUT | BATCH / STREAMING | [model-output](model-output.md) |
| `MODEL_SNAPSHOT_SYNC_OUTPUT` | 模型完整快照同步 | OUTPUT | BATCH | [model-snapshot-sync-output](model-snapshot-sync-output.md) |
| `JDBC_OUTPUT` | JDBC 输出 | OUTPUT | BATCH / STREAMING | [jdbc-output](jdbc-output.md) |
| `JDBC_SNAPSHOT_SYNC_OUTPUT` | JDBC 完整快照同步 | OUTPUT | BATCH | [jdbc-snapshot-sync-output](jdbc-snapshot-sync-output.md) |
| `KAFKA_OUTPUT` | Kafka 输出 | OUTPUT | STREAMING | [kafka-output](kafka-output.md) |
| `FILE_OUTPUT` | 文件输出 | OUTPUT | BATCH | [file-output](file-output.md) |

输入/输出组也包括实时专用节点。JOIN 与 STREAM_JOIN 各有手册；批处理 WINDOW 不能承担流式窗口，MULTI_SCALE 点聚类仍不能通过当前 Engine 预校验。
