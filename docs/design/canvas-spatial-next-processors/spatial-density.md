# Canvas Calculate Density Processor 设计

## 1. 定位与对齐边界

`SPATIAL_DENSITY` 对齐 ArcGIS GeoAnalytics Server Calculate Density 的核心矢量能力：Point 输入、
可选数量字段、Uniform/Kernel、Square/Hexagon、格网大小、搜索半径、面积单位和可选时间切片。
节点只输出 Polygon 矢量格网，不引入 Raster、H3、自动投影或后台接口。

官方参考：[Calculate Density (GeoAnalytics)](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/calculate-density-geoanalytics/)。

当前实现采用下文公开且可复现的平台公式。尚未对真实 ArcGIS Enterprise 作业完成逐格网数值、边缘
和大规模对照，因此“参数能力对齐”不等于“Esri 内部算法逐位等价”。

## 2. 配置契约

Canvas 4.68 新增：

```ts
interface SpatialDensityField {
  fieldId: string;
  sourceColumnName: string;
  outputColumnName: string;
}

interface SpatialDensityConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  fields: SpatialDensityField[];
  weighting: 'UNIFORM' | 'KERNEL' | null;
  binShape: 'SQUARE' | 'HEXAGON' | null;
  binSize: number;
  binSizeUnit: SpatialDistanceUnit;
  radius: number;
  radiusUnit: SpatialDistanceUnit;
  areaUnit: SpatialAreaUnit;
  temporalSlicing: SpatialTemporalSlicing | null;
  outputTableName: string;
  binIdColumnName: string;
  binGeometryColumnName: string;
  countDensityColumnName: string;
}
```

`fields` 可以为空且最多 32 项；点数密度不在数组中并始终输出。`fieldId` 是节点内唯一 UUID，
编辑与排序时保持稳定。来源字段必须为数值类型，来源字段和所有输出字段名均按大小写不敏感唯一。

## 3. 输入、格网与单位

- 仅支持 BATCH/BOUNDED、带完整 CRS 元数据的 XY Point。
- CRS 必须是可解析的投影坐标系，格网与半径单位都换算到第一轴单位；不在经纬度上把角度当米。
- 方格 `binSize` 是边长；六边形为对边距离，内部边长为 `binSize / √3`。
- 原点固定为 `(0, 0)`；只输出半径范围内收到至少一个点贡献的格网，不补空格网。
- 搜索半径换算后必须严格大于格网大小，且 `radius/binSize <= 512`，避免单点无限候选展开。
- `areaUnit` 只缩放密度值，不改变成员关系、格网边界或距离。

NULL/Empty Point 与时间切片中的 NULL/间隙观测不参与。实际坐标不可表示、数量字段出现 NaN/Infinity
时安全失败，不静默丢行或写入非有限结果。

## 4. 密度公式

对格网中心到点的投影距离 `d` 和搜索半径 `r`：

```text
UNIFORM: quantity / (πr²)
KERNEL:  3 / (πr²) × (1 - d²/r²)² × quantity, 0 <= d <= r
```

点数密度令每个有效点的 `quantity=1`。数量字段为 NULL 时对该字段贡献 0，但仍贡献点数密度。
各贡献按格网和可选时间窗口求和，再从来源 CRS 平方面积换算到 `areaUnit`。

时间切片复用空间汇总的左闭右开固定/日历窗口；重叠窗口会让一个观测贡献到多个窗口，窗口间隙不贡献。

## 5. 输出与执行

输出列顺序：

1. STRING 格网 ID；
2. 与来源 CRS 一致的 XY Polygon；
3. 可选窗口开始/结束 TIMESTAMP；
4. DOUBLE 点数密度；
5. `fields` 定义顺序的 DOUBLE 数量密度。

节点保留入口完整表 Map，并把新表追加到末尾。Compiler 只构造零行或惰性 Spark 计划，不执行
Action；Runner 执行候选格网展开、距离过滤与聚合。安全摘要仅包含来源/输出表、方法、形状、数量字段数
和是否切片，不包含半径、字段值、时间值或数据内容。

## 6. Inspector 与 Canvas

Inspector 依次展示：来源表与 Point、Uniform/Kernel、方格/六边形、格网大小、搜索半径、面积单位、
数量字段、时间切片和输出。数量字段、时间切片和输出字段分别使用独立设置 Modal；上游失效值保留并标红，
普通业务错误允许保存草稿。

Canvas 卡片只展示来源到结果的流向、方法、形状、格网/半径单位、面积单位、密度字段数和时间切片状态，
不展示任何数据值。

## 7. 尚未完成的验收

- ArcGIS Enterprise Uniform/Kernel 逐格网数值、搜索边界和六边形对齐对照；
- 米、国际/美国测量英尺等不同投影轴单位真值；
- 重叠日历窗口、极稀疏与极密集点集容量；
- 半径/格网比例接近上限时的 Executor 内存和 Shuffle 规模。

这些验收完成前，不把当前实现描述为 ArcGIS 数值完全一致。

## 8. 当前支持范围收口（2026-09-13，协议仍为 4.76）

- 当前明确承诺的矢量 Density 范围已形成闭环：投影 XY Point、Uniform/Kernel、方格/六边形、
  点数与数量字段密度、固定/日历时间切片及显式距离/面积单位继续使用本文公式和边界。
- Uniform 与 Kernel（含时间切片）的全部结果字段均达到 `FIELD_COMPLETE`，不存在
  `WRITTEN_UNKNOWN_SOURCE`。格网 ID、格网 Geometry 和点数密度追溯 Point Geometry；窗口字段
  追溯时间字段；数量密度同时追溯 Point Geometry 和对应数量字段。非有限值的 `raise_error`
  只作为失败分支，不再污染正常数量密度的字段来源。
- 20,000 个规则稀疏投影点的 Preview 仅建立并分析 Catalyst 计划，提交 Spark Job 数为 0；
  Kernel 六边形密度完成真实执行，计划包含候选格网展开与分布式聚合，不含 `CollectLimit`、
  `collect_list`、笛卡尔积或 Broadcast Nested Loop Join。该样例不等价于生产容量承诺。
- `SpatialDensityNodeOperatorSparkTest` 3 项通过。真实页面已验证新增节点延迟编译、Kernel/六边形、
  时间切片、数量字段、结果字段、无效草稿应用和紧凑问题详情；未保存任务定义。
- 当前收口不扩大第 7 节边界：Enterprise Uniform/Kernel 逐格网数值、边缘与六边形编码、不同投影
  轴单位真值、重叠日历窗口及半径比例上限附近的生产容量仍开放，不声明 Esri 数值完全等价。
