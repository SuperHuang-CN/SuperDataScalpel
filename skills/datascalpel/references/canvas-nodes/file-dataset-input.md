# FILE_DATASET_INPUT · 文件数据集输入

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

批处理文件数据集输入配置；从一个来源文件数据集中选择一张或多张已完成 Schema 解析的逻辑表，运行时严格按 Manifest 中的字段快照读取，不根据实际文件重新推断 Schema。

## 模式与连线

- 模式：BATCH；类别：INPUT。
- 无入边，至少一条出边；不读取上游 Map。
- 输入有界性见下文。

## 关键配置与行为

- 只引用现有解析资源；发现 GET /api/v1/file-datasets/{id}/tables/{tableId}/schema 等 JSON 元数据接口。无需且不能通过系统 MCP 上传文件。
- 按当前受支持格式与 Schema 解析状态选择；不根据运行文件重新推断字段，不能把无法获取元数据解释为空表。

配置定位（只列关键语义，完整字段读取实时契约）：

- `fileDatasetId`：来源文件数据集 UUID 字符串；草稿可为空，编译前必须存在且其来源文件已 READY。Canvas 不保存对象 Key、物化位置、解析参数或存储凭据。
- `tables`：从该数据集中选择的逻辑表；至少一项，同一表 UUID 不能重复且必须属于 fileDatasetId。每项按自身稳定 code 产生一张 BOUNDED Canvas 表。

## 逻辑表 Map 与字段

每个所选文件表按元数据 code 产生一张 BOUNDED 表，保留字段次序和类型；不同表 code 不冲突。

## 最小配置示例

前提：文件数据集及所选表已存在，文件 READY，表已有受支持解析状态和完整 JSON Schema；UUID 关系已核对。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "fileDatasetId": "11111111-1111-4111-8111-111111111111",
  "tables": [
    {
      "fileDatasetTableId": "22222222-2222-4222-8222-222222222222"
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `FILE_DATASET_TABLE_SOURCE_MISMATCH` | 核对表确实属于所选数据集。 |
| `FILE_DATASET_TABLE_NOT_READY` | 报告解析依赖，等待用户准备资源。 |
| `FILE_DATASET_FORMAT_NOT_SUPPORTED` | 说明该执行侧格式限制。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
