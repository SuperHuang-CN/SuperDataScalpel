# 06 Admin 与 Dispatcher 执行融合开发设计

## 1. 目标

`SPARK_CANVAS` 的真实执行统一由 Admin 生成可靠 Kafka 命令并交给 Dispatcher 调度。Task Engine 只用于最终预检，不参与执行生命周期；生产代码不保留旧 HTTP 执行旁路。

依赖：

- [01 计算引擎管理](01-compute-engine-management.md)
- [02 Kafka 执行契约与 Admin 可靠消息](02-kafka-contracts-and-admin-reliability.md)
- [03 Task Dispatcher 基础](03-task-dispatcher-foundation.md)
- [05 Task Runner 通用启动](05-task-runner-kafka-and-artifacts.md)

## 2. 最终运行链路

```text
用户运行任务
  → Admin 读取任务和计算引擎快照
  → Admin 重建 metadataSnapshot/runtimeDataSources
  → Admin HTTP 调用 Task Engine 最终预检
  → Admin 上传 manifest 到 MinIO
  → Admin 同事务创建 TaskRun + SUBMIT Outbox
  → Outbox Publisher 发送 Kafka
  → Dispatcher 接收、排队、启动 Runner
  → Runner 上传 result 并发送 Kafka
  → Dispatcher 校验、持久化并发送 Kafka
  → Admin 消费事件更新 TaskRun
```

Admin 不等待 Dispatcher 同步接受任务。运行接口在 TaskRun 和 Outbox 提交后返回 `202 Accepted`。

## 3. 运行准备

沿用现有 `CanvasTaskRunPreparationService`，但输出扩展为不可变准备结果：

```text
taskId
taskType
definitionVersion
canvasDefinition
metadataSnapshot
runtimeDataSources
computeEngineSnapshot
referencedDataSourceVersions
deadlineAt
```

步骤：

1. 短事务读取已发布任务、定义版本和 computeEngineId。
2. 读取计算引擎快照，要求 ACTIVE 且健康不是 DOWN。
3. 加载定义引用的数据源。
4. 在事务外读取真实表元数据。
5. 构建权威 metadataSnapshot 和 runtimeDataSources。
6. HTTP 调用 Task Engine `task-compilations`，要求 `valid=true`。
7. 短事务重新检查任务版本、计算引擎 revision 和数据源 updatedAt。
8. 生成 executionId/runId/attempt 和 manifest。

Task Engine 地址继续来自 `task.engine.base-url`，计算引擎对象只描述 Dispatcher，不替代预检服务配置。

## 4. Manifest 和制品

路径不变：

```text
task-runs/{runId}/attempts/1/manifest.json
task-runs/{runId}/attempts/1/result.json
task-runs/{runId}/attempts/1/console.log
```

Admin：

- 上传 manifest 正文。
- 计算并保存 SHA-256。
- 不提前生成 Runner 使用的预签名 URL。
- 在 Kafka命令中只发送对象 Key和 manifest SHA。

Dispatcher 使用自己的 MinIO凭据在真正提交时生成 URL。Admin 和 Dispatcher 必须配置相同 Bucket 和对象 Key根目录。

该 Bucket 必须保持私有，生产环境的 S3/MinIO Endpoint 和预签名 URL 必须使用 TLS。Manifest 不提供管理端下载接口；Result 和 Console Log 只能由 Admin 在鉴权后代理读取。Bucket 生命周期策略负责清理超过业务保留期的 Manifest、Result、Log 和补偿失败的孤立对象。

## 5. TaskRun 与 Outbox 原子创建

运行准备中的外部 HTTP/JDBC/MinIO操作完成后，进入短事务：

1. 再次验证任务和引擎版本。
2. 创建 TaskRun，状态 `QUEUED`。TaskRun 的 JPA 主键继续由 `GenerationType.UUID` 生成；另存唯一的 `executionRunId`，作为 manifest、Kafka 和 Dispatcher 共享的稳定 `runId`。
3. 保存 computeEngineId、commandTopicSnapshot、executionId、attempt、deadline 和对象 Key。
4. definitionSnapshot 只保存不含秘密的任务/manifest 摘要。
5. 创建 SUBMIT_EXECUTION Outbox。
6. 提交。

如果数据库事务失败，Admin 立即最佳努力删除已上传的 manifest，且不发送执行命令；删除补偿失败时记录完整服务端日志，并由 Bucket 生命周期策略兜底清理。

`executionRunId` 在上传 manifest 前生成，因此对象 Key 不依赖预分配 JPA 主键，也不需要引入可被用户看到的 `PREPARING` 半成品运行记录。Admin 的 TaskRun REST API仍使用实体主键定位运行，执行消息和制品路径只使用 `executionRunId`。

如果 Kafka 不可用，TaskRun 保持 QUEUED，Outbox 自动重试；页面可显示“等待分发”。

## 6. 运行 API

继续复用：

```http
POST /api/v1/tasks/{taskId}/actions/run
```

响应为现有 TaskRunResponse，HTTP `202`。对 SPARK_CANVAS：

- 返回不表示 Dispatcher 已启动任务。
- 返回表示运行请求、manifest 和可靠消息已经持久化。
- Dispatcher 容量不足稍后通过 `EXECUTION_REJECTED` 反馈，TaskRun 转 FAILED。

LOCAL_SQL 保持当前进程内执行路径，不进入 Dispatcher。

## 7. 取消

取消接口为：

```http
POST /api/v1/task-runs/{runId}/actions/cancel
```

行为：

- QUEUED/RUNNING：短事务改为 CANCEL_REQUESTED并写 CANCEL Outbox。
- CANCEL_REQUESTED：幂等返回当前状态。
- 终态：返回 409 或当前终态，不能重新发布取消。
- Outbox Topic 使用 TaskRun 固化的计算引擎 commandTopic 快照，不能重新按任务当前引擎路由。

任务运行列表和详情 Drawer 对活动 Canvas 运行提供取消按钮；行级 loading 只锁定当前运行。

## 8. Dispatcher 事件应用

Admin Consumer 根据第二阶段规则处理事件。业务额外约束：

- engineId 必须等于 TaskRun.computeEngineId。
- externalExecutionId 只允许首次设置或保持相同。
- sequence 必须严格递增。
- TaskRun 终态不可覆盖。
- CANCEL_REQUESTED 收到迟到 RUNNING 时保持 CANCEL_REQUESTED，但可以记录 startAt/externalExecutionId。
- SUCCEEDED 只有在未先形成 CANCELLED/TIMED_OUT时可生效。

状态变化后前端使用现有 TanStack Query轮询刷新，无需浏览器连接 Kafka。

## 9. Admin 主动核对

虽然 Dispatcher 使用 Outbox 保证事件最终发送，Admin 仍保留低频修复任务：

1. 查找长时间停留在 QUEUED/RUNNING/CANCEL_REQUESTED 的外部 Run。
2. 使用 ComputeEngine 的 Dispatcher URL主动调用：

```http
GET /api/v1/task-executions/{executionId}
```

3. 响应身份和 sequence 合法时，走与 Kafka事件相同的应用函数。
4. Dispatcher 404 且超过宽限期时不立即失败；记录告警并等待 Kafka/下一轮。
5. 超过 deadline 加恢复宽限期仍无法确认时，Admin 可以标记 TIMED_OUT，但必须保留核对审计信息。

Dispatcher 永不反向调用 Admin。

## 10. 移除旧执行链路

Admin 删除或停用：

- `TaskCompilationService.submitExecution/getExecution/cancelExecution`。
- `TaskEngineClient` 的 task-executions 方法。
- `callbackUrl` 和 callback Token组装。
- `/api/v1/internal/task-runs/{runId}/actions/report`。
- `TaskExecutionCallbackAuthenticationFilter`。
- HTTP callback 专用权限。
- 基于 callback 丢失读取 result 的旧恢复逻辑，改为 Dispatcher事件/查询恢复。

Task Engine 编译 Client 和 Canvas Designer 网关必须保留。

## 11. 计算引擎生命周期与任务运行

- ACTIVE：允许新运行。
- DRAINING：运行 API拒绝新任务，已有 TaskRun继续处理。
- INACTIVE/ERROR：拒绝新任务。
- health DOWN：默认拒绝新任务；管理员测试恢复后再运行。
- Dispatcher 短暂 HTTP不可用但 Kafka和最近健康正常时，是否允许运行由第一期固定为“不允许”，优先明确反馈。

反注册前 Admin 通过 Dispatcher HTTP确认没有活动执行；强制反注册会由 Dispatcher 取消活动任务并通过 Kafka反馈。

## 12. 前端变化

- SPARK_CANVAS 编辑/发布表单增加计算引擎选择。
- 列表显示计算引擎名称和状态。
- 运行后提示“任务已提交，等待计算引擎调度”，不提示“执行已开始”。
- TaskRun 状态支持 CANCEL_REQUESTED/CANCELLED。
- Dispatcher/Engine 不可用错误区分：
  - Task Engine 预检不可用：本次运行没有创建。
  - Dispatcher 状态不可用：本次运行没有创建。
  - Kafka 暂时不可用：TaskRun 已创建并等待 Outbox重试。
- 运行详情展示计算引擎、外部执行 ID、Attempt、Application ID 和安全的 HTTP/HTTPS Tracking URL。
- Result 和 Console Log 通过 Admin 鉴权接口下载，Manifest 永不对浏览器开放。

## 13. 测试计划

- 发布/运行时计算引擎缺失、非 ACTIVE、后端不匹配。
- 预检失败不创建 TaskRun/Outbox。
- manifest 上传成功但 DB失败时不发送消息。
- TaskRun 与 Outbox 同事务。
- Kafka故障时 Outbox保留并恢复发送。
- Dispatcher REJECTED/RUNNING/各终态映射。
- 重复、乱序、身份错误事件。
- 取消命令使用 TaskRun 固化路由。
- 主动 HTTP核对复用同一事件应用函数。
- 旧 callback 路由和安全 Filter 被删除。
- 前端状态、计算引擎选择和提示文本。

## 14. 唯一生产路径与部署前提

生产代码只保留 `Admin Outbox → Kafka → Dispatcher → Runner` 一条 Canvas 执行路径，不存在 `task-execution.mode`、`kafka.enabled` 或 Canvas 执行开关，也不会在基础设施故障时降级为模拟成功或本地执行。

部署前必须准备 Kafka、私有对象存储和至少一个已注册且可运行的计算引擎。Task Engine 或对象存储不可用时，本次运行不创建 TaskRun；Kafka暂时不可用时，已经和 TaskRun 原子提交的 Outbox继续重试；Dispatcher拒绝或执行失败通过事件收敛到 TaskRun终态。

测试环境可以通过 Spring `test` Profile关闭真实 Listener并装配测试替身，这只用于测试隔离，不改变产品能力。

## 15. 阶段退出条件

- SPARK_CANVAS 执行和取消只通过 Kafka进入 Dispatcher。
- Dispatcher 状态只通过 Kafka或 Admin主动查询进入 Admin。
- Dispatcher/Runner 不再调用 Admin HTTP。
- TaskRun、manifest 和 Outbox形成可靠提交边界。
- Local Docker真实任务可以经过新链路运行，完整验收在第八阶段执行。
