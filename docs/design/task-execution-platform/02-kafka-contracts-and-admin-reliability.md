# 02 Kafka 执行契约与 Admin 可靠消息开发设计

## 1. 目标

本阶段建立 Admin、Dispatcher 和 Runner 共享的 Kafka JSON 契约，并在 Admin 中实现 Outbox 发布和执行事件幂等消费。阶段结束时允许 Dispatcher 尚未存在，但消息可以稳定序列化、持久化、重试和回放。

依赖：[01 计算引擎管理](01-compute-engine-management.md)。

## 2. 消息拓扑

每个计算引擎配置两个独占输入 Topic，所有 Dispatcher 共用一个 Admin 事件 Topic：

```text
commandTopic       Admin ─────────> Dispatcher
runnerEventTopic   Runner ────────> Dispatcher
adminEventTopic    Dispatcher ────> Admin
```

约束：

- `commandTopic` 和 `runnerEventTopic` 不能被两个 ACTIVE 计算引擎复用。
- `adminEventTopic` 可以共享，Admin 使用一个稳定 Consumer Group。
- 所有执行消息的 Kafka Key 固定为 `executionId` 字符串。
- 同一 executionId 的 Submit 和 Cancel 必须进入同一 Topic、同一 Key。
- Topic 默认不依赖 Kafka 自动创建；生产环境由运维预建，注册时检查存在性。

## 3. 公共契约位置

契约放入 `data-scalpel-contracts`：

```text
cn.superhuang.data.scalpel.contract.execution
├─ ExecutionMessageEnvelope
├─ ExecutionCommand
├─ DispatcherExecutionEvent
├─ RunnerExecutionEvent
├─ ExecutionArtifactLocation
├─ SafeExecutionError
└─ enums
```

只使用 Java record、enum 和 sealed interface，不依赖 Spring Kafka、JPA、Spark 或业务 Entity。Jackson 多态类型由明确的 `messageType` 判别，未知字段和未知枚举按严格模式拒绝。

`SafeExecutionError` 统一携带 `code/message/category/retryable/nodeId/nodeType/nodeName/phase/sqlState/diagnosticId`。Dispatcher 自身错误允许节点字段和 SQLState为空，但类别、阶段和诊断 ID始终必填；Runner节点错误必须携带完整节点身份。该对象只包含可向管理端展示的安全信息，不包含调用栈。Dispatcher实时事件和 HTTP对账响应必须返回同一结构，避免 Admin在对账路径丢失节点诊断。

## 4. 公共消息头

所有消息包含：

```json
{
  "messageVersion": 1,
  "messageId": "uuid",
  "messageType": "SUBMIT_EXECUTION",
  "occurredAt": "2026-07-17T12:00:00Z",
  "engineId": "uuid",
  "executionId": "uuid",
  "runId": "uuid",
  "attempt": 1
}
```

校验：

- `messageVersion` 第一阶段只接受 1。
- 所有 ID 是非空 UUID。
- `attempt` 固定从 1 开始。
- `occurredAt` 使用 UTC Instant。
- 业务载荷不得覆盖公共身份字段。

Kafka Header 同时设置：

```text
datascalpel-message-version=1
datascalpel-message-type=<messageType>
datascalpel-engine-id=<engineId>
```

JSON 顶层字段是权威值；Header 用于运维观察和未来路由，二者不一致时拒绝消息。

## 5. Admin 到 Dispatcher 命令

### 5.1 SUBMIT_EXECUTION

```json
{
  "messageVersion": 1,
  "messageId": "uuid",
  "messageType": "SUBMIT_EXECUTION",
  "occurredAt": "2026-07-17T12:00:00Z",
  "engineId": "uuid",
  "executionId": "uuid",
  "runId": "uuid",
  "attempt": 1,
  "taskId": "uuid",
  "taskType": "SPARK_CANVAS",
  "definitionVersion": 3,
  "deadlineAt": "2026-07-17T13:00:00Z",
  "artifacts": {
    "manifestKey": "task-runs/{runId}/attempts/1/manifest.json",
    "manifestSha256": "64-lowercase-hex",
    "resultKey": "task-runs/{runId}/attempts/1/result.json",
    "logKey": "task-runs/{runId}/attempts/1/console.log"
  }
}
```

命令不包含：

- manifest 正文。
- 数据库密码。
- MinIO AccessKey/SecretKey。
- 预签名 URL。
- Kafka 用户名或密码。
- Dispatcher Backend 参数。

`manifestKey`、`resultKey`、`logKey` 必须严格符合本次 `runId/attempt` 路径，Dispatcher 不接受任意对象 Key。

### 5.2 CANCEL_EXECUTION

```json
{
  "messageVersion": 1,
  "messageId": "uuid",
  "messageType": "CANCEL_EXECUTION",
  "occurredAt": "2026-07-17T12:05:00Z",
  "engineId": "uuid",
  "executionId": "uuid",
  "runId": "uuid",
  "attempt": 1,
  "reason": "用户请求停止"
}
```

`reason` 是安全短文本，最长 500，不允许传递异常堆栈。

## 6. Runner 到 Dispatcher 事件

Runner 只发布原始运行信号，Dispatcher 才能发布 Admin 可消费的权威执行事件。

### 6.1 RUNNER_STARTED

```json
{
  "messageVersion": 1,
  "messageId": "uuid",
  "messageType": "RUNNER_STARTED",
  "occurredAt": "2026-07-17T12:01:00Z",
  "engineId": "uuid",
  "executionId": "uuid",
  "runId": "uuid",
  "attempt": 1,
  "sparkApplicationId": "local-..."
}
```

### 6.2 RUNNER_RESULT_AVAILABLE

Runner 必须先上传结果再发送事件：

```json
{
  "messageVersion": 1,
  "messageId": "uuid",
  "messageType": "RUNNER_RESULT_AVAILABLE",
  "occurredAt": "2026-07-17T12:03:00Z",
  "engineId": "uuid",
  "executionId": "uuid",
  "runId": "uuid",
  "attempt": 1,
  "resultKey": "task-runs/{runId}/attempts/1/result.json",
  "resultSha256": "64-lowercase-hex"
}
```

### 6.3 RUNNER_FAILED

仅用于 Runner 尚能访问 Kafka但无法形成合法 result 的兜底场景：

```json
{
  "messageType": "RUNNER_FAILED",
  "error": {
    "code": "MANIFEST_DOWNLOAD_FAILED",
    "message": "无法下载任务清单"
  }
}
```

错误中禁止出现预签名 URL、密码、JDBC URL、Spark Plan 或本地路径。

## 7. Dispatcher 到 Admin 事件

状态类型：

```text
EXECUTION_ACCEPTED
EXECUTION_REJECTED
EXECUTION_SUBMITTED
EXECUTION_RUNNING
EXECUTION_SUCCEEDED
EXECUTION_FAILED
EXECUTION_TIMED_OUT
EXECUTION_CANCELLED
EXECUTION_LOST
```

事件示例：

```json
{
  "messageVersion": 1,
  "messageId": "uuid",
  "messageType": "EXECUTION_RUNNING",
  "occurredAt": "2026-07-17T12:01:00Z",
  "engineId": "uuid",
  "executionId": "uuid",
  "runId": "uuid",
  "attempt": 1,
  "sequence": 3,
  "backendType": "YARN",
  "externalExecutionId": "application_...",
  "trackingUrl": "http://...",
  "startedAt": "2026-07-17T12:01:00Z",
  "endedAt": null,
  "affectedRows": null,
  "error": null
}
```

规则：

- `sequence` 由 Dispatcher 对每个 executionId/attempt 从 1 递增。
- 只有 Dispatcher 事件可以修改 Admin TaskRun 状态。
- `trackingUrl` 允许为空，必须经过协议和长度校验。
- 终态事件可以包含受影响行数和结构化安全错误，不包含完整 result.json或异常调用栈。
- 第一个被 Dispatcher 账本接受的终态是权威终态。

## 8. Admin Outbox

新增 `TaskExecutionOutboxMessage` Entity：

| 字段 | 含义 |
| --- | --- |
| `id` | messageId，同时是主键 |
| `aggregateId` | runId |
| `executionId` | Kafka Key |
| `engineId` | 路由校验 |
| `topic` | 创建消息时固化的目标 Topic |
| `messageType` | SUBMIT/CANCEL |
| `payload` | 完整 JSON，使用 LONG32VARCHAR |
| `state` | PENDING/PUBLISHING/PUBLISHED/FAILED |
| `attempts` | 发布次数 |
| `nextAttemptAt` | 下次重试时间 |
| `publishedAt` | 成功时间 |
| `lastError` | 脱敏短错误 |

创建 TaskRun 和 SUBMIT Outbox 必须在同一个管理数据库事务中完成。业务事务不直接等待 Kafka Broker。

发布器：

- 使用定时批处理读取到期 PENDING 消息。
- PostgreSQL 使用 JPA Pessimistic Lock 或稳定 claim 状态避免多实例重复并发发布。
- Producer 使用 `acks=all`、幂等 Producer 和有限单次发送超时。
- 发送成功后短事务标记 PUBLISHED。
- 发送成功但更新数据库前崩溃会造成重复投递，由 Dispatcher Inbox 去重。
- 指数退避有上限；持续失败保留记录并暴露监控，不删除消息。

## 9. Admin Inbox 与事件应用

新增 `DispatcherEventInboxMessage`：

```text
messageId UUID unique
executionId UUID
runId UUID
attempt int
sequence long
receivedAt Instant
processedAt Instant
processingState RECEIVED/PROCESSED/REJECTED
safeError String
```

消费步骤：

1. 严格反序列化并验证 Header/JSON 身份一致。
2. 按 messageId 插入 Inbox；重复键直接按幂等成功处理。
3. 锁定 runId 对应 TaskRun。
4. 校验 engineId、executionId、attempt。
5. 只应用大于 `lastDispatcherEventSequence` 的事件。
6. 应用合法状态迁移并更新 sequence。
7. 标记 Inbox 已处理。
8. 数据库事务提交后再提交 Kafka Offset。

无法建立 Kafka 与 PostgreSQL 分布式事务，因此依赖 Inbox、TaskRun 序号和幂等状态迁移获得至少一次安全性。

Listener 只把 JSON、协议版本、Header/Body 身份等确定性坏消息视为不可重试并投递 DLT。JPA、数据库连接和其他基础设施异常必须继续抛出，让 Kafka 重投；不能把数据库失败吞掉后提交源 Offset。坏消息的 DLT 发送也必须等待 Broker 确认，DLT 发送失败时同样不提交源 Offset。

## 10. Admin 状态映射

Dispatcher 内部状态比 Admin 业务状态更细。映射如下：

| Dispatcher 事件 | Admin TaskRun 状态 |
| --- | --- |
| ACCEPTED、SUBMITTED | QUEUED |
| RUNNING | RUNNING |
| SUCCEEDED | SUCCESS |
| REJECTED、FAILED、LOST | FAILED |
| TIMED_OUT | TIMED_OUT |
| CANCELLED | CANCELLED |

发布 CANCEL 命令时 Admin 先把可取消 Run 改为 `CANCEL_REQUESTED`。迟到的 RUNNING 事件不能把它恢复为普通 RUNNING；最终由 CANCELLED 或其他已经形成的合法终态收敛。

## 11. Kafka 配置

Admin 增加独立执行消息配置，不复用业务数据源管理中的 Kafka DataSource Entity：

```properties
data-scalpel.execution.kafka.bootstrap-servers=127.0.0.1:9092
data-scalpel.execution.kafka.security-protocol=PLAINTEXT
data-scalpel.execution.kafka.consumer-group=data-scalpel-admin-execution-v1
data-scalpel.execution.kafka.admin-event-topics=datascalpel.execution.event
data-scalpel.execution.kafka.outbox.batch-size=50
data-scalpel.execution.kafka.outbox.poll-interval=500ms
```

支持通过标准 Spring Kafka properties 配置 SSL/SASL。密码只来自环境变量或外部密钥挂载，不保存到系统配置表。

## 12. 日志、指标与死信

日志允许记录 messageId、executionId、runId、engineId、messageType、topic、partition 和 offset。禁止记录 payload。

第一阶段不自动把业务拒绝消息投递到另一个执行 Topic。无法反序列化或身份不一致的消息发送到固定 DLT：

```text
datascalpel.execution.invalid-message.v1
```

DLT 只保存原始 Kafka Bytes 和安全失败原因，读取权限限制给管理员。

关键指标：

```text
admin.execution.outbox.pending
admin.execution.outbox.oldest.age
admin.execution.event.processed
admin.execution.event.rejected
admin.execution.event.lag
```

## 13. 测试计划

- 三类消息有效 JSON 往返和未知字段拒绝。
- messageVersion、UUID、attempt、SHA-256 和对象 Key 校验。
- Kafka Key 和 Header 生成。
- TaskRun 与 Outbox 同事务提交/回滚。
- Producer 成功后数据库更新失败导致重复发送时的安全性。
- Inbox messageId 去重、sequence 去重、身份不匹配拒绝。
- 乱序 RUNNING/终态、重复终态和取消竞态。
- Kafka 不可用时运行请求仍保留可重试 Outbox。
- 使用 Kafka Testcontainers 验证真实发送、消费和 Offset 行为。

## 14. 阶段退出条件

- `data-scalpel-contracts` 中的 v1 消息协议冻结。
- Admin 可以可靠地产生 SUBMIT/CANCEL Outbox。
- Admin 可以幂等消费 Dispatcher 事件。
- Kafka 暂时不可用不会丢失 TaskRun 或执行命令。
- 不再设计任何 Dispatcher 到 Admin 的 HTTP 回调契约。
