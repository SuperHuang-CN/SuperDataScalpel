# TDENGINE_TMQ_INPUT · TDengine TMQ 输入

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

流式 TDengine TMQ 输入配置；订阅一个外部管理的完整超级表数据 Topic，并以保存的数据库、超级表和结构指纹防止来源漂移。输出为包含超级表普通字段与 TAG 的无界表，不追加 Topic、子表、VGroup 或 Offset 技术列。

## 模式与连线

- 模式：STREAMING；类别：INPUT。
- 无入边，至少一条出边；不读取上游 Map。
- 输入有界性见下文。

## 关键配置与行为

- 必须是具有 TMQ_SUBSCRIBE 能力的 TDENGINE_WEBSOCKET 数据源，RESTful 不支持；只接受单超级表完整 * 纯数据 Topic，不接受投影、过滤或 WITH META。
- 读取 GET /api/v1/data-sources/{id}/tmq-topics 和 /tmq-topic 的当前契约；Topic 的数据库、超级表和结构指纹必须一致，不自动创建 Topic。
- eventTimeColumn 和 watermarkDelaySeconds 成对配置；不用事件时间时均省略。maxOffsetsPerVGroupPerTrigger 控制每 VGroup 微批上限。
- 不追加 Topic、子表、VGroup 或 offset 技术字段；旧指纹升级 WARNING 需要说明并按元数据更新。

配置定位（只列关键语义，完整字段读取实时契约）：

- `topicName`：必填的外部 TMQ Topic 名称；必须是单一超级表完整 * 投影的纯数据 Topic，不支持数据库 Topic、子表、投影、过滤、JOIN、聚合或 WITH META。
- `catalogName`：必填的 Topic 来源数据库 Catalog 名称；必须与元数据快照及当前 Topic 定义一致。
- `supertableName`：必填的 Topic 来源超级表名；必须与元数据快照及当前 Topic 定义一致。
- `topicDefinitionFingerprint`：必填的 64 位小写 SHA-256 Topic/超级表定义指纹；发布、重新启用和运行准备时与当前定义复核，变化时拒绝运行，旧版合法指纹会给出升级提示。

## 逻辑表 Map 与字段

输出普通字段与 TAG 组成的 UNBOUNDED 表；只有完整合法的事件时间配置才传播 Watermark。

## 最小配置示例

前提：UUID 为启用的受支持 TDengine 来源，外部已准备完整超级表 Topic；示例指纹只是格式演示，必须从当前 tmq-topic 元数据响应取得真实值。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "dataSourceId": "11111111-1111-4111-8111-111111111111",
  "topicName": "meters_topic",
  "catalogName": "telemetry",
  "supertableName": "meters",
  "topicDefinitionFingerprint": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "outputTableName": "meter_events",
  "startingOffsets": "LATEST",
  "maxOffsetsPerVGroupPerTrigger": 10000,
  "triggerIntervalSeconds": 10
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `TDENGINE_TMQ_TOPIC_CHANGED` | 重新获取当前 Topic 结构并重新审查设计。 |
| `TDENGINE_TMQ_EVENT_TIME_CONFIGURATION_INCOMPLETE` | 成对设置或一起移除事件时间配置。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
