# GEOMETRY_CONSTRUCT · 构造几何

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

Geometry 构造配置；保留来源表全部字段，并从 WKT、WKB、GeoJSON 或 X/Y 字段追加一个具体 EPSG + XY Geometry 字段。targetGeometry 的 CRS 直接赋给解析结果，输入坐标必须已经属于该 CRS；本节点不执行坐标转换。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- source.kind 支持 WKT/WKB/GEOJSON/POINT_FROM_XY；来源字段类型必须匹配，不用 GeoJSON FeatureCollection 代替单几何。
- targetGeometry 明确具体类型、EPSG 和 XY；这里只赋 CRS 元数据，不做投影转换。

配置定位（只列关键语义，完整字段读取实时契约）：

- `source`：必填的严格判别来源；WKT/GEOJSON 读取 STRING，WKB 读取 BINARY，POINT_FROM_XY 读取两个数值字段。来源为 NULL 时结果为 NULL；格式损坏或实际 GeometryKind 不符会在运行时失败。
- `targetGeometry`：输出 Geometry 的必填类型；必须声明具体 GeometryKind、XY 维度和 code>0 的 EPSG CRS。POINT_FROM_XY 只允许 POINT。解析来源的真实类型必须与声明 kind 一致，不能使用通用 GEOMETRY。

## 逻辑表 Map 与字段

保留输入 Map，追加命名结果表；outputTableName 必须与输入及其他结果表不同。 保留来源列，追加 Geometry；类型与 CRS 使用显式声明。流模式继承来源有界性；事件时间与 Watermark 的实际传播以预校验 outputTables 为准。

## 最小配置示例

前提：features 含数值 x、y，坐标已属于 EPSG:3857，尚无 geom 字段。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "features",
  "outputTableName": "with_geometry",
  "outputColumnName": "geom",
  "source": {
    "kind": "POINT_FROM_XY",
    "xColumnName": "x",
    "yColumnName": "y"
  },
  "targetGeometry": {
    "kind": "POINT",
    "crs": {
      "authority": "EPSG",
      "code": 3857
    },
    "dimension": "XY"
  }
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `GEOMETRY_CONSTRUCT_SOURCE_TYPE_MISMATCH` | 选择正确源编码/列类型。 |
| `UNSUPPORTED_GEOMETRY_CRS` | 明确有效 EPSG，坐标转换需求另行设计。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
