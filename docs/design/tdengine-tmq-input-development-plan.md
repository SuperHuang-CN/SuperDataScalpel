# TDengine TMQ 输入设计与实现说明

## 1. 状态与范围

- 状态：第一阶段代码已接入，尚未使用真实 TDengine 集群完成联调。
- 节点：`TDENGINE_TMQ_INPUT`，仅支持 `STREAMING`；当前 Canvas 为 `4.5`。
- 数据源：只接受现有 `TDENGINE_WEBSOCKET`；`TDENGINE_RESTFUL` 和 JNI Native 不支持 TMQ。
- Topic：完全由外部系统创建、修改和删除。DataScalpel 只发现、校验和订阅。
- 内容：只接受单一超级表、完整 `*` 投影的纯数据 Topic，不管理或输出子表。
- Offset：Spark Checkpoint 是唯一事实来源，TMQ 自动提交关闭，平台不提交服务端 Group Offset。

第一阶段不支持数据库 Topic、子表 Topic、字段投影、过滤、表达式、聚合、窗口、JOIN、UNION、
子查询或 `WITH META`；不提供 Topic 创建、删除、预览、手动 Consumer Group 或手动 Offset 管理。

## 2. 数据源能力与元数据接口

`DatabaseCapability.TMQ_SUBSCRIBE` 只赋予 `TDENGINE_WEBSOCKET`。两种 TDengine 类型继续共用
`resourceBrowserKind=TDENGINE_SUPERTABLES`；WebSocket 详情页依据该能力显示“超级表 / TMQ Topic”
内层页签，RESTful 只显示超级表。TMQ 直接复用数据源主机、6041 端口、账号、密码和 `useSSL`，不保存
第二套凭据。

管理端提供两个要求 `datasource.metadata` 权限的只读接口：

```http
GET /api/v1/data-sources/{id}/tmq-topics?keyword=
GET /api/v1/data-sources/{id}/tmq-topic?topic={topicName}
```

列表返回 Topic、数据库、超级表、创建时间、支持状态、原因和指纹；详情增加字段及 `MS/US` 时间精度。
数据源不存在返回 404，非 WebSocket TDengine 返回 409，Topic 不存在返回 404。响应不携带原始 Topic
SQL、密码、完整连接属性、数据行或子表名称。外部查询在管理数据库事务之外执行。

## 3. Topic 识别与指纹

`TdEngineTmqMetadataReader` 位于无 Spring/JPA 依赖的 `data-scalpel-dialect`。它读取
`information_schema.ins_topics`，动态识别系统视图列名，并使用受限识别器接受规范形式或语义等价的：

```sql
SELECT * FROM database.supertable
```

识别器不是通用 SQL 执行器。它拒绝缺少定义、包含 `WITH META`、非纯数据类型或不满足单来源完整投影
的 Topic；再通过现有 `TdEngineDialect` 确认来源位于 `ins_stables`、读取 `DESCRIBE`、确认数据库时间
精度并执行无损平台类型映射。纳秒时间戳、JSON TAG、GEOMETRY 和未知物理类型均标记为不支持。

`definitionFingerprint` 当前使用带 `v2:` 前缀的 SHA-256。除规范化 Topic 类型、数据库、超级表、
定义、服务端 Topic ID 和创建时间外，v2 还包含字段顺序、字段名、FIELD/TAG 角色、平台类型参数、
时间精度和最终输出 Schema，因此 TDengine `RELOAD TOPIC` 或超级表结构变化不会被漏检。旧 64 位
十六进制指纹作为 v1 继续兼容：匹配当前 Topic 时允许编译并给出升级 Warning；用户重新选择 Topic 后
写入 v2。列表展示全部 Topic，只有 `supported=true` 的条目可被 Canvas 选择。

## 4. Canvas 契约与运行准备

Canvas 当前版本为 4.5；4.5 为 TMQ 增加可选事件时间配置，读取较低 4.x 小版本后保存会规范化为
当前小版本。节点配置固定为：

```text
dataSourceId
topicName
catalogName
supertableName
topicDefinitionFingerprint
outputTableName
startingOffsets                 EARLIEST | LATEST
maxOffsetsPerVGroupPerTrigger   默认 10000，范围 1..1000000
triggerIntervalSeconds          默认 10，范围 1..300
eventTimeColumn                 可选，必须是 TIMESTAMP
watermarkDelaySeconds           与事件时间同时配置，范围 1..2592000
```

节点分类为 Input，只支持 Streaming，0 入边、至少 1 出边，输出 `UNBOUNDED` Dataset。Schema 来自
Topic 超级表快照，Origin 为 `TDENGINE_TMQ`。平台不自动猜测事件时间；用户显式配置后，Operator 在
来源 Dataset 上调用 `withWatermark`，并同步设置 `CanvasTableSchema.eventTimeColumn/watermarkDelay`。
Compiler 使用同一个 `TdEngineTmqInputNodeOperator` 和 Schema-only I/O 生成零行流 Dataset，不连接
TDengine。

草稿保存只校验 JSON 结构、UUID、枚举和范围。发布、重新启用和运行准备时重新检查：

1. 数据源已启用、具有 `SOURCE` 用途、类型为 WebSocket 且具备 `TMQ_SUBSCRIBE`。
2. Topic 存在且仍受支持，定义指纹、数据库和超级表与节点保存值一致。
3. 最新超级表字段全部可无损映射，并据此生成编译元数据快照。

直接任务引用索引使用 `TDENGINE_TMQ_TOPIC`。数据源删除保护继续由通用
`TaskDataSourceReference` 处理；关联任务展示 Topic 和超级表，但不访问外部 TDengine。

## 5. Manifest 版本

Admin 和 Runner 当前严格使用 `manifestVersion: 22`。`RuntimeDataSource` 包含可空
`RuntimeTdEngineTmqConnection`：

```text
bootstrapServers
username
password
useSsl
```

只有任务引用 TMQ 节点时才生成该字段。连接由保存的数据源字段直接组装，不从 JDBC URL 反向解析。
Manifest 是私有受保护制品；密码和完整连接选项不得进入编译响应、结果、Kafka 事件、管理端运行记录或
日志。非 v22 Manifest 不兼容，升级必须同步发布 Admin、Dispatcher 与 Runner。

## 6. Spark DataSource V2

Task Engine 内置以下 MicroBatch Source，不新增模块或第三方 Spark Connector：

```text
TdEngineTmqTableProvider
TdEngineTmqScan
TdEngineTmqMicroBatchStream
TdEngineTmqInputPartition
TdEngineTmqPartitionReader
```

Offset JSON 为：

```json
{
  "version": 1,
  "topic": "meters_topic",
  "vGroups": { "0": 12031, "1": 9834 }
}
```

每个 VGroup 保存下一条待读 Offset，微批范围是 `[start,end)`。新部署无 Checkpoint 时，EARLIEST 读取
`beginningOffsets`，LATEST 读取 `endOffsets`；恢复始终使用 Spark 传入的 Checkpoint Offset。
`latestOffset` 获取当前 beginning/end offsets，逐 VGroup 按最大跨度截断。WAL beginning 超过
Checkpoint 返回 `TDENGINE_TMQ_OFFSET_EXPIRED`；VGroup 集合、编号或 Offset 回退返回
`TDENGINE_TMQ_VGROUP_CHANGED`。`commit(end)` 只更新 Source 进度内存状态，不调用任何 TMQ commit。

第一阶段每个微批只规划一个 `InputPartition`，Reader 建立一个 Consumer 消费全部 VGroup。Consumer
配置固定为 WebSocket、`enable.auto.commit=false`、按节点首次位置设置 `auto.offset.reset`，并使用
`MapDeserializer`；不再设置 TDengine 3.2 起废弃的 `msg.with.table.name`。内部 Group ID 由
`taskId + sourceNodeId + outputNodeId + writeId` 生成，不包含 definitionVersion、Checkpoint 世代或
完整 Checkpoint 路径；因此每条 Sink 都获得完整独立数据流，同时定义升级不会持续消耗 Topic 每组
100 个 Consumer Group 的配额。Client ID 仅用于诊断并带运行 attempt 后缀。

普通停止不删除 Group。定义永久替换 Topic、删除输出或改变稳定输出身份时，管理端将旧 Group 写入
独立清理队列，后台执行 `DROP CONSUMER GROUP IF EXISTS ... ON ...`，不使用 `FORCE`。执行前再次检查
当前定义是否仍引用相同数据源、Topic 和 Group；失败按退避重试并只形成运行告警，不阻断定义保存、
发布、启动、停止或当前查询运行。

Reader 订阅后最多等待 30 秒取得与 Checkpoint 一致的全部 VGroup，随后对每个 VGroup `seek(start)`；
建立分配时 poll 到的记录全部丢弃。Reader 固定读取到本批 end offset，poll 超时 1 秒，有剩余 Offset
但连续 60 秒无进展则以可重试错误失败。关闭时始终 unsubscribe 并 close，不跨线程或跨微批复用
Consumer。

## 7. 数据转换与失败语义

`MapDeserializer` 的字段集合必须与 Manifest 超级表 Schema 完全一致。缺失、额外字段或值转换失败
均返回 `TDENGINE_TMQ_SCHEMA_MISMATCH`。当前支持 Boolean、整数、浮点、Decimal、String、Binary、
Date 和毫秒/微秒 Timestamp；Timestamp 转换为 Spark UTC 微秒内部值。消息只输出超级表字段和 TAG，
不输出 Topic、数据库、子表、VGroup、Offset 或消息类型；任何非数据消息返回
`TDENGINE_TMQ_MESSAGE_TYPE_UNSUPPORTED`。

稳定错误包括：

```text
TDENGINE_TMQ_TOPIC_NOT_FOUND
TDENGINE_TMQ_TOPIC_UNSUPPORTED
TDENGINE_TMQ_TOPIC_CHANGED
TDENGINE_TMQ_AUTHENTICATION_FAILED
TDENGINE_TMQ_NETWORK_ERROR
TDENGINE_TMQ_ASSIGNMENT_TIMEOUT
TDENGINE_TMQ_OFFSET_EXPIRED
TDENGINE_TMQ_VGROUP_CHANGED
TDENGINE_TMQ_SCHEMA_MISMATCH
TDENGINE_TMQ_MESSAGE_TYPE_UNSUPPORTED
TDENGINE_TMQ_POLL_FAILED
```

认证、Topic/Schema、Offset 和 VGroup 问题不可重试；网络、分配超时和可恢复轮询失败标记为可重试。
当前平台仍不自动重试真实运行。Source Checkpoint 不改变现有 Sink 语义，不宣称端到端 Exactly Once。

## 8. 运行进度、前端与安全边界

Canvas Streaming Palette 增加“TDengine TMQ 输入”。数据源候选由启用状态、SOURCE 用途、WebSocket
类型和 `TMQ_SUBSCRIBE` 能力共同过滤。Topic 下拉实时加载，禁用不受支持项并展示原因；选择后自动
回填数据库、超级表和指纹。字段详情使用按需 Modal，Inspector 只保留输出表、首次位置和每 VGroup
最大 Offset 跨度，以及可选事件时间和 Watermark。节点卡片展示数据源、Topic、超级表、
EARLIEST/LATEST、事件时间和字段数。

Runner 从 Spark `SourceProgress.startOffset/endOffset` 解析版本化 TMQ Offset，仅上报批次行数、速率、
VGroup 数量、批次 Offset 跨度和已提交 Offset 摘要。平台没有读取各 VGroup 服务端最新端点，因此不把
该摘要表述为准确积压量或 Lag。旧 Group 清理的待处理数与失败数在实时运行区紧凑展示。

Spark 选项脱敏规则覆盖 password/passwd/token/credential/key 和 TDengine 密码字段。安全日志只记录
数据源 ID、Topic、超级表、首次位置和字段数量；禁止记录密码、完整 Properties、Checkpoint URI、原始
Topic SQL 或数据值。

## 9. 发布与验证

保持 `com.taosdata.jdbc:taos-jdbcdriver:3.6.3`，不引入 Native Client。Task Runner 本地包和集群精简
包都包含 TDengine 驱动及 `Java-WebSocket`。上线前停止或排空旧 Runner；Offset 过期或 VGroup 变化后
以新的 Checkpoint 世代建立部署，不提供通用 Checkpoint 删除接口，也不通过服务端 Group Offset 绕过
Spark Checkpoint。

静态构建不能替代真实环境验收。目标 TDengine 环境仍需验证系统视图字段、Topic 类型值、WebSocket
及 SSL、账号权限、字段/TAG 反序列化、重启续读、WAL 过期、VGroup 变化、大积压限流和多 Sink 独立
Group。所有页面、接口、Manifest 摘要、日志及错误详情必须继续进行凭据与数据行泄漏检查。
