# 05 Task Runner 通用启动与 Kafka 结果链路开发设计

## 1. 目标

把现有只适配 Local Docker 工作目录和 HTTP 回执的 Runner 改造成平台无关的一次性 Spark 应用，使同一套执行语义可以运行在 Local Docker、YARN cluster 和 Kubernetes cluster。

依赖：

- [02 Kafka 执行契约与 Admin 可靠消息](02-kafka-contracts-and-admin-reliability.md)
- [04 Local Docker 后端迁移](04-local-docker-backend.md)

## 2. 核心边界

Runner：

- 不调用 Admin HTTP。
- 不调用 Dispatcher HTTP。
- 不持有 Admin/Dispatcher 数据库连接。
- 可以访问 MinIO、Kafka 和 Canvas 引用的 JDBC。
- 不负责业务排队、重试次数、超时终态或任务路由。
- 只执行 launch.json 指定的唯一 executionId/runId/attempt。

Dispatcher 仍是执行生命周期权威。Runner 上传的结果只有被 Dispatcher 校验并写入账本后，才会转换为 Admin 事件。

## 3. Launch Descriptor

调度信息与稳定 manifest 分离，使用临时 `launch.json`：

```json
{
  "launchVersion": 1,
  "engineId": "uuid",
  "executionId": "uuid",
  "runId": "uuid",
  "attempt": 1,
  "deadlineAt": "2026-07-17T13:00:00Z",
  "manifest": {
    "getUrl": "presigned-get-url",
    "sha256": "64-lowercase-hex",
    "maxBytes": 10485760
  },
  "result": {
    "putUrl": "presigned-put-url",
    "objectKey": "task-runs/.../result.json"
  },
  "runnerEvent": {
    "bootstrapServers": "kafka:9092",
    "topic": "datascalpel.runner.event.local",
    "securityProtocol": "PLAINTEXT",
    "clientId": "datascalpel-runner-{executionId}"
  }
}
```

约束：

- launch.json 不是 Canvas Definition，也不持久化到任务定义。
- 预签名 URL和 Kafka认证参数不得进入结果、日志和异常消息。
- Dispatcher 在任务真正出队时生成短期 URL。
- launch 文件只读交付给 Runner，完成后由 Dispatcher/集群清理。
- SASL 密码使用独立受限文件或环境变量引用，不直接放进 `spark-submit` 参数。

## 4. Manifest v6

Runner 当前只接受严格的 `manifestVersion: 6`：

```text
manifestVersion
execution
task
metadataSnapshot
runtimeDataSources
runtimeFileStorage
runtimeFileInputs
```

边界：

- `task.definition` 是纯 Canvas 定义。
- `metadataSnapshot` 是权威编译元数据。
- `runtimeDataSources` 是真实 JDBC 运行信息。
- `runtimeFileStorage` 仅在引用文件 Input 时存在；`runtimeFileInputs` 按稳定逻辑表 ID 去重。表级保存
  `fileDatasetId/fileDatasetTableId/schemaFingerprint` 和统一解析参数；
  `sources` 按当前顺序保存来源 ID、文件 ID、格式、压缩方式、存储形态、私有对象
  位置和来源键。Runner 对来源逐一使用 metadataSnapshot 中的同一权威 Schema 和 FAILFAST Reader，
  再按清单顺序执行 `unionByName`，语义固定为 `UNION ALL`。
- Kafka、Dispatcher、Backend、预签名 URL不进入 manifest。
- v5 及更早版本不兼容读取；Admin、Dispatcher 和 Task Engine 必须同步部署。升级前必须排空或取消旧任务。
- 第一阶段 manifest 保持明文并存放在私有 MinIO Bucket。文件存储凭据、对象 Key和物化前缀不得进入日志、Result、Kafka终态事件或管理端运行记录。

## 5. Runner 启动入口

统一 Main：

```text
cn.superhuang.datascalpel.taskengine.runner.TaskRunnerMain
```

读取启动文件的优先级：

1. `DATASCALPEL_TASK_LAUNCH_FILE` 环境变量。
2. 当前工作目录 `launch.json`。
3. 缺失时安全失败，不支持把完整 JSON 放在命令行参数。

Runner 的可写目录由 `DATASCALPEL_TASK_WORK_DIRECTORY` 指定。Local Docker 使用 `/work`，Kubernetes 使用 `/tmp/datascalpel`；YARN 未显式设置时使用本地化 `launch.json` 的父目录。挂载为只读的 Kubernetes Secret 只提供启动描述，绝不作为 result 临时文件目录。

执行流程：

1. 严格读取 launch.json 并验证身份、deadline 和 URL Scheme。
2. 下载 manifest，限制大小并校验 SHA-256。
3. 严格反序列化 manifest，校验与 launch 身份完全一致。
4. 创建 SparkSession 后发布 `RUNNER_STARTED`。
5. 使用 metadataSnapshot 再次执行完整 Canvas 编译。
6. 校验真实 Input/Output Schema。
7. 构造完整非输出计划。
8. 按稳定顺序执行 JDBC Output。
9. 原子生成本地 result.json。
10. 计算 result SHA-256并 PUT 到 MinIO。
11. 发布 `RUNNER_RESULT_AVAILABLE`。
12. finally 停止 SparkSession 和 Kafka Producer。

## 6. SparkSession 平台化

删除 Runner 中硬编码的：

```java
.master("local[*]")
```

规则：

- Local Docker Backend 在 launch 环境显式声明本地模式，Runner 设置 `local[*]`。
- YARN/Kubernetes 不设置 master 和 deploy mode，由 spark-submit 注入。
- appName 包含 executionId，便于日志和集群追踪。
- 统一保留 caseSensitive、ANSI、UTC 等 Canvas 语义配置。
- Runner 不覆盖集群注入的 executor、memory、queue、namespace 等配置。

## 7. Kafka Producer

Runner 使用轻量 `kafka-clients`，不启动 Spring：

```properties
acks=all
enable.idempotence=true
max.in.flight.requests.per.connection=5
delivery.timeout.ms=30000
request.timeout.ms=10000
```

事件发送有限重试。Runner 没有数据库 Outbox，因此可靠性依靠：

```text
result.json 已上传
+ Kafka 发送重试
+ Dispatcher Backend 终态巡检
+ Dispatcher 主动检查 result 对象
```

如果结果上传成功但 Kafka 失败，Runner 可以非零退出；Dispatcher 发现外部应用终止后仍读取已存在的 result.json 并收敛状态。

`RUNNER_RESULT_AVAILABLE` 只表示 result 对象已经可校验，不直接触发 Admin 终态。Dispatcher 等 Backend 确认终止，收集并上传 `console.log`，再应用经过校验的 result 并发布权威终态事件。这样 Admin 收到终态时日志对象已经可用。

## 8. Result 契约

结果保持安全、稳定：

```json
{
  "schemaVersion": 2,
  "executionId": "uuid",
  "runId": "uuid",
  "attempt": 1,
  "state": "SUCCESS",
  "startedAt": "...",
  "endedAt": "...",
  "durationMs": 1234,
  "affectedRows": 20,
  "nodeResults": [
    {
      "nodeId": "uuid",
      "nodeType": "JDBC_OUTPUT",
      "nodeName": "结果输出",
      "state": "SUCCESS",
      "phase": "WRITE",
      "startedAt": "...",
      "endedAt": "...",
      "durationMs": 800,
      "rowsWritten": 20,
      "message": "JDBC 输出写入成功",
      "error": null
    }
  ],
  "error": null
}
```

失败结果示例：

```json
{
  "schemaVersion": 2,
  "executionId": "uuid",
  "runId": "uuid",
  "attempt": 1,
  "state": "FAILED",
  "startedAt": "...",
  "endedAt": "...",
  "durationMs": 931,
  "affectedRows": null,
  "nodeResults": [
    {
      "nodeId": "65b9615d-b72a-42c1-8e4e-f28a660da082",
      "nodeType": "JDBC_INPUT",
      "nodeName": "用户输入",
      "state": "FAILED",
      "phase": "READ",
      "startedAt": "...",
      "endedAt": "...",
      "durationMs": 812,
      "rowsWritten": null,
      "message": "数据源用户无权读取表 dev_source.sys_user",
      "error": {
        "code": "JDBC_PERMISSION_DENIED",
        "message": "数据源用户无权读取表 dev_source.sys_user",
        "category": "PERMISSION",
        "retryable": false,
        "nodeId": "65b9615d-b72a-42c1-8e4e-f28a660da082",
        "nodeType": "JDBC_INPUT",
        "nodeName": "用户输入",
        "phase": "READ",
        "sqlState": "42501",
        "diagnosticId": "uuid"
      }
    }
  ],
  "error": {
    "code": "JDBC_PERMISSION_DENIED",
    "message": "数据源用户无权读取表 dev_source.sys_user",
    "category": "PERMISSION",
    "retryable": false,
    "nodeId": "65b9615d-b72a-42c1-8e4e-f28a660da082",
    "nodeType": "JDBC_INPUT",
    "nodeName": "用户输入",
    "phase": "READ",
    "sqlState": "42501",
    "diagnosticId": "uuid"
  }
}
```

`schemaVersion: 2` 是当前唯一可接受版本，Dispatcher严格拒绝 v1、未知字段、身份或时间不一致、错误码/SQLState格式错误、节点状态与错误对象不一致，以及顶层/节点诊断 ID不一致的结果。节点结果按拓扑执行顺序保存；失败时保留已完成节点并追加失败节点，未开始节点不写入。SUCCESS禁止携带错误，FAILED、TIMED_OUT和CANCELLED必须携带安全错误。

错误类别固定为 `CONFIGURATION/CONNECTION/AUTHENTICATION/PERMISSION/SCHEMA/CONSTRAINT/TIMEOUT/CANCELLED/RESOURCE/EXTERNAL_SYSTEM/INTERNAL`；阶段固定为 `PREPARE/READ/PROCESS/WRITE/DELIVERY/DISPATCH`。SQLState `08xxx/28xxx/42501/23xxx/57014` 分别映射连接、认证、权限、约束和超时错误。只有连接、网络超时和暂时性外部系统故障标记为可重试；本阶段不自动重试。

结果原子写入：

```text
result.json.tmp
→ fsync/close
→ atomic move result.json
→ SHA-256
→ MinIO PUT
```

禁止包含：

- JDBC 密码和完整 Properties。
- 预签名 URL。
- Kafka认证信息。
- Spark Plan。
- 本地文件路径。
- 未脱敏数据库异常堆栈。

### 8.1 节点日志

控制台日志固定使用以下生命周期事件：

```text
TASK_START / TASK_SUCCESS / TASK_FAILED
NODE_START / NODE_SUCCESS / NODE_FAILED
```

每条节点日志带 executionId、runId、attempt、节点 ID/类型/名称、阶段和耗时，并只记录安全的表名、数据源 ID、Join类型、条件数量和写入模式。Input 和 Join 的成功表示逻辑计划准备完成，不额外触发 Spark Action。Output 覆盖目标 Schema解析、物化写入、按需 TRUNCATE和写入指标采集。

节点失败日志带诊断 ID、分类和经过脱敏的异常链/调用栈；同一异常只打印一次完整栈，任务失败日志只打印摘要。调用栈最大 64 KiB，超限必须有明确截断标记。日志不得输出 manifest、完整 JDBC Properties、数据行、SQL参数值、密码、Secret、Token、Credential、Access Key、签名参数或预签名 URL。

## 9. 真实执行语义

保持现有第一期语义：

- JDBC 只支持 PostgreSQL 和 MySQL。
- Input 使用数据库类型对应的限定名和标识符引用。
- Join 支持 INNER、LEFT、RIGHT、FULL。
- 多条件只支持 EQUALS + AND。
- Output 支持 BY_NAME/EXPLICIT。
- APPEND 使用 Spark JDBC append。
- OVERWRITE 使用数据库 TRUNCATE 后 append，不 drop/recreate。
- 多个 Output 按稳定拓扑顺序执行，不提供跨库事务。
- 每个 Output 使用 `Dataset.observe` 将 `rowsWritten=count(1)` 注入同一次 JDBC 写入计划，禁止写入前独立执行 `count()`。
- `QueryExecutionListener` 按 executionId、attempt 和 nodeId 关联指标；每个 Output 使用独立 Spark Job Group。
- 只有 JDBC `save()` 成功后才采纳 `rowsWritten`，多个 Output 的 `affectedRows` 是各输出行数之和。
- 写入成功但指标缺失时任务仍为 SUCCESS，节点 `rowsWritten` 和任务 `affectedRows` 保持 null，不得伪装成 0。
- Canvas 行数表示进入 JDBC Writer 的逻辑输出行，不等同于数据库触发器执行后的物理表行数。
- Runner 显式关闭 Spark 推测执行，降低有副作用 JDBC 写入被并行重复执行的风险；本阶段仍不承诺数据库 Exactly Once。
- 真实任务不自动重试。

## 10. 两种 Runner 制品

暂时继续由 `data-scalpel-task-engine` 构建：

### 10.1 Local Uber JAR

```text
data-scalpel-task-engine-*-runner-local.jar
```

包含：Spark、Scala、Jackson、Kafka Client、PostgreSQL/MySQL Driver。可直接：

```text
java -jar task-runner-local.jar
```

### 10.2 Cluster JAR

```text
data-scalpel-task-engine-*-runner-cluster.jar
```

- Spark 和 Scala 使用 provided，由目标 Spark 4.1.1 集群提供。
- 包含 Runner、共享执行契约、Jackson、Kafka Client、PostgreSQL/MySQL JDBC Driver及 Kafka 所需压缩库。
- 不包含 Spark、Scala、Hadoop、Netty；这些类由目标 Spark 4.1.1 集群提供。
- 不允许把另一版本 Spark/Scala 打进 YARN/Kubernetes Driver classpath。

两个制品运行相同 Main 和相同 manifest/launch/result 协议。

## 11. 三种启动交付

### Local Docker

只读 bind mount：

```text
/work/launch.json
```

### YARN cluster

使用本地权限受限临时文件：

```text
--files /secure/work/{executionId}/launch.json#launch.json
```

文件内容不得出现在命令或提交日志中。预签名 URL有效期从真正提交时开始计算。

### Kubernetes cluster

创建每次执行独立 Secret，挂载：

```text
/opt/datascalpel/runtime/launch.json
```

Driver 启动后即可删除 API 中的临时 Secret还不安全，因为 Pod 可能尚未完成挂载；应在 Driver Pod 已创建并确认挂载后，或终态清理时删除。

## 12. 取消与超时

Runner 不直接消费取消 Topic。取消由 Dispatcher 调用 Backend 原生能力：

- Docker stop/kill。
- YARN application -kill。
- Kubernetes delete Driver Pod/Application。

Runner 自己在以下边界检查 deadline 和线程中断：

- 下载 manifest 前后。
- 编译各节点之间。
- 每个 Output 开始前。
- Output 目标结构校验完成后、开始 TRUNCATE/WRITE 前。

不能保证 JDBC 写入已开始后的事务级回滚。

## 13. 测试计划

- launch 严格 JSON、身份不一致、deadline 和 URL校验。
- manifest SHA-256 和大小限制。
- Local 模式设置 master，cluster 模式不覆盖 master。
- result 原子生成、上传后再发事件。
- Kafka 短暂失败重试、永久失败但 result 可恢复。
- 所有异常路径停止 SparkSession 和 Producer。
- PostgreSQL/MySQL Canvas 执行回归。
- Observation/QueryExecutionListener 在同一次 JDBC `save()` 中返回准确输出行数，不产生独立 `count` Action。
- 空输出记录 0；指标缺失保留 null；多输出按节点汇总且任一指标缺失时总数为 null。
- local JAR 使用 JDK 21 `java -jar` 启动。
- cluster JAR 检查不包含 Spark/Scala 重复类。
- 输出和日志敏感信息扫描。

## 14. 阶段退出条件

- Runner 不再依赖 HTTP callback 和 Docker bind result 作为唯一结果通道。
- 同一 Runner 协议适配三种部署模式。
- Local Uber JAR和 Cluster JAR都能构建。
- result 上传与 Runner Kafka事件链路完成。
- Dispatcher 可以根据 Runner 事件和 MinIO结果收敛执行状态。
