# SPATIAL_POINT_CLUSTER · 空间点聚类

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

有界批处理点聚类配置；对一张 XY Point 表执行 DBSCAN 或 HDBSCAN，保留参与聚类记录的全部来源字段并追加簇 ID、噪声标记及 HDBSCAN 诊断。NULL/Empty 点不参与，结果不继承 Watermark；集群运行需配置共享 spark.checkpoint.dir。MULTI_SCALE 仅能保存草稿，当前不能执行。

## 模式与连线

- 模式：BATCH；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- parameters 用 algorithm 判别：DBSCAN 空间/时空邻域含自身，核心点连接、边界点不桥接；HDBSCAN 不用固定半径和 DBSCAN 时间邻域。
- MULTI_SCALE 当前只能保存草稿并会被预校验拒绝，不能作为本场景“已通过”方案，也不自动替换算法。
- NULL/Empty 点不参与；ID 用于确定性身份，重复 ID 与巨大点集成本需说明。缺少共享 Checkpoint 配置报告依赖，不替用户修改 Engine。

配置定位（只列关键语义，完整字段读取实时契约）：

- `featureIdColumnName`：必填且不能为 Geometry 的要素身份字段名；每个实际参与点的值必须非 NULL 且唯一，相同坐标但不同 ID 仍是不同观测。真实数据约束在惰性执行时检查。
- `distanceMethod`：必填的距离方式：PLANAR 使用来源 CRS 的 XY 坐标距离；GEODESIC 只支持 EPSG:4326 XY Point，并使用 WGS84 测地距离。
- `parameters`：必填的多态算法参数，通过 algorithm 选择 DBSCAN、HDBSCAN 或不可执行的 MULTI_SCALE 草稿。minimumFeatures 均须为 2 至 100000，并包含观测自身。
- `clusterIdColumnName`：必填且不能与来源字段或其他输出重名的 LONG 簇 ID 字段名；噪声点为 NULL。簇编号只标识本次运行结果，不承诺跨重跑稳定。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 保留参与记录字段并追加簇 ID/噪声标记；HDBSCAN 可有诊断列，输出 BOUNDED 且不继承 Watermark。

## 最小配置示例

前提：上游逻辑表 features 含 id:LONG 和 geom:GEOMETRY(POINT, EPSG:3857, XY)，字段名称与输出名不冲突。 输入 BOUNDED，id 非空唯一；所选 Engine 需已配置共享 spark.checkpoint.dir。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "pointGeometryColumnName": "geom",
  "featureIdColumnName": "id",
  "distanceMethod": "PLANAR",
  "parameters": {
    "algorithm": "DBSCAN",
    "searchDistance": 100.0,
    "searchDistanceUnit": "METERS",
    "minimumFeatures": 3
  },
  "outputTableName": "clusters",
  "clusterIdColumnName": "cluster_id",
  "noiseColumnName": "is_noise",
  "dbscan": {
    "mode": "SPATIAL"
  }
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `SPATIAL_CLUSTER_CHECKPOINT_NOT_CONFIGURED` | 报告运行基础设施依赖。 |
| `SPATIAL_CLUSTER_ALGORITHM_NOT_AVAILABLE` | 说明未实现算法，重新确认可行选型。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
