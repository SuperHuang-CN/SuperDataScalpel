# 01 计算引擎管理开发设计

## 1. 文档定位

本文是通用 Spark 任务执行平台十阶段开发计划的第一阶段，也是后续文档的入口。目标是在 Admin 中建立“计算引擎”业务域，用一个稳定的管理模型表示一套固定部署的 Task Dispatcher 及其计算后端。

十个阶段按以下顺序实施，后一个阶段可以依赖前一个阶段已经稳定的契约：

1. [计算引擎管理](01-compute-engine-management.md)
2. [Kafka 执行契约与 Admin 可靠消息](02-kafka-contracts-and-admin-reliability.md)
3. [Task Dispatcher 工程与持久化调度内核](03-task-dispatcher-foundation.md)
4. [Local Docker 后端迁移](04-local-docker-backend.md)
5. [Task Runner 通用启动与 Kafka 结果链路](05-task-runner-kafka-and-artifacts.md)
6. [Admin 与 Dispatcher 执行融合](06-admin-dispatcher-integration.md)
7. [Task Engine 收缩为预检服务](07-task-engine-preflight-only.md)
8. [Local Docker 端到端验收](08-local-docker-end-to-end.md)
9. [YARN Cluster 后端](09-yarn-cluster-backend.md)
10. [Kubernetes Cluster 后端](10-kubernetes-cluster-backend.md)

## 2. 已确认的架构决定

- `data-scalpel-task-engine` 的 Daemon 最终只提供 Canvas 编译和预检，不再调度真实任务。
- 新增独立 Maven 模块 `data-scalpel-task-dispatcher`，负责准入、排队、提交、观测、取消和恢复。
- 一个 Dispatcher 部署只绑定一个执行后端：`LOCAL_DOCKER`、`YARN` 或 `KUBERNETES`。
- YARN 固定使用 `cluster` deploy mode，不提供 client mode。
- Dispatcher 使用与 Admin 相同的 PostgreSQL 数据库账户，但只访问独立 `dispatcher` Schema。
- Admin 可以通过 HTTP 调用 Dispatcher 的控制面；Dispatcher 不通过 HTTP 回调 Admin。
- 执行提交、取消、Runner 状态和最终结果使用 Kafka。
- Runner 可以访问 Kafka、MinIO 和业务 JDBC，但不调用 Admin 或 Dispatcher HTTP。
- 第一阶段不建设多集群自动选择、负载均衡或服务注册中心。任务明确选择一个计算引擎。

## 3. 术语和边界

### 3.1 Task Engine

Task Engine 是预检服务。它长期持有 SparkContext，根据 Canvas 定义和临时元数据快照构造零行 Dataset，返回图问题和节点输入输出 Schema。

### 3.2 Compute Engine

Compute Engine 是 Admin 中的管理对象，不是单独进程。它描述一套 Dispatcher 部署及其固定计算后端，是任务选择执行位置时使用的业务标识。

### 3.3 Task Dispatcher

Task Dispatcher 是独立进程。它持久化执行账本，消费 Kafka 命令，并通过 Docker、YARN 或 Kubernetes 启动 Runner。

### 3.4 Task Runner

Task Runner 是一次性 Spark 应用。它读取 manifest，执行 Canvas，上传结果并发送 Runner 事件，完成后退出。

## 4. 模块和包结构

计算引擎属于现有模块化单体的业务能力，不新增 `data-scalpel-compute-engine` Maven 模块。代码放置如下：

```text
data-scalpel-business
└─ src/main/java/cn/superhuang/data/scalpel/business/compute
   ├─ domain
   ├─ repository
   ├─ service
   └─ web
      ├─ resource
      ├─ request
      └─ response

data-scalpel-ui/src/modules/computeengine
├─ api
├─ components
├─ model
├─ pages
└─ index.ts
```

`data-scalpel-admin` 只增加配置装配和 Dispatcher HTTP Client 所需 Bean，不放业务 Entity、Repository、Service 或 Resource。

## 5. ComputeEngine 领域模型

### 5.1 实体

`ComputeEngine` 继承现有 `BaseEntity`，使用 UUID 主键。建议字段如下：

| 字段 | 类型 | 约束与含义 |
| --- | --- | --- |
| `id` | UUID | JPA `GenerationType.UUID` |
| `name` | String | 必填、全局唯一，展示名称 |
| `description` | String | 可选，最长 1000 |
| `dispatcherBaseUrl` | String | 必填，Admin 控制面调用地址 |
| `accessTokenCiphertext` | String | 必填，加密保存，API 永不回传 |
| `expectedBackendType` | enum | 创建时选择并在注册时核对 |
| `reportedBackendType` | enum | Dispatcher 最近一次上报值 |
| `registrationState` | enum | 注册生命周期 |
| `healthState` | enum | 最近一次主动检查结果 |
| `commandTopic` | String | Admin 到 Dispatcher 的命令 Topic |
| `runnerEventTopic` | String | Runner 到 Dispatcher 的事件 Topic |
| `adminEventTopic` | String | Dispatcher 到 Admin 的事件 Topic |
| `maxQueuedExecutions` | Integer | 队列上限，必须大于等于 0 |
| `maxConcurrentSubmissions` | Integer | 同时执行外部提交命令的上限 |
| `maxInFlightApplications` | Integer | 已提交但未终止的应用上限，0 表示不额外限制 |
| `dispatcherInstanceId` | String | 注册后锁定的 Dispatcher 稳定身份 |
| `lastCheckAt` | Instant | 最近主动检查时间 |
| `lastError` | String | 脱敏后的最近错误，最长 2000 |
| `detachedAt` | Instant | 离线解除绑定时间；普通生命周期中为空 |
| `detachReason` | String | 管理员填写的离线解除绑定原因，最长 500 |

第一期对 Topic 采用保守约束：`commandTopic` 和 `runnerEventTopic` 在全部计算引擎记录中全局唯一，而不只在 `ACTIVE` 引擎之间唯一，避免停用引擎重新启用时与现有 Listener 发生歧义。多个 Dispatcher 可以共享 `adminEventTopic`，Admin 依靠消息中的 `engineId` 路由和校验。

不在该实体保存 YARN ResourceManager、Kubernetes kubeconfig、Docker Socket、Runner JAR 路径或 MinIO 密钥。这些参数属于固定 Dispatcher 部署，不由单个任务下发。

### 5.2 枚举

```text
ComputeBackendType:
  LOCAL_DOCKER
  YARN
  KUBERNETES

ComputeEngineRegistrationState:
  CREATED
  REGISTERING
  ACTIVE
  DRAINING
  INACTIVE
  DETACHED
  ERROR

ComputeEngineHealthState:
  UNKNOWN
  UP
  DOWN
```

### 5.3 状态规则

```text
CREATED ──register──> REGISTERING ──success──> ACTIVE
   │                       │                     │
   └───────────────────────┴──failure────────> ERROR

ACTIVE ──drain──> DRAINING ──deactivate──> INACTIVE
ERROR  ──deactivate(force)────────────────> INACTIVE
INACTIVE ──register──> REGISTERING

ACTIVE/DRAINING/ERROR ──detach──> DETACHED
DETACHED ──register──> REGISTERING
```

- `ACTIVE` 和 `DRAINING` 时不能直接覆盖配置；必须通过自动重新配置动作安全排空、反注册并重新注册。
- `ACTIVE` 才接受新任务。
- `DRAINING` 不接受新提交，但继续接受取消并监管已有任务。
- 健康检查失败只修改 `healthState`，不自动改变注册状态。
- 已经被任务引用的计算引擎不能删除；应先迁移任务并反注册。
- `DETACHED` 表示 Dispatcher 不可达时，Admin 已在本地解除注册关系；它不代表远端进程或任务已被停止。
- 从 `DETACHED` 重新注册前，管理员必须确认原 Dispatcher 已永久停止，避免两个 Dispatcher 同时消费同一 Topic。

## 6. Task 与 TaskRun 关联

`DataTask` 增加可空 UUID 标量字段 `computeEngineId`：

- `LOCAL_SQL` 不使用该字段，保存时必须为 null。
- `SPARK_CANVAS` 草稿阶段允许为空。
- 发布、重新启用和真实运行时必须选择一个 `ACTIVE` 计算引擎。
- 每次真实运行前同时读取 Dispatcher info 和 registration，严格比对 engineId、dispatcherInstanceId、backendType、全部 Topic 和准入策略；任一配置不一致或远端已经进入 `DRAINING/INACTIVE` 时拒绝创建 Run。
- 任务停用后允许修改计算引擎。

`TaskRun` 增加或确认以下快照字段：

```text
computeEngineId
commandTopicSnapshot
externalExecutionId
attempt
deadlineAt
lastDispatcherEventSequence
```

TaskRun 必须保存本次实际路由的 `computeEngineId` 和命令 Topic 快照，不能在结果或取消处理时重新读取任务、计算引擎的当前值。

## 7. Admin 对外 API

所有接口遵守现有 GET/POST 约定，错误使用 RFC 9457 Problem Detail。

```http
GET  /api/v1/compute-engines
GET  /api/v1/compute-engines/{id}
POST /api/v1/compute-engines
POST /api/v1/compute-engines/{id}/actions/update
POST /api/v1/compute-engines/{id}/actions/reconfigure
POST /api/v1/compute-engines/{id}/actions/delete
POST /api/v1/compute-engines/{id}/actions/test
POST /api/v1/compute-engines/{id}/actions/register
POST /api/v1/compute-engines/{id}/actions/drain
POST /api/v1/compute-engines/{id}/actions/deactivate
POST /api/v1/compute-engines/{id}/actions/detach
```

列表使用现有 `SearchRequest` 和 `SearchEngine`，支持名称、后端类型、注册状态和健康状态查询。

建议权限：

```text
compute.engine.view
compute.engine.create
compute.engine.update
compute.engine.delete
compute.engine.test
compute.engine.manage
```

不扩展任务权限体系；任务编辑和运行仍使用现有 task 权限。

### 7.1 创建请求

```json
{
  "name": "本地 Spark",
  "description": "本地 Docker 调试引擎",
  "dispatcherBaseUrl": "http://127.0.0.1:18092",
  "accessToken": "plain-token-only-in-request",
  "expectedBackendType": "LOCAL_DOCKER",
  "commandTopic": "datascalpel.execution.command.local",
  "runnerEventTopic": "datascalpel.runner.event.local",
  "adminEventTopic": "datascalpel.execution.event",
  "maxQueuedExecutions": 20,
  "maxConcurrentSubmissions": 2,
  "maxInFlightApplications": 2
}
```

响应不包含明文 Token 或密文，只返回 `accessTokenConfigured: true`。

### 7.2 更新请求

Token 字段可选：

- 缺失或空白表示保留当前 Token。
- 显式传入新值表示替换并重新加密。

普通 `update` 只处理 `CREATED/INACTIVE/ERROR`。`ACTIVE/DRAINING` 使用 `reconfigure`，并同时要求更新和管理权限：

```text
候选配置预检
→ ACTIVE 时 Drain
→ 使用旧配置非强制反注册
→ 保存候选配置
→ 使用新配置重新注册
```

候选配置预检失败时不改变当前注册。Dispatcher 仍有排队或活动执行时返回 HTTP 409，计算引擎保持 `DRAINING` 且不保存候选配置；任务结束后由用户保留表单草稿并再次应用。流程不使用强制反注册。新配置保存后若重新注册失败，则保留新配置并进入 `ERROR`，由管理员修正后恢复。

### 7.3 反注册与离线解除绑定

计算引擎提供三种语义不同的运维动作，界面和接口不得相互自动降级：

1. **安全反注册**：调用 Dispatcher 的 `deactivate(force=false)`。Dispatcher 必须可访问且没有活动执行。
2. **强制反注册并取消任务**：调用 Dispatcher 的 `deactivate(force=true)`。Dispatcher 必须可访问，由 Dispatcher 取消排队和运行任务并收敛本地执行账本。
3. **离线解除绑定**：只修改 Admin，用于 Dispatcher 主机永久损坏且控制面不可达的灾难恢复。

Admin 在调用 Drain 或任一远程反注册动作前，必须先读取 Dispatcher 当前 registration，并核对 `engineId`、已知的 `dispatcherInstanceId`、后端类型、全部 Topic 和准入策略。不一致时返回 HTTP 409，且不得向 Dispatcher 发送状态变更命令，防止错误记录排空或反注册其他计算引擎。未曾成功记录 Dispatcher 实例身份的 `ERROR` 记录只允许重试注册，不在页面展示远程反注册和离线解除绑定动作。

离线解除绑定使用：

```http
POST /api/v1/compute-engines/{id}/actions/detach
```

```json
{
  "confirmationName": "本地 Spark",
  "reason": "原 Dispatcher 主机已永久下线"
}
```

服务端必须满足全部条件才进入 `DETACHED`：

- 当前状态是 `ACTIVE`、`DRAINING` 或 `ERROR`。
- 确认名称与计算引擎名称精确一致。
- 对 Dispatcher `/info` 的探测发生连接拒绝、连接超时等传输级故障。
- 不存在关联的 `QUEUED`、`RUNNING`、`CANCEL_REQUESTED`、`STOP_REQUESTED` TaskRun。
- 不存在该引擎的 `PENDING`、`PUBLISHING` 或 `FAILED` 执行 Outbox 消息。

Dispatcher 返回 HTTP 错误、认证失败或业务错误时不允许离线解除绑定，因为这些情况说明控制面可能仍在运行。远程强制反注册失败时也不得自动转为离线解除绑定，避免短暂网络分区造成双 Dispatcher 消费的脑裂。

成功后清除 Admin 保存的 Dispatcher 实例身份和上报后端，记录 `detachedAt` 与 `detachReason`。第一期不删除已有 TaskRun 和执行历史。

## 8. Dispatcher 控制面契约

第一阶段先在 Admin 侧锁定契约并使用 MockWebServer 测试；第三阶段由 Dispatcher 实现。

```http
GET  /api/v1/dispatcher/info
GET  /api/v1/dispatcher/registration
POST /api/v1/dispatcher/registration/actions/activate
POST /api/v1/dispatcher/registration/actions/drain
POST /api/v1/dispatcher/registration/actions/deactivate
GET  /api/v1/task-executions/{executionId}
```

所有 `/api/v1/**` 要求：

```http
Authorization: Bearer <dispatcher-token>
```

注册请求示例：

```json
{
  "engineId": "uuid",
  "topics": {
    "commandTopic": "datascalpel.execution.command.local",
    "runnerEventTopic": "datascalpel.runner.event.local",
    "adminEventTopic": "datascalpel.execution.event"
  },
  "admissionPolicy": {
    "maxQueuedExecutions": 20,
    "maxConcurrentSubmissions": 2,
    "maxInFlightApplications": 2
  }
}
```

`GET /api/v1/dispatcher/info` 返回：

```json
{
  "dispatcherInstanceId": "stable-uuid",
  "backendType": "LOCAL_DOCKER",
  "version": "0.1.0-SNAPSHOT",
  "capabilities": {
    "cancellation": true,
    "logCollection": true,
    "restartReconciliation": true
  },
  "dependencies": []
}
```

注册时必须校验：

- Dispatcher 上报后端与 `expectedBackendType` 一致。
- 已保存的 `dispatcherInstanceId` 未发生异常变化。
- Dispatcher readiness 为 UP。
- Topic 和准入策略被 Dispatcher 接受。

## 9. HTTP Client 和事务边界

`ComputeEngineDispatcherClient` 使用 Spring `RestClient`：

- 连接超时 3 秒。
- 请求超时 10 秒；执行详情查询可使用 15 秒。
- 自动添加 Bearer Token。
- 不记录 Token、完整注册请求或 Dispatcher 内部错误堆栈。
- Dispatcher 的安全 4xx detail 可以向管理员返回；认证失败和 5xx 转换为安全的上游错误。

HTTP 调用不能发生在管理数据库长事务中：

```text
短事务读取引擎快照
→ 事务外调用 Dispatcher
→ 短事务直接比较配置快照并提交状态
```

如果调用期间配置已经变化，本次响应不得覆盖新配置状态。

## 10. 前端页面

新增路由：

```text
/compute-engine
```

列表列建议：名称、后端、注册状态、健康状态、Dispatcher 地址、队列/并发策略、最近检查时间、操作。

交互：

- 新建、查看和编辑使用紧凑 Drawer，所有注册状态都提供配置入口。
- Token 使用 Password 输入框，详情不回显。
- 测试、注册、Drain 和反注册是行级异步命令。
- `ACTIVE/DRAINING` 在同时具有更新和管理权限时允许编辑，保存动作显示为“应用并重新注册”；`REGISTERING` 和权限不足时只读。
- `ACTIVE` 应用前确认将暂停新任务准入；排空未完成时抽屉保留草稿并提供“再次应用并重新注册”。
- Drain 和强制反注册必须明确说明对排队与运行任务的影响。
- `ACTIVE/DRAINING/ERROR + DOWN` 时展示“离线解除绑定”，要求填写原因并再次输入完整引擎名称；确认框必须说明该动作不会停止远端进程。
- `DETACHED` 可以修改配置和重新注册；重新注册确认框必须提醒管理员先停止原 Dispatcher。
- 删除必须二次确认并展示引擎名称。
- 任务编辑页仅列出 `ACTIVE` 计算引擎；已绑定但暂时不可用的引擎仍显示原值和状态，不能静默清空。

## 11. 配置与安全

Admin 用现有凭据加密能力保存 Dispatcher Token；如果现有密钥不可用，禁止创建或替换 Token。

日志只允许记录：

```text
engineId
dispatcherInstanceId
operation
HTTP status
durationMs
```

禁止记录 Token、Authorization Header、Kafka 安全参数和完整 Dispatcher 响应。

## 12. 测试计划

- 实体：默认状态、状态迁移、唯一名称和配置变更后的健康状态重置。
- Service：活动引擎禁止修改、删除引用保护、Token 替换语义。
- HTTP Client：认证 Header、超时、安全错误映射、实例身份变化。
- Resource：GET/POST 路径、权限、Bean Validation、Problem Detail。
- Task：SPARK_CANVAS 发布必须选择活动引擎，LOCAL_SQL 不得绑定。
- 前端：列表筛选、状态操作、Token 不回显、任务选择器保留失效值。
- 完成后运行 `pnpm check`、Admin 针对性测试和 `./mvnw verify`。

## 13. 阶段退出条件

- ComputeEngine 可以在 Admin 中完整 CRUD。
- Dispatcher 控制面契约已经稳定并有 Mock 测试。
- 注册、Drain、反注册状态机完成。
- SPARK_CANVAS 可以保存计算引擎引用并在发布时校验。
- Token 不在任何响应或日志中暴露。
- 本阶段不要求真正提交任务；任务执行在第六阶段切换。
