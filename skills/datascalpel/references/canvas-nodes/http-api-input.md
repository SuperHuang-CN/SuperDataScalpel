# HTTP_API_INPUT · HTTP API 资源输入

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

批处理 HTTP API 输入配置；从一个已启用且具有 SOURCE 用途的 HTTP_API 数据源调用一个或多个已纳管资源，每项按资源声明的 Schema 产生一张 BOUNDED 表。请求、认证、签名、分页和异步轮询由资源定义负责。

## 模式与连线

- 模式：BATCH；类别：INPUT。
- 无入边，至少一条出边；不读取上游 Map。
- 输入有界性见下文。

## 关键配置与行为

- 请求路径、鉴权、签名、分页和异步轮询由资源负责；runtimeParameters 只填该资源声明的运行参数。
- 不将凭据、任意 URL 或外部响应写入 Canvas。只读取资源 Schema，本流程不真实调用来源接口验证结果。

配置定位（只列关键语义，完整字段读取实时契约）：

- `resources`：按顺序调用的资源；至少一项，同一资源 UUID 不能重复且必须属于 dataSourceId。各 outputTableName 在节点内必须唯一；Compiler 只使用声明 Schema，不请求远端。

## 逻辑表 Map 与字段

每个资源按 outputTableName 产生 BOUNDED 表；快照 tables 用资源 UUID 标识，并声明 API_RESOURCE。

## 最小配置示例

前提：UUID 分别为启用的 SOURCE HTTP_API 数据源与所属已纳管资源，资源定义含完整响应字段及调用参数约束。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "dataSourceId": "11111111-1111-4111-8111-111111111111",
  "resources": [
    {
      "resourceId": "22222222-2222-4222-8222-222222222222",
      "outputTableName": "api_orders",
      "runtimeParameters": []
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `API_RESOURCE_NOT_FOUND` | 补齐资源 UUID 对应的安全快照。 |
| `DUPLICATE_API_RESOURCE_SELECTION` | 删除重复选择，输出名另需保持唯一。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
