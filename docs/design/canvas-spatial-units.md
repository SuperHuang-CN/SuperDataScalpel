# Canvas 空间距离、面积与固定时长单位

## 范围与兼容性

Canvas 4.36 扩充 `SpatialDistanceUnit` 和 `SpatialAreaUnit`，对照 ArcGIS Enterprise 11.3
GeoAnalytics 的距离/面积单位以及 Esri Projection Engine 的单位定义。它是单位语义补齐，
不等于节点的算法、默认参数、输出或数值精度已经与官方服务完成对照。

- 旧 `FEET`、`MILES`、`NAUTICAL_MILES`、`ACRES`、`SQUARE_FEET`、`SQUARE_MILES` 始终保留国际制换算。
- 新增国际码及平方国际码，并用独立 `*_US` 表达美国测量制。不要把旧 FEET 重新解释成美国测量英尺。
- 不复制 `Feet`/`FeetInt`/`FeetUS` 等 Esri API 别名；平台 JSON 使用自己的强类型枚举。
  Esri 未带后缀的英文单位名不能仅按字面匹配：`esriUnits` 枚举中 Feet/Yards/Miles/NauticalMiles
  是 US 定义，Int 为独立定义。对接具体工具时仍需核实该端点语义，不能推定所有产品同名单位相同。
- 新枚举要求 4.36。前端导入、Business 保存/发布、Compiler/Runner 图校验检查实际类型字段，
  包括隐藏/非活动草稿；低版本返回 `SPATIAL_EXTENDED_UNITS_REQUIRE_SCHEMA_VERSION` 及精确字段路径。
  普通表名/字段名恰好为 `FEET_US` 不触发门槛。旧合法小版本读入后按当前小版本保存。
- 不改变 Manifest、Task Result 或 HTTP API，不自动迁移数值或增加真实预读。

## 长度换算

以下为每单位对应米数。小数是展示近似值；美国测量英尺计算使用精确比例 `1200 / 3937`。

| 平台枚举 | UI 名称 | 米/单位 | Esri/EPSG 标识 |
| --- | --- | ---: | --- |
| METERS | 米 | 1 | EPSG 9001 |
| KILOMETERS | 千米 | 1000 | EPSG 9036 |
| FEET | 国际英尺 | 0.3048 | EPSG 9002 |
| YARDS（4.36） | 国际码 | 0.9144 | EPSG 9096 |
| MILES | 国际英里 | 1609.344 | EPSG 9093 |
| NAUTICAL_MILES | 国际海里 | 1852 | EPSG 9030 |
| FEET_US（4.36） | 美国测量英尺 | 1200/3937 | EPSG 9003 |
| YARDS_US（4.36） | 美国测量码 | 3×1200/3937 | Esri 109002 |
| MILES_US（4.36） | 美国测量英里 | 5280×1200/3937 | EPSG 9035 |
| NAUTICAL_MILES_US（4.36） | 美制海里（1954 年前） | 1853.248 | Esri 109012 |

`SOURCE_CRS_UNIT` 是平台选项，不对应固定米数：投影坐标使用来源轴的线性单位，不能假定为米。
平面/测地及角度限制沿用各节点规则。简化在地理 CRS 下只允许来源角度单位；测地距离不能用来源角度单位。
旧美制海里使用 Esri 明确的 1853.248 米，不用国际海里，也不以近似的测量英尺数量替代。

## 面积换算

设 `f = 1200/3937` 米。每单位对应平方米：

| 平台枚举 | 平方米/单位 | 版本 |
| --- | ---: | --- |
| SQUARE_METERS | 1 | 原有 |
| SQUARE_KILOMETERS | 1000000 | 原有 |
| HECTARES | 10000 | 原有 |
| ACRES | 4046.8564224 | 原有国际英亩 |
| SQUARE_FEET | 0.09290304 | 原有国际制 |
| SQUARE_MILES | 2589988.110336 | 原有国际制 |
| SQUARE_YARDS | 0.83612736 | 4.36 国际制 |
| SQUARE_FEET_US | f² ≈ 0.09290341161327487 | 4.36 |
| SQUARE_YARDS_US | 9f² ≈ 0.8361307045194736 | 4.36 |
| SQUARE_MILES_US | 5280²f² ≈ 2589998.470319522 | 4.36 |
| ACRES_US | 43560f² ≈ 4046.872609874252 | 4.36 |

面积单位独立于长度输出单位。Within 的分摊比例仍由同一测度的比值计算，不因为最终展示单位而改变。

## 面板与字段覆盖

复用紧凑单位 Select；名称明确国际制/美国测量制。字段旁帮助可通过悬停、聚焦或点击查看。
切换单位只改变单位枚举，**不自动换算已填写的数值**，帮助明确要求用户同时检查数值。
隐藏字段及非法业务组合保留草稿，不能通过切换模式静默清空。

| 节点 | 使用公共单位的配置位置 |
| --- | --- |
| Geometry Simplify | toleranceUnit；Canvas 容差摘要使用同一单位名称 |
| Nearest | maximumDistanceUnit、distanceOutputUnit、matching.connectionLines.maximumGeodesicSegmentLengthUnit |
| Summarize Within | lengthUnit、areaUnit |
| Bin Aggregate | binSizeUnit（含 H3 近似大小；显式分辨率不读取该数值，但保留草稿） |
| Point Cluster | parameters.searchDistanceUnit（DBSCAN） |
| 四个 Track 节点 | boundaries.maximumDistanceGapUnit |
| Reconstruct | reconstruction.pathGeometry.maximumGeodesicSegmentLengthUnit |
| Find Dwell | distanceThresholdUnit、rangeOptions.meanDistanceUnit（点级输出隐藏设置仍保留） |
| Motion Statistics | idleDistanceThresholdUnit、metrics[i].outputUnit（Distance/ElevationChange）、windowOptions.distanceUnit/inputElevationUnit/elevationUnit |

速度和加速度使用原独立枚举及换算：英尺/秒、英里/小时是国际制，KNOTS 是国际海里/小时。
本次不声称它们与每个 Esri 端点别名完全等价，也不以距离单位推断速度单位。
时间间隔、日历切片及参考时间是另一组语义；4.36 不扩展它们，4.47 固定周见下节。
现有 Buffer/Measure 使用其他配置契约，须在路线图的现有节点复核阶段处理，不因本次共享枚举补齐而标记完成。

## 4.47 固定时长周与日历周

`SpatialDurationUnit` 增加 `WEEKS`，定义为固定 `7 × 24 × 60 × 60 = 604800` 秒。
毫秒、秒、分钟、小时、天保持旧换算，天始终为 24 小时。可用小数/整数及数值上限继续由各参数决定，
不因增加单位放宽校验；微秒乘法溢出仍返回原稳定配置错误。

| 配置位置 | 固定周生效字段 |
| --- | --- |
| Bins / Within | temporalSlicing.intervalUnit、repeatIntervalUnit |
| DBSCAN | dbscan.searchDurationUnit（仅 LINEAR 使用；其他算法隐藏草稿保留） |
| 四个 Track 节点 | boundaries.maximumTimeGapUnit |
| Dwell | minimumDurationUnit、rangeOptions.durationUnit |
| Incidents | incidentDurationUnit |
| Motion | windowOptions.durationUnit、idleTimeThresholdUnit；旧 metrics[i].outputUnit 的 DURATION 项 |

- 固定 Select 显示“周（固定 7 天）”，切换不换算已填数值。阈值原有严格/包含边界不变。
- `boundaries.fixedTimeBoundary.unit=WEEKS`（4.21）和 `temporalSlicing.calendar.*Unit=WEEKS`（4.39）
  仍是按 IANA 时区推进的日历周。跨纽约春季 DST 的日历周可为 167 小时，固定周仍为 168 小时。
  不把固定周解释为每周一重置，也不改变参考时刻对齐规则。
- 保存、GraphPlan 和前端导入检查上述明确的固定时长字段，包括非活动草稿。
  低于 4.47 返回 `SPATIAL_DURATION_WEEKS_REQUIRE_SCHEMA_VERSION` 及精确路径；日历周及普通字符串不触发。
  不含新固定周的旧合法定义读入后按当前版本保存；Manifest、Result、HTTP API 不变。
- GA Motion REST 的时长单位表还列出 Months/Years；这里**不**假定一个月 30 天或一年 365 天。
  月年与固定时长的映射没有足够官方依据，固定时长枚举不开放；已有日历月年能力不受影响。
  本节是明确的平台换算契约，不宣称与 Esri 全部单位/厂商别名及 DST 行为等价。

## 证据与验收边界

- [Calculate Motion Statistics](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/calculate-motion-statistics/)
- [Summarize Within](https://developers.arcgis.com/rest/services-reference/enterprise/geoanalytics/tasks/summarize-within/)
- [Esri esriUnits](https://developers.arcgis.com/enterprise-sdk/api-reference/net/esriUnits/)
- [Esri SR Unit Type](https://developers.arcgis.com/enterprise-sdk/api-reference/net/esriSRUnitType/)
- [Esri SR Unit 2 Type](https://developers.arcgis.com/enterprise-sdk/api-reference/net/esriSRUnit2Type/)
- [Esri 线性单位定义](https://github.com/Esri/projection-engine-db-doc/blob/main/json/pe_list_linunit.json)
- [Esri 面积单位定义](https://github.com/Esri/projection-engine-db-doc/blob/main/json/pe_list_areaunit.json)

单位库条目的 version 元数据早于 11.3；上述单位系数不能代替真实 11.3 工具服务的行为验收。
专项覆盖全部枚举 JSON、三端门槛接线、非活动路径、EPSG:3857/2263/4326、
国际/美国英尺阈值反例、Within 裁剪长度/面积和 Motion 高程/距离/速度独立性。
完整官方服务对照、规模数据与全页面交互验收继续保留在开发清单。
