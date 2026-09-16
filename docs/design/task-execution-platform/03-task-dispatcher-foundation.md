# 03 Task Dispatcher 工程与持久化调度内核开发设计

## 1. 目标

新增独立可执行模块 `data-scalpel-task-dispatcher`，实现 Dispatcher 控制面、Kafka 命令接入、独立 PostgreSQL 执行账本、FIFO 准入、Outbox 事件和重启恢复框架。本阶段先提供可测试的 Backend SPI 和 Fake Backend，不接真实 Docker/YARN/Kubernetes。

依赖：

- [01 计算引擎管理](01-compute-engine-management.md)
- [02 Kafka 执行契约与 Admin 可靠消息](02-kafka-contracts-and-admin-reliability.md)

## 2. Maven 模块

根 reactor 新增：

```text
data-scalpel-task-dispatcher
```

技术栈：

- Java 21。
- Spring Boot 4.1.0。
- Spring MVC。
- Spring Data JPA / Hibernate。
- PostgreSQL。
- Spring Kafka。
- JDK `ProcessBuilder`，后续调用 Docker、spark-submit、yarn、kubectl。

Dispatcher 不依赖 `data-scalpel-business`、`data-scalpel-admin`、`data-scalpel-task-engine` 或 `data-scalpel-dialect`。它只依赖稳定的 `data-scalpel-contracts`。

## 3. 包结构

```text
cn.superhuang.data.scalpel.dispatcher
├─ TaskDispatcherApplication
├─ config
├─ management
├─ messaging
│  ├─ command
│  ├─ runner
│  └─ outbox
├─ domain
├─ repository
├─ service
├─ backend
├─ artifact
├─ lifecycle
└─ web
   ├─ resource
   └─ response
```

职责：

- `management`：注册、Drain、实例身份和依赖检查。
- `messaging`：Kafka Listener、严格 JSON、Inbox 和 Outbox。
- `service`：执行状态机、准入、超时、恢复和协调。
- `backend`：不同运行环境的提交、观测、取消和日志能力。
- `artifact`：MinIO 对象检查、预签名 URL和结果读取。
- `web`：只提供 Admin 主动调用的控制面。

## 4. 数据库和 Schema

Dispatcher 使用与 Admin 相同的数据库连接账户，数据库 URL 和 Schema 独立配置：

```properties
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/data_scalpel
spring.datasource.username=${与 Admin 相同}
spring.datasource.password=${与 Admin 相同}
spring.datasource.hikari.schema=${DATASCALPEL_TASK_DISPATCHER_DB_SCHEMA:dispatcher}
spring.jpa.properties.hibernate.default_schema=${DATASCALPEL_TASK_DISPATCHER_DB_SCHEMA:dispatcher}
spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true
spring.jpa.hibernate.ddl-auto=update
```

数据库用户必须具有创建目标 Schema 的权限；也可以由运维提前创建：

```sql
CREATE SCHEMA IF NOT EXISTS dispatcher;
```

边界：

- Dispatcher Entity 不映射 Admin 表。
- 不建立跨 Schema 外键。
- 不读取或更新 Admin TaskRun。
- Admin 也不直接查询 Dispatcher 表。
- 本阶段不引入 Flyway，遵循工程第一版 `ddl-auto=update` 基线。

## 5. 持久化模型

### 5.1 DispatcherRegistration

单个 Dispatcher 进程只允许存在一个当前注册：

```text
id UUID
engineId UUID unique
dispatcherInstanceId UUID
backendType enum
state INACTIVE/ACTIVE/DRAINING/ERROR
commandTopic
runnerEventTopic
adminEventTopic
maxQueuedExecutions
maxConcurrentSubmissions
maxInFlightApplications
registeredAt
updatedAt
lastError
version
```

`dispatcherInstanceId` 首次启动时生成并写入 Dispatcher 自己的数据库，以后进程重启保持不变。数据库被重建时身份变化，Admin 必须人工确认后重新注册。

### 5.2 DispatcherTaskExecution

```text
id UUID
engineId UUID
executionId UUID
runId UUID
taskId UUID
attempt int
taskType SPARK_CANVAS
definitionVersion int
requestFingerprint String
backendType enum
state enum
externalExecutionId String
trackingUrl String
manifestKey String
manifestSha256 String
resultKey String
logKey String
deadlineAt Instant
cancelRequested boolean
eventSequence long
safeErrorCode String
safeErrorMessage String
queuedAt
submissionStartedAt
submittedAt
startedAt
endedAt
lastObservedAt
createdAt
updatedAt
version
```

唯一约束：

```text
(execution_id, attempt)
```

同一个 executionId/attempt 收到完全相同的 Submit 是幂等重复；指纹不同则记录协议冲突并发布 REJECTED，不覆盖已有执行。

### 5.3 DispatcherMessageInbox

对 Admin 命令和 Runner 事件统一去重：

```text
messageId UUID unique
messageType
topic
partition
offset
executionId
receivedAt
processedAt
state
safeError
```

### 5.4 DispatcherEventOutbox

```text
messageId UUID
executionId UUID
engineId UUID
sequence long
topic
payload LONG32VARCHAR
state
attempts
nextAttemptAt
publishedAt
lastError
```

同一次执行的 `sequence` 分配、账本状态更新和 Outbox 插入必须在同一个数据库事务中完成。

## 6. Dispatcher 状态机

```text
QUEUED
SUBMITTING
SUBMITTED
RUNNING
CANCEL_REQUESTED
SUCCESS
FAILED
TIMED_OUT
CANCELLED
LOST
```

主要流转：

```text
SUBMIT command
  → QUEUED
  → SUBMITTING
  → SUBMITTED
  → RUNNING
  → SUCCESS | FAILED | TIMED_OUT | CANCELLED | LOST
```

规则：

- 终态不可再改变，第一个合法终态生效。
- CANCEL 到达 QUEUED 时直接进入 CANCELLED，不调用 Backend。
- CANCEL 到达 SUBMITTING 时设置 `cancelRequested`，Backend 返回 Handle 后立即补偿取消。
- CANCEL 到达 SUBMITTED/RUNNING 时进入 CANCEL_REQUESTED，并调用 Backend。
- Deadline 到达后执行取消流程，最终进入 TIMED_OUT；迟到的 Runner 成功结果不得覆盖。
- Runner 事件只能推进对应 executionId/runId/attempt，身份不一致拒绝。

## 7. Backend 接口

```java
public interface TaskExecutionBackend {
    ComputeBackendType type();
    BackendReadiness readiness();
    BackendSubmission submit(ExecutionLaunch launch) throws BackendException;
    BackendStatus inspect(ExternalExecutionHandle handle) throws BackendException;
    void cancel(ExternalExecutionHandle handle) throws BackendException;
    BackendLog collectLog(ExternalExecutionHandle handle) throws BackendException;
    Optional<ExternalExecutionHandle> recover(ExecutionIdentity identity) throws BackendException;
}
```

关键模型：

```text
ExecutionIdentity: engineId/executionId/runId/attempt
ExecutionLaunch: 身份、临时 launch 文件、deadline、资源配置
ExternalExecutionHandle: backendType + stable external id
BackendStatus: PENDING/RUNNING/SUCCEEDED/FAILED/UNKNOWN
BackendLog: bytes + truncated flag
BackendReadiness: ready + safe issues
```

接口不出现 Spark Dataset、Admin Entity 或 Kafka ConsumerRecord。

## 8. 命令消费与准入

### 8.1 动态 Listener

Dispatcher 未注册时不消费执行命令。ACTIVE 注册成功后根据持久化 Topic 启动：

- command Listener。
- runnerEvent Listener。

进程重启后读取 `DispatcherRegistration` 自动恢复 Listener。

DRAINING 时继续消费命令：

- 新 SUBMIT 生成 `EXECUTION_REJECTED`，原因 `ENGINE_DRAINING`。
- CANCEL 正常处理。
- Runner 事件正常处理。

### 8.2 FIFO 队列

Kafka Listener 只负责把合法 Submit 持久化为 QUEUED，不直接调用 Backend。

Admission Worker：

1. 锁定注册记录，序列化同一引擎的准入计算。
2. 检查 `SUBMITTING` 数量。
3. 检查 in-flight 数量。
4. 以 `queuedAt, executionId` 稳定排序选择最早 QUEUED。
5. 悲观锁定记录并改为 SUBMITTING。
6. 事务提交后调用 Backend。
7. 短事务保存 Handle、SUBMITTED 和事件。

外部 Backend 调用不得运行在数据库事务中。

队列满时 Submit 仍然被 Kafka 正常消费，但产生 `EXECUTION_REJECTED/CAPACITY_EXCEEDED`，而不是让 Kafka Partition 永久阻塞。

## 9. 控制面 HTTP

### 9.1 健康检查

```http
GET /health/live
GET /health/ready
```

ready 检查：

- PostgreSQL 可用。
- Kafka Producer 和所需 Topic 可用。
- MinIO Bucket 可用。
- 固定 Backend readiness 为 UP。
- ACTIVE 注册对应的 Listener 已启动。

### 9.2 信息和注册

```http
GET  /api/v1/dispatcher/info
GET  /api/v1/dispatcher/registration
POST /api/v1/dispatcher/registration/actions/activate
POST /api/v1/dispatcher/registration/actions/drain
POST /api/v1/dispatcher/registration/actions/deactivate
GET  /api/v1/dispatcher/runtime-overview
GET  /api/v1/task-executions?scope=ACTIVE|QUEUED|RECENT&page=0&size=20
GET  /api/v1/task-executions/{executionId}
```

注册幂等：

- 相同 engineId 和相同配置重复 Activate 返回当前注册。
- 同一实例已 ACTIVE 或 DRAINING 但请求另一个 engineId，返回 409。
- 当前注册为 INACTIVE 或 ERROR 时，允许当前请求重新激活或由另一个 engineId 接管。
- ACTIVE 状态不能直接替换 Topic 或策略，必须先 Drain/Deactivate。

反注册：

- 非强制模式要求没有 QUEUED、SUBMITTING、SUBMITTED、RUNNING、CANCEL_REQUESTED。
- 强制模式取消排队和活动任务，完成状态收敛后变为 INACTIVE。

所有错误使用 RFC 9457 Problem Detail。

### 9.3 运行态只读投影

`GET /api/v1/dispatcher/runtime-overview` 返回当前 Dispatcher 的稳定实例身份、版本、后端类型、
注册状态、Backend/制品存储/Kafka/Kafka Listener 依赖健康、准入容量、各执行状态占用、采集
时间和当前后端资源配置。资源配置按 backend type 判别：

- Local Docker：镜像、容器 CPU/内存限制、Runner JVM Heap。
- YARN：队列、Driver/Executor 内存、Executor Core 和数量。
- Kubernetes：Namespace、镜像、Driver/Executor 内存、Executor Core 和实例数。

响应不得包含 Token、工作目录、制品地址、凭据或任意动态执行参数。该接口中的资源值是 Dispatcher
固定部署配置，不能解释为实时 CPU/内存使用率。具体执行则持久化其不可变的运行资源快照：JAR 请求缺省时
使用注册计算引擎的默认值，超出注册上限的请求会在创建容器或提交 Spark 应用前以 `RESOURCE_LIMIT_EXCEEDED` 终态拒绝。

`GET /api/v1/task-executions` 使用固定 `scope` 投影，并使用 `page`、`size` 分页：

| scope | 状态范围 | 稳定排序 | 额外字段 |
| --- | --- | --- | --- |
| `ACTIVE` | `SUBMITTING`、`SUBMITTED`、`RUNNING`、`CANCEL_REQUESTED` | `queuedAt, executionId` 升序 | 无 |
| `QUEUED` | `QUEUED` | `queuedAt, executionId` 升序 | 全局绝对 `queuePosition` |
| `RECENT` | 所有终态 | `endedAt, executionId` 降序 | 无 |

执行摘要包含任务、运行和执行身份，任务类型、定义版本、提交开始/提交完成/最后观测时间、
状态、追踪地址和经过脱敏的错误摘要。该查询只以 Dispatcher PostgreSQL 执行账本为事实来源；
Kafka Lag 不参与队列统计或排序。

## 10. MinIO 制品适配器

Dispatcher 配置独立 MinIO Client，但与 Admin 指向同一个私有 Bucket：

```properties
data-scalpel.dispatcher.artifact.endpoint=http://127.0.0.1:19000
data-scalpel.dispatcher.artifact.runner-endpoint=http://host.docker.internal:19000
data-scalpel.dispatcher.artifact.bucket=data-scalpel
data-scalpel.dispatcher.artifact.presign-duration=2h
```

职责：

- 验证 manifest 对象存在和大小限制。
- 在真正提交前生成新的 manifest GET、result PUT 预签名 URL。
- 读取并校验 result.json。
- 上传 Dispatcher 收集的 console.log。

MinIO Secret 只通过环境变量或外部配置注入，不写入注册请求和数据库。

## 11. 轮询、超时和恢复

定时协调器：

- 领取 QUEUED。
- 检查 SUBMITTED/RUNNING Backend 状态。
- 检查 deadline。
- 处理 CANCEL_REQUESTED。
- 重试 Event Outbox。

重启恢复：

- QUEUED：继续排队。
- SUBMITTING 且有 Handle：按 SUBMITTED 恢复。
- SUBMITTING 无 Handle：调用 Backend `recover(identity)`；找到则保存 Handle，找不到时经过配置的提交不确定宽限期后标记 LOST，禁止盲目重提。
- SUBMITTED/RUNNING：使用 Handle 重新观测。
- CANCEL_REQUESTED：继续取消。
- 已请求强制终止：持续执行 Backend 硬终止；已有外部 Handle 时最多确认 30 秒，仍无法确认则进入
  `LOST`，并使用 `EXECUTION_TERMINATION_UNCONFIRMED` 表示业务结果不确定；后台继续终止和清理。
- 终态：保持业务结果不变，继续确认外部终止、归档日志、清理资源及重发尚未完成的 Outbox。

## 12. 关闭顺序

1. readiness 变为 DOWN。
2. 停止接收新 HTTP 控制命令。
3. 停止 command Listener 拉取新消息。
4. 停止 Admission Worker。
5. 等待正在进行的 submit 调用到达安全点。
6. 提交已处理 Kafka Offset。
7. 尽力刷新 Outbox。
8. 关闭 Listener、线程池和 Spring Context。

普通进程关闭不主动取消外部运行任务；重启后重新挂接。只有显式强制反注册才取消。

## 13. 配置

核心配置：

```properties
data-scalpel.dispatcher.backend=LOCAL_DOCKER
data-scalpel.dispatcher.token=${DATASCALPEL_TASK_DISPATCHER_TOKEN}
data-scalpel.dispatcher.admission-poll-interval=500ms
data-scalpel.dispatcher.observation-poll-interval=5s
data-scalpel.dispatcher.outbox-poll-interval=500ms
data-scalpel.dispatcher.submission-uncertain-grace=2m
data-scalpel.dispatcher.observation-failure-grace=5m
data-scalpel.dispatcher.deactivation-timeout=30s
data-scalpel.dispatcher.log-max-bytes=20MB
```

Token 为空拒绝启动。一个进程只能配置一个 backend，不能由单次消息选择。

Backend 单次观测失败只记录首次失败时间并继续重试；连续不可观测超过 `observation-failure-grace` 才进入 `LOST`。强制反注册先切换为 `DRAINING` 并请求取消全部活动执行，只有状态在 `deactivation-timeout` 内收敛后才能变为 `INACTIVE`；超时保持 `DRAINING` 并返回冲突。

任务级强制终止与强制反注册不是同一操作。前者在执行账本记录
`force_terminate_requested_at`：Local Docker 直接 kill，YARN 使用 application kill，Kubernetes 以零宽限期
删除 Driver 和 Executor。确认终止后发布 `EXECUTION_CANCELLED` 和
`EXECUTION_FORCE_TERMINATED`；确认超时则发布 `EXECUTION_LOST`，不得永久停留在
`CANCEL_REQUESTED`。

## 14. 测试计划

- 独立 `dispatcher` Schema 初始化，确认无跨 Schema访问。
- 注册、重复注册、实例冲突、Drain 和反注册。
- Inbox 重复命令和指纹冲突。
- FIFO 顺序、队列容量、并发提交和 in-flight 限制。
- Submit 外部调用不在 JPA 事务内。
- Cancel 在 QUEUED/SUBMITTING/RUNNING 各状态的补偿。
- Deadline 和第一终态生效。
- Fake Backend 下的完整 ACCEPTED→SUBMITTED→RUNNING→SUCCESS。
- 进程重启后 QUEUED、SUBMITTING、RUNNING 恢复。
- Outbox 重复发布安全性。
- HTTP Token、Problem Detail 和 readiness。

## 15. 阶段退出条件

- Dispatcher 可以独立构建和启动。
- 注册信息、命令、执行账本和事件全部持久化在 `dispatcher` Schema。
- Fake Backend 可以完成完整状态流转。
- Kafka 重复消息、Dispatcher 重启和事件重复发送不会重复创建执行。
- 真实运行后端留到后续阶段实现。

## 2026-09 终态后的运行资源责任

业务终态与外部资源终止、日志归档、清理分别持久化。Deadline 形成 TIMED_OUT 后，后台仍按执行身份恢复未知 Handle、重试取消并确认；外部作业在终态宽限 30 秒后仍存活时升级硬终止。迟到的提交响应只能补充 Handle、重新打开清理责任，不改变既有终态。提交仍在本进程进行时不并发判定资源不存在，重启恢复沿用提交不确定宽限期。

尚未确认外部终止的已提交执行继续计入在途容量；确认终止后，日志归档失败不会继续占用运行容量。观察与清理各轮最多处理 50 条到期记录，清理仅选择未完成记录，重试间隔从 5 秒指数增加，最大 5 分钟。新列允许存量记录为空，旧终态在第一次到期处理时恢复确认和清理。提交、观察、清理、Outbox 各有单线程调度器，避免外部调用阻塞其他职责。
