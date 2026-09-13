# Canvas `SPATIAL_MEASURE` Processor 设计文档

## 1. 状态、目标与范围

- 实现状态：基础测量已实现；Canvas 4.50 增加逐测量项显式输出单位。
- 初始协议版本：Canvas `1.21`；显式输出单位：Canvas `4.50`。
- 节点类型：`SPATIAL_MEASURE`。
- 节点类别：`PROCESSOR`。
- 执行模式：`BATCH`、`STREAMING`。
- 图规则：至少一条入边和一条出边；多个上游表 Map 先执行无覆盖合并。

`SPATIAL_MEASURE` 对一张逻辑表中的 Geometry 字段执行逐行空间测量，在保留来源行和字段
的同时追加一个或多个 DOUBLE 指标。首期支持面积、长度、周长、两 Geometry 间距离以及
Point 的 X/Y 坐标。

距离、面积和长度必须显式选择平面或椭球语义，节点不会根据 EPSG code 猜测用户意图，
也不会隐式转换 CRS。需要改变坐标系时必须先使用 `SPATIAL_TRANSFORM`。

## 2. 稳定配置协议

测量项使用以 `kind` 为判别字段的 sealed 联合。只有需要距离或面积语义的测量项包含
`mode`；X/Y 是坐标读取，不携带无意义的模式字段。

```ts
interface SpatialMeasureConfiguration {
  sourceTableName: string;
  outputTableName: string;
  measurements: SpatialMeasurement[];
}

type SpatialMeasureMode = 'PLANAR' | 'SPHEROID';

type SpatialMeasurement =
  | {
      kind: 'AREA';
      geometryColumnName: string;
      mode: SpatialMeasureMode;
      outputColumnName: string;
      outputUnit?: SpatialAreaUnit | null;
    }
  | {
      kind: 'LENGTH';
      geometryColumnName: string;
      mode: SpatialMeasureMode;
      outputColumnName: string;
      outputUnit?: SpatialDistanceUnit | null;
    }
  | {
      kind: 'PERIMETER';
      geometryColumnName: string;
      mode: SpatialMeasureMode;
      outputColumnName: string;
      outputUnit?: SpatialDistanceUnit | null;
    }
  | {
      kind: 'DISTANCE';
      leftGeometryColumnName: string;
      rightGeometryColumnName: string;
      mode: SpatialMeasureMode;
      outputColumnName: string;
      outputUnit?: SpatialDistanceUnit | null;
    }
  | {
      kind: 'X';
      geometryColumnName: string;
      outputColumnName: string;
    }
  | {
      kind: 'Y';
      geometryColumnName: string;
      outputColumnName: string;
    };
```

示例：

```json
{
  "sourceTableName": "roads_and_regions",
  "outputTableName": "roads_and_regions_measured",
  "measurements": [
    {
      "kind": "LENGTH",
      "geometryColumnName": "road_centerline",
      "mode": "SPHEROID",
      "outputColumnName": "road_length_km",
      "outputUnit": "KILOMETERS"
    },
    {
      "kind": "AREA",
      "geometryColumnName": "region_boundary",
      "mode": "SPHEROID",
      "outputColumnName": "region_area_hectares",
      "outputUnit": "HECTARES"
    },
    {
      "kind": "DISTANCE",
      "leftGeometryColumnName": "event_location",
      "rightGeometryColumnName": "service_location",
      "mode": "SPHEROID",
      "outputColumnName": "service_distance_m",
      "outputUnit": "METERS"
    }
  ]
}
```

协议限制：

- `sourceTableName`、`outputTableName` 必填。
- `measurements` 按数组顺序保存，至少一项、最多 32 项。
- 每个 `outputColumnName` 必填，不能与来源字段或其他测量输出重名。
- 所有参与字段必须是带完整 Geometry 定义的 `GEOMETRY` 字段。
- AREA 只接受 POLYGON、MULTIPOLYGON。
- LENGTH 只接受 LINESTRING、MULTILINESTRING。
- PERIMETER 只接受 POLYGON、MULTIPOLYGON。
- X、Y 只接受 POINT。
- DISTANCE 接受现有任意 GeometryKind，包括通用 GEOMETRY。
- 所有参与字段首期只支持 `EPSG + XY`。
- AREA 的 `outputUnit` 使用 `SpatialAreaUnit`；LENGTH/PERIMETER/DISTANCE 使用
  `SpatialDistanceUnit`。X/Y 不接受单位字段。
- 缺失/null `outputUnit` 保持 4.50 之前的结果：PLANAR 为来源 CRS 坐标单位（面积为平方），
  SPHEROID 为米/平方米。任何非 null 单位在低于 4.50 的定义中拒绝。
- 定义不保存单位换算系数、任意 Sedona 函数名、SQL、样例值或 UI 内部类型。

## 3. 测量模式与单位

### 3.1 PLANAR

PLANAR 直接在 Geometry 当前平面坐标系中计算：

- AREA 使用 `ST_Area`。
- LENGTH 使用 `ST_Length`。
- PERIMETER 使用 `ST_Perimeter` 的平面模式。
- DISTANCE 使用 `ST_Distance`。

原始结果单位来自 CRS 坐标单位；面积为坐标单位平方。4.50 起，投影 CRS 可依据 GeoTools
解析出的第一坐标轴线性单位，将结果可靠换算为逐项选择的固定距离/面积单位。未选择单位时
不换算。节点仍不在输出 Schema 中虚构单位元数据。

地理 CRS 使用 PLANAR 时结果是角度或角度平方，只能保留来源单位：线性项显式选择
`SOURCE_CRS_UNIT` 或保持 null，AREA 保持 null。固定线性/面积单位无法可靠换算并返回
`SPATIAL_MEASURE_OUTPUT_UNIT_UNSUPPORTED`。计划仍可执行的来源单位组合产生
`PLANAR_MEASURE_USES_ANGULAR_UNITS` Warning，Inspector 同步提示优先选择 SPHEROID 或先
显式转换到合适的投影 CRS。

### 3.2 SPHEROID

SPHEROID 固定使用 WGS84 椭球语义，并在得到米/平方米原始结果后按 `outputUnit` 换算：

- AREA 使用 `ST_AreaSpheroid`，输出平方米。
- LENGTH 使用 `ST_LengthSpheroid`，输出米。
- PERIMETER 使用 Sedona `ST_Perimeter` 的 spheroid 严格模式，输出米。
- DISTANCE 使用 `ST_DistanceSpheroid`，输出米。

所有参与 Geometry 必须为 EPSG:4326；其他 CRS 返回编译错误，不能由节点自动转换。
DISTANCE 两侧还必须使用相同 coordinate dimension。首期仅支持 XY，因此任何非 XY 字段
均在进入表达式前被拒绝。线性项不接受 `SOURCE_CRS_UNIT`；AREA 使用固定面积单位。

### 3.3 X/Y

X 和 Y 分别使用 `ST_X`、`ST_Y`，返回 Point 当前 CRS 下的原始坐标值。它们不是距离或
面积，不使用 `mode`，也不自动把坐标转换成经纬度或米。

## 4. NULL、数据值与执行语义

- 任一测量所需 Geometry 为 NULL 时，该测量输出 NULL。
- 所有输出字段固定为 nullable DOUBLE，避免对 Empty 或真实几何数据作过强 Schema 承诺。
- 不同测量项彼此独立；一个 NULL 结果不影响其他项。
- 配置顺序只决定新增字段顺序，不改变测量计算含义。
- Compiler 只验证字段、Geometry 定义、CRS、kind 和 Spark/Sedona 表达式，不读取真实值。
- 无效、Empty 或数值异常 Geometry 的实际结果遵循 Sedona；Compiler 不产生“可能无效”
  Warning，也不试跑数据。
- 需要在测量前确认拓扑合法性时，应显式连接 `GEOMETRY_VALIDATE`。

## 5. Map、Schema 与批流传播

Map 规则：

- 从输入 Map 按 `sourceTableName` 精确取表。
- 保留输入 Map 中全部表，以 `outputTableName` 追加测量结果。
- 输出名与任一已有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。

Schema 规则：

- 来源字段及全部空间元数据原样保留。
- 测量字段按 `measurements` 顺序追加为 nullable DOUBLE。
- 新字段的 length、precision、scale、geometry、default、autoIncrement、generated 和
  comment 均为空或 false。
- 输出表 `origin=null`，继承来源 `datasetKind`、`eventTimeColumn` 和 `watermarkDelay`。

所有操作逐行、无状态且不改变行数。BOUNDED 和 UNBOUNDED 输入使用同一 Operator，不
改变事件时间、Watermark，也不在节点中保存 Trigger、Checkpoint 或状态超时配置。

## 6. 校验与稳定错误码

复用错误码：

- `CONFIGURATION_REQUIRED`
- `REQUIRED_CONFIGURATION`
- `TABLE_NOT_FOUND`
- `COLUMN_NOT_FOUND`
- `DUPLICATE_TABLE_NAME`
- `DUPLICATE_COLUMN_NAME`
- `GEOMETRY_FIELD_OPERATION_UNSUPPORTED`
- `GEOMETRY_TYPE_DEFINITION_REQUIRED`
- `UNSUPPORTED_GEOMETRY_CRS`
- `UNSUPPORTED_GEOMETRY_DIMENSION`
- `SPARK_ANALYSIS_ERROR`
- `NODE_EXECUTION_MODE_NOT_SUPPORTED`
- `UPSTREAM_INVALID`

新增错误码：

| 错误码 | 条件 |
| --- | --- |
| `EMPTY_SPATIAL_MEASUREMENTS` | 没有配置测量项 |
| `SPATIAL_MEASUREMENT_LIMIT_EXCEEDED` | 测量项超过 32 项 |
| `INVALID_SPATIAL_MEASUREMENT` | 测量判别类型、字段组合或 mode 不完整 |
| `SPATIAL_MEASURE_KIND_UNSUPPORTED` | GeometryKind 不支持对应测量 |
| `SPATIAL_MEASURE_CRS_MISMATCH` | DISTANCE 两侧 CRS 或 dimension 不一致 |
| `SPHEROID_MEASURE_REQUIRES_WGS84` | SPHEROID 测量字段不是 EPSG:4326 |
| `PLANAR_MEASURE_USES_ANGULAR_UNITS` | 地理 CRS 使用 PLANAR；级别为 WARNING |
| `SPATIAL_MEASURE_OUTPUT_UNIT_UNSUPPORTED` | 当前 mode/CRS 无法可靠解释或换算所选输出单位 |
| `SPATIAL_MEASURE_UNIT_REQUIRE_SCHEMA_VERSION` | 低于 4.50 却携带显式 `outputUnit` |

配置错误精确指向 `configuration.measurements[index]` 或具体字段路径。WARNING 不阻断
Schema 传播和下游编译。

## 7. 前端设计器

- Palette 名称为“空间测量”，说明为“计算 Geometry 的面积、长度、周长、距离或坐标”。
- 节点位于“空间处理”分组，批处理和实时模式均展示。
- Inspector 从 Compiler `inputTables` 选择来源表，只展示 GEOMETRY 字段。
- 测量项使用紧凑卡片，支持新增、上移、下移和删除，并显示 `当前项数/32`。
- AREA/LENGTH/PERIMETER/X/Y 选择一个 Geometry 字段；DISTANCE 分别选择左右字段。
- 仅 AREA/LENGTH/PERIMETER/DISTANCE 显示 PLANAR/SPHEROID 和同行的输出单位选项。
- 根据上游 GeometryKind 过滤候选；已保存但失效的字段继续显示并标红。
- SPHEROID 选中非 4326 字段时原值保留并显示错误；PLANAR + 4326 显示单位警告。
- 面板明确展示当前有效单位；旧 null 配置显示“兼容默认”，不会因打开 Inspector 被改写。
- 地理 PLANAR + 固定单位、SPHEROID + `SOURCE_CRS_UNIT` 原值保留并就地标红；切换 mode
  不修改单位，也不自动换算已经产生业务含义的数值。
- 节点摘要显示来源表、测量数量、测量类型集合和模式集合，不显示坐标或结果值。

## 8. Task Engine、Runner 与安全边界

新增唯一无状态 `SpatialMeasureNodeOperator`，Compiler 与 Runner 通过同一内置 Registry
调用。执行阶段固定为 `PROCESS`，成功消息固定为“空间测量已准备”。

安全摘要只允许记录表名、参与字段名、输出字段名、测量类型、模式、输出单位、数量和 CRS。日志、
结果和事件不得记录 Geometry、坐标、距离、面积、长度或其他实际测量结果。成功日志不得
为了指标统计触发 Spark Action。

真实数据导致 Sedona 执行失败时使用处理器统一失败分类和诊断 ID，对外信息保持通用；
不得把导致失败的 Geometry 或坐标从 Spark 异常文本回显给用户。

## 9. 行为验收标准

- 六种测量项、两种 mode 及可选输出单位可稳定 JSON 往返，未知判别类型被拒绝。
- 旧缺失/null 单位结果不变；显式单位受 Canvas 4.50 保存、导入和 Compiler 三端门槛约束。
- 投影 PLANAR 与 WGS84 SPHEROID 的距离/面积换算在已知样例中得到目标单位结果；不可靠组合明确拒绝。
- 测量数量、输出字段顺序、重名校验和 nullable DOUBLE Schema 稳定。
- PLANAR 与 SPHEROID 在已知 Geometry 上得到容差内结果和正确单位语义。
- GeometryKind、CRS、dimension 和 DISTANCE 双字段限制得到准确诊断。
- EPSG:4326 的 PLANAR Warning 不影响输出 Schema 和下游传播。
- NULL 输入只影响对应测量项，不删除行。
- 输入 Map 完整保留，输出表只追加不覆盖。
- BATCH 与 STREAMING 使用同一 Operator并继承有界性、事件时间和 Watermark。
- Compiler 不读取真实 Geometry、不统计结果、不触发 Spark Action。
- Inspector 只消费权威 `inputTables` 并保留失效值。
- 安全摘要和日志不包含任何测量结果或空间数据值。

## 10. 不在范围内

- 三维距离、Z/M 坐标、方位角、最短线和最近点。
- 自定义地球半径、自定义椭球、结果字段单位元数据和任意 CRS 目录维护。
- Raster 测量、轨迹测量、聚合面积或分组统计。
- 空间 Join 距离阈值、最近邻查询和跨行测量。
- Geometry 自动转换 CRS、自动修复或抽样验证。

## 11. 协议发布

本节点已与 `GEOMETRY_CONSTRUCT`、`GEOMETRY_VALIDATE`、`GEOMETRY_SERIALIZE` 一起在
Canvas `1.21` 引入。4.50 仅增加可选逐项输出单位；旧定义继续读取并保持数值语义，当前统一规范化写出 `4.50`。
