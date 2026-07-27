# 07 Task Engine 收缩为预检服务开发设计

## 1. 目标

在 Admin 已切换到 Dispatcher Kafka执行链路后，从 Task Engine Daemon 删除所有真实执行调度职责，使长期进程只负责 Canvas 编译和预检。Runner 制品仍可暂时由该 Maven 模块构建，但 Runner Main 不被 Daemon加载。

依赖：[06 Admin 与 Dispatcher 执行融合](06-admin-dispatcher-integration.md)。

## 2. 最终 Task Engine 职责

保留：

- 普通 Java Main。
- JDK HttpServer。
- 基础 SparkSession/SparkContext。
- 请求级 child session。
- Canvas 图分析。
- 零行 Dataset编译。
- JDBC_INPUT、JOIN、JDBC_OUTPUT预检。
- 编译并发、超时和取消。
- live/ready。

删除：

- 执行队列。
- Docker CLI。
- Runner 工作目录管理。
- result/log上传。
- Admin callback。
- 执行状态查询和取消。
- Docker/Runner JAR readiness。

## 3. HTTP API

最终只保留：

```http
GET  /health/live
GET  /health/ready
POST /api/v1/task-compilations
POST /api/v1/task-compilations/{requestId}/actions/cancel
```

移除：

```http
POST /api/v1/task-executions
GET  /api/v1/task-executions/{executionId}
POST /api/v1/task-executions/{executionId}/actions/cancel
```

旧接口不做代理到 Dispatcher，避免形成两套执行入口。升级后请求旧接口返回普通 404 Problem Detail。

## 4. 代码清理

从 Daemon 装配移除：

```text
execution/TaskExecutionService
execution/TaskExecutionRecord
execution/ExecutionArtifactClient
execution/ExecutionCallbackClient
execution/DockerCommandFactory
```

HTTP Router 删除 execution routes 和相关 Problem Code。

契约处理：

- Daemon 的 task-execution HTTP request/response record 删除。
- manifest、runtimeDataSources、TaskExecutionResult等 Runner 使用的契约保留在 runner 可访问包。
- 如果编译期和 Runner契约同包造成误依赖，按 `contract.compilation`、`contract.runner` 分包，不改变 JSON字段。

## 5. 配置清理

删除 Task Engine 配置：

```text
task.engine.execution.enabled
task.engine.execution.max-concurrency
task.engine.execution.queue-capacity
task.engine.execution.timeout-seconds
task.engine.execution.work-directory
task.engine.execution.runner-jar
task.engine.execution.docker-image
task.engine.execution.callback-token
```

删除环境变量：

```text
DATASCALPEL_TASK_ENGINE_EXECUTION_ENABLED
DATASCALPEL_TASK_EXECUTION_CALLBACK_TOKEN
DATASCALPEL_TASK_RUNNER_JAR
DATASCALPEL_TASK_ENGINE_EXECUTION_WORK_DIRECTORY
```

保留编译配置：

```text
DATASCALPEL_TASK_ENGINE_HOST
DATASCALPEL_TASK_ENGINE_PORT
DATASCALPEL_TASK_ENGINE_TOKEN
DATASCALPEL_TASK_ENGINE_MAX_CONCURRENCY
DATASCALPEL_TASK_ENGINE_ACQUIRE_TIMEOUT_SECONDS
DATASCALPEL_TASK_ENGINE_COMPILE_TIMEOUT_SECONDS
```

## 6. readiness

ready 只检查：

- 基础 SparkContext 已创建。
- SparkContext 未停止。
- 编译线程池可以接受任务。

不检查：

- Docker。
- Runner JAR。
- MinIO。
- Kafka。
- Admin。
- Dispatcher。

这使 Task Engine 可以在没有任何真实执行基础设施的环境中独立提供设计期预检。

## 7. 进程生命周期

启动：

```text
加载配置
→ 验证 Token
→ 创建基础 SparkSession
→ 创建编译执行器
→ 启动 HTTP Server
```

关闭：

```text
停止接收新编译
→ 取消活动 Job Group
→ 等待编译线程退出
→ 停止 HTTP Server
→ 停止 SparkSession
```

不存在需要监管或保留的外部任务。

## 8. Runner 制品边界

第一阶段不额外新增 Maven 模块，继续由 `data-scalpel-task-engine` 构建：

```text
runner-local.jar
runner-cluster.jar
```

但必须保证：

- `TaskEngineDaemon` 不引用 Runner Main。
- Daemon 分发包不必携带 runner-local.jar。
- Dispatcher 通过显式部署路径取得 Runner制品。
- Runner 不引用 Engine HTTP或 Daemon生命周期类。

等 Local/YARN/Kubernetes 都稳定后，再评估是否建立独立 `data-scalpel-task-runner` 模块；本阶段不增加该重构范围。

## 9. Admin 清理

确认第六阶段已经删除：

- Engine execution HTTP Client。
- callback Base URL/Token配置。
- callback Resource和认证 Filter。
- 旧执行恢复轮询。

保留：

- `TaskCompilationService`。
- `TaskEngineClient` 的 compile/cancel compile。
- Canvas Designer自动预检。
- 发布和真实运行前的最终预检。

系统设置中的 `task.engine.base-url` 继续存在，语义明确为“Task Engine 预检服务地址”。

## 10. 文档和启动脚本

更新：

- 根 README 模块职责。
- `task-engine-daemon-and-compilation.md`。
- `canvas-task-execution.md`，将执行拓扑指向 Dispatcher。
- `start-local-dev.sh`，分别启动 Engine 和 Dispatcher。
- Task Engine 分发 properties 和示例。

本地端口建议：

```text
Admin API:       18080
Service Engine:  8081
Task Engine:     18091
Dispatcher:      18092
Vite UI:         18887
```

## 11. 兼容和部署顺序

删除 Engine执行 API前必须确认：

1. Admin 已部署 Dispatcher Kafka模式。
2. Admin 没有再调用 Engine task-executions。
3. 旧 Engine队列为空。
4. 没有 Engine监管中的 Docker容器。
5. Dispatcher Local Docker链路已通过一次真实执行。

不自动迁移正在 Engine 中执行的任务。升级窗口必须先 Drain旧执行入口。

## 12. 测试计划

- Engine 启动不需要 Docker、MinIO、Kafka或 Runner JAR。
- compile/compile cancel全部回归。
- 旧 task-executions 路径返回 404。
- Engine配置中不再识别执行参数。
- shutdown只取消编译 Job Group。
- Runner 两种制品仍能构建和执行独立测试。
- Admin代码扫描没有 callback 和 Engine execution Client引用。
- 分发包检查不包含不必要的执行配置和 Docker脚本。

## 13. 阶段退出条件

- Task Engine Daemon 只提供预检。
- Docker调度只存在于 Dispatcher。
- 真实执行状态只存在于 Dispatcher账本和 Admin TaskRun。
- Runner仍可独立构建，且不影响 Engine Daemon部署。
- README、设计文档和启动脚本不再描述 Engine监管真实执行。
