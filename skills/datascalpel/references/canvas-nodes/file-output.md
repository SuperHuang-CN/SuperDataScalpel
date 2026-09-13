# FILE_OUTPUT · 文件输出

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

批处理文件输出配置；向同一个已启用且具有 DISTRIBUTION 用途的 S3 数据源声明一项或多项独立目录写入。输出不产生下游表；多项写入按顺序执行且没有跨目标事务，前项可能已成功而后项失败。

## 模式与连线

- 模式：BATCH；类别：OUTPUT。
- 恰好一条入边，无出边；一条入边可包含多张逻辑表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- targetPath 为数据源内受控相对目录，不是任意 URL 或本地绝对路径；FAIL_IF_EXISTS 拒绝已有目录，OVERWRITE 影响须在方案中明确。
- formatOptions.type 区分 CSV/JSON_LINES/PARQUET/SHAPEFILE/GEOPARQUET/GEOJSON；各格式字段不同，不能混用。
- 普通格式对 Geometry 有限制；Shapefile 要显式 Shape 类型、DBF 映射且检查字段宽度；GeoParquet 恰好一个 EPSG+XY Geometry；GeoJSON 为 EPSG:4326 XY，另受单文件及属性限制。
- 多写入无跨目标事务。这是未来任务运行时的文件输出，配置用 JSON，当前不调用文件生成、上传下载或结果预览。

配置定位（只列关键语义，完整字段读取实时契约）：

- `writes`：按数组顺序执行的独立目标目录写入，至少一项；每项 writeId 在节点内唯一。任一写入失败时已提交目录不回滚，后续项跳过。

## 逻辑表 Map 与字段

无下游 Map；普通格式按其契约保留列，Shapefile 使用属性映射；几何序列化/空间格式需明确选择，不静默丢字段。

## 最小配置示例

前提：orders 是 BOUNDED，包含当前普通 Parquet 支持的标量列；UUID 是启用且有 DISTRIBUTION 用途的 S3 数据源。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "dataSourceId": "22222222-2222-4222-8222-222222222222",
  "writes": [
    {
      "writeId": "11111111-1111-4111-8111-111111111111",
      "sourceTableName": "orders",
      "targetPath": "exports/orders",
      "conflictPolicy": "FAIL_IF_EXISTS",
      "formatOptions": {
        "type": "PARQUET"
      }
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `INVALID_FILE_OUTPUT_PATH` | 改为允许的相对目录，拒绝路径穿越。 |
| `INVALID_FILE_OUTPUT_FORMAT_OPTION` | 只填写活动格式要求的参数。 |
| `GEOMETRY_FIELD_OPERATION_UNSUPPORTED` | 选择合适空间格式或明确序列化方案。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
