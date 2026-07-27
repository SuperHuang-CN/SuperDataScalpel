# 04 Local Docker 后端迁移开发设计

## 1. 目标

把当前 Task Engine Daemon 中的 Docker 调度能力迁移到 Dispatcher，完成可恢复、可取消、可观测的 `LocalDockerExecutionBackend`。本阶段不切换 Admin 的正式执行入口，避免调度迁移和业务切换同时发生。

依赖：[03 Task Dispatcher 工程与持久化调度内核](03-task-dispatcher-foundation.md)。

## 2. 迁移范围

当前位于 Task Engine 的能力：

```text
execution/TaskExecutionService
execution/DockerCommandFactory
execution/ExecutionArtifactClient
execution/ExecutionCallbackClient
执行队列、活动容器、超时、日志捕获
```

目标：

- Docker 命令和容器生命周期移入 `data-scalpel-task-dispatcher`。
- Admin HTTP 回调逻辑不迁移，后续由 Kafka 事件替代。
- Runner 的真实 Canvas JDBC 执行代码暂时保留原位置。
- Task Engine 的执行 HTTP API在第七阶段统一删除；本阶段先标记为待移除。

## 3. Docker 生命周期模型

不再使用不可恢复的单条命令：

```text
docker run --rm ...
```

改为：

```text
docker create
docker start
docker inspect
docker logs
docker stop
docker kill
docker rm
```

原因：

- `docker create` 成功后即可获得稳定 Container ID并持久化。
- Dispatcher 重启后可以通过名称和 Label 重新发现容器。
- 日志、取消和清理不依赖进程内 `Process` 对象。
- 容器退出后仍可读取状态和日志，完成持久化后再删除。

## 4. 稳定身份

容器名：

```text
datascalpel-runner-{executionId}
```

Labels：

```text
cn.superhuang.datascalpel.managed=true
cn.superhuang.datascalpel.engine-id=<engineId>
cn.superhuang.datascalpel.execution-id=<executionId>
cn.superhuang.datascalpel.run-id=<runId>
cn.superhuang.datascalpel.attempt=<attempt>
```

提交前按 executionId 查询：

- 不存在：允许 create。
- 存在且 Labels 完全一致：视为幂等恢复，返回已有 Handle。
- 名称相同但身份不一致：返回安全冲突，不删除未知容器。

外部 Handle 固定为 Container ID，容器名用于恢复查询。

## 5. Docker 命令

默认创建方式：

```text
docker create
  --name datascalpel-runner-{executionId}
  [--platform linux/amd64|linux/arm64]
  --label ...
  --pull=missing
  --memory=4g
  --cpus=2
  --add-host=host.docker.internal:host-gateway
  --mount type=bind,source={runnerJar},target=/opt/datascalpel/task-runner.jar,readonly
  --mount type=bind,source={executionWorkDir},target=/work
  --env-file {generatedEnvFile}
  eclipse-temurin:21-jdk
  java -jar /opt/datascalpel/task-runner.jar
```

动态值不得直接拼进 Shell 字符串。`ProcessBuilder` 使用参数数组，不经过 `sh -c`。

第一阶段默认值：

```properties
data-scalpel.dispatcher.local-docker.image=eclipse-temurin:21-jdk
data-scalpel.dispatcher.local-docker.platform=
data-scalpel.dispatcher.local-docker.pull=missing
data-scalpel.dispatcher.local-docker.memory=4g
data-scalpel.dispatcher.local-docker.cpus=2
data-scalpel.dispatcher.local-docker.stop-timeout=10s
```

`platform` 默认留空并使用 Docker Server 原生平台；显式配置时必须与 Server 平台一致。镜像、镜像平台、JAR 路径和工作目录在 readiness 中检查。`pull=missing/never` 时，本地镜像平台与期望平台不一致会阻止 Dispatcher 就绪；`pull=always` 允许 Docker 在创建时重新拉取目标平台镜像。

Docker CLI 的 stdout 和 stderr 必须分别捕获。`docker ps/inspect/info` 等机器协议只解析 stdout，stderr 仅作为脱敏、截断后的诊断；`docker logs` 才明确合并两个流。不得因为 stderr 中出现平台 Warning而把成功命令判为失败。

## 6. 工作目录

```text
{workRoot}/{executionId}/attempt-{attempt}/
├─ launch.json
├─ runner.env
├─ result.json
└─ dispatcher-submit.log
```

要求：

- 每次执行独立目录。
- 创建时限制为当前进程用户可读写。
- `launch.json` 和 env 文件不得进入日志。
- 终态并确认 MinIO制品完整后按保留策略删除。
- 调试环境可以保留失败目录，生产默认有限保留。

## 7. Backend 方法语义

### 7.1 readiness

依次检查：

1. `docker version` 成功。
2. Docker Server 可访问并能识别原生平台。
3. 显式 platform 与 Docker Server一致。
4. JDK 21镜像存在或 pull 策略允许获取，已有镜像架构与期望平台一致。
5. Runner Uber JAR 是普通文件且可读。
6. 工作目录可创建文件。

readiness 不能启动真实任务。

### 7.2 submit

1. 验证 ExecutionLaunch。
2. 创建安全工作目录和启动文件。
3. 检查是否已有匹配容器。
4. `docker create`。
5. 不解析 `docker create` 的控制台文本；按确定性容器名查询并 inspect。
6. 校验 engineId、executionId、runId 和 attempt Labels。
7. `docker start`。
8. 返回 inspect得到的 Container ID，由 Service立即持久化。

`docker create/start` 与数据库无法形成原子事务。任何一步后进程崩溃，都由确定性容器名和 Labels恢复；禁止因为 `submit` 没有返回就直接创建第二个容器。

### 7.3 inspect

解析 `docker inspect` JSON，不通过字符串包含判断：

| Docker 状态 | Backend 状态 |
| --- | --- |
| created | PENDING |
| running/restarting | RUNNING |
| exited + exitCode 0 | SUCCEEDED |
| exited + 非 0 | FAILED |
| dead | FAILED |
| 不存在 | UNKNOWN |

容器退出码为 0 仍然需要合法 result.json；没有结果由 Dispatcher 标记 `RUNNER_RESULT_MISSING`。

### 7.4 cancel

- created：直接 remove，结果 CANCELLED。
- running：`docker stop --time 10`。
- 宽限期后仍运行：`docker kill`。
- 已退出：不修改已经确认的终态。
- 命令返回 not found 时再次 recover，不能把瞬时竞态直接视为成功。

### 7.5 collectLog

使用：

```text
docker logs --timestamps <containerId>
```

stdout/stderr 合并保存，最多 20 MiB。超过限制保留头部和尾部并写入明确截断标记。日志上传成功后才允许删除容器。

### 7.6 recover

按稳定 Label 查询容器并校验所有身份标签。只接受唯一匹配：

- 一个匹配且 running/exited：返回 Handle。
- 一个匹配且仍是 created：确认身份后执行一次幂等 `docker start`，再返回 Handle。
- 没有匹配：返回 empty。
- 多个匹配：返回 `AMBIGUOUS_EXTERNAL_EXECUTION`，人工处理，不自动选择或删除。

## 8. Runner 启动协议

Local Docker 只使用统一 `launch.json` 协议。Dispatcher 把执行目录挂载为 `/work`，并通过受限环境文件设置：

```text
DATASCALPEL_TASK_LAUNCH_FILE=/work/launch.json
DATASCALPEL_TASK_WORK_DIRECTORY=/work
```

完整 manifest、预签名 URL 和 JDBC 凭据不进入 Docker 命令参数。旧的 manifest URL、result path 和 HTTP callback 兼容变量已经删除。

## 9. 安全

- Docker Socket 等价于宿主机高权限，只允许受控 Dispatcher 进程访问。
- 禁止用户通过 Task 定义覆盖镜像、mount、entrypoint、network 或任意 Docker 参数。
- Runner JAR、镜像和资源限制全部来自 Dispatcher 部署配置。
- 环境文件不记录、不回传，终态后安全删除。
- `docker inspect` 返回的环境变量不得写入应用日志。
- 容器只读挂载 Runner JAR，工作目录以外不挂载宿主机目录。

## 10. 配置迁移

从 Task Engine 移出的环境变量：

```text
DATASCALPEL_TASK_ENGINE_EXECUTION_ENABLED
DATASCALPEL_TASK_RUNNER_JAR
DATASCALPEL_TASK_ENGINE_EXECUTION_WORK_DIRECTORY
```

替换为：

```text
DATASCALPEL_TASK_DISPATCHER_BACKEND=LOCAL_DOCKER
DATASCALPEL_TASK_DISPATCHER_RUNNER_JAR
DATASCALPEL_TASK_DISPATCHER_WORK_DIRECTORY
DATASCALPEL_TASK_DISPATCHER_DOCKER_IMAGE
DATASCALPEL_TASK_DISPATCHER_DOCKER_PLATFORM
```

第七阶段再从 Task Engine 配置类和分发文件中彻底删除旧配置。

## 11. 测试计划

- Docker 参数数组和禁止 Shell 拼接。
- 容器名称、Labels 和重复提交。
- create 成功/start 失败时保留 Handle并可取消。
- inspect 各状态和异常 JSON。
- stop→kill 取消流程。
- Dispatcher 重启后的 Label 恢复。
- 日志 20 MiB 截断和上传后清理。
- 未知容器和身份冲突不误删。
- 使用轻量假的 Docker CLI 测试命令；Docker 可用时增加真实容器集成测试。

## 12. 阶段退出条件

- LocalDockerExecutionBackend 完整实现 Backend 接口。
- 容器在 Dispatcher 重启后可恢复观测和取消。
- 当前 Runner 可以在兼容模式下完成一次测试执行。
- Task Engine 和 Dispatcher 不再共享 Docker 调度代码。
- 正式 Admin 流量尚未切换，避免双调度。
