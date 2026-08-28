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
  "launchVersion": 4,
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
  },
  "qualitySamples": [
    {
      "ruleId": "uuid",
      "putUrl": "short-lived-presigned-put-url",
      "objectKey": "task-runs/{runId}/attempts/{attempt}/quality/samples/{ruleId}.parquet",
      "maximumBytes": 20971520
    }
  ],
  "userJar": {
    "getUrl": "short-lived-presigned-get-url",
    "sha256": "64-lowercase-hex",
    "sizeBytes": 102400
  }
}
```

约束：

- launch.json 不是 Canvas Definition，也不持久化到任务定义。
- 预签名 URL和 Kafka认证参数不得进入结果、日志和异常消息。
- Dispatcher 在任务真正出队时生成短期 URL。
- launch v4 的 `qualitySamples` 仅为本次质检可生成样本的行级规则签发固定对象 Key PUT 地址；
  Canvas 或关闭样本时为空。该清单不进入 Manifest、Result、日志或 Kafka 事件。
- `userJar` 只在 `SPARK_JAR` 出队时存在，和 Manifest 使用相互独立的短期下载地址与 100 MiB 大小限制。
- launch 文件只读交付给 Runner，完成后由 Dispatcher/集群清理。
- SASL 密码使用独立受限文件或环境变量引用，不直接放进 `spark-submit` 参数。

## 4. Manifest v21

Admin 当前写出 `manifestVersion: 21`；Runner 严格只接受 v21。升级时先停止或排空旧 Runner，
再统一发布 Admin、Dispatcher 和 Runner：

```text
manifestVersion
execution
task
metadataSnapshot
runtimeDataSources
streaming
runtimeFileStorage
runtimeFileInputs
snapshotSyncLimits
taskType
modelQuality
sparkJarJob
```

边界：

- `taskType` 显式区分 `SPARK_CANVAS/SPARK_STREAMING_CANVAS/SPARK_MODEL_QUALITY/SPARK_JAR`。
- Canvas 使用 `task.definition`，它是纯 Canvas 定义；模型质检改用互斥的 `modelQuality` 载荷，
  不生成 Canvas 节点，也不进入 Canvas 编译器。
- Spark JAR 使用互斥的 `sparkJarJob`，保存 Job API/Class、有序参数、允许的 Spark Conf、资源绑定和
  触发身份；`task`、`streaming`、`modelQuality` 必须为空。用户 JAR本体和下载地址不进入 Manifest。
- `modelQuality` 保存目标模型、字段和物理位置、实际执行规则、跳过规则、引用模型及有效码表值快照；
  JDBC 凭据仍只位于 `runtimeDataSources`。v21 继续保存本次失败样本上限，并在字段快照中标记主键。
- `metadataSnapshot` 是逻辑编译与解析依据，不是运行时物理 Schema 相等契约。
- `runtimeDataSources` 是真实 JDBC、Kafka、HTTP API、外部 S3 和按需 TDengine TMQ 运行信息；
  外部凭据只存在于此私有 Manifest。`tdEngineTmqConnection` 仅在任务引用 TMQ 节点时生成，包含
  WebSocket 地址、账号、密码和 SSL 开关。
- `runtimeFileStorage` 仅在引用文件 Input 时存在；`runtimeFileInputs` 按稳定逻辑表 ID 去重。表级保存
  `fileDatasetId/fileDatasetTableId/schemaFingerprint` 和统一解析参数；`schemaFingerprint` 仅为
  Manifest 向后兼容信息，Runner 不用它比较运行时 Schema；
  `sources` 按当前顺序保存来源 ID、文件 ID、格式、压缩方式、存储形态、私有对象
  位置和来源键。Runner 对来源逐一使用 metadataSnapshot 中的同一权威 Schema 和 FAILFAST Reader，
  再按清单顺序执行 `unionByName`，语义固定为 `UNION ALL`。
- `snapshotSyncLimits` 是 Admin 受保护运行配置，固定携带每侧最大行数、来源与目标合计估算字节上限
  和锁等待秒数；默认分别为 `100000`、`268435456` 和 `30`，不得回写 Canvas Definition。
- `streaming` 从唯一无界输入提取触发间隔。JDBC 增量输入还可携带来源节点、SHA-256 来源签名和
  跨定义版本的初始 Offset；Runner 原样校验签名，但不会用它覆盖同版本已有 Spark Checkpoint。
- Kafka、Dispatcher、Backend、预签名 URL不进入 manifest。
- v17 及更早版本不再兼容。Admin、Dispatcher 和 Task Engine 必须同步部署，升级前必须排空或取消
  旧版本任务；旧实时任务升级后按原定义重新部署并使用新版本 Checkpoint 前缀。
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
4. Spark JAR任务额外下载用户 JAR并校验准确大小和 SHA-256。
5. 创建 SparkSession 后发布 `RUNNER_STARTED`。
6. 使用 metadataSnapshot 再次执行完整 Canvas 编译。
7. 按逻辑 Schema 构造 Input/Output 计划，不执行物理 Schema 相等校验。
8. 构造完整非输出计划。
9. 按稳定拓扑顺序执行 Output；Snapshot Sync 使用独立单连接事务与严格目标表写锁。
10. 质检失败规则按需在 Driver 生成有界 Parquet，并使用 launch v4 中的短期固定 Key PUT 地址上传。
11. Spark JAR使用父优先 ClassLoader 调用 SDK `SparkBatchJob.execute()`，结果固定 `nodeResults=[]`。
12. 原子生成本地 result.json。
13. 计算 result SHA-256并 PUT 到 MinIO。
14. 发布 `RUNNER_RESULT_AVAILABLE`。
15. finally 停止 SparkSession 和 Kafka Producer。

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
  "schemaVersion": 8,
  "taskType": "SPARK_CANVAS",
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
      "metrics": null,
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
  "schemaVersion": 5,
  "taskType": "SPARK_CANVAS",
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
      "metrics": null,
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

Runner 当前只写 `schemaVersion: 8`。v4 增加顶层 `taskType` 和互斥载荷：Canvas 只能使用
`nodeResults`，模型质检只能使用 `qualityResult`，`SPARK_JAR` 的 `nodeResults` 必须为空且不含
`qualityResult`；Dispatcher 兼容读取历史 v2～v8，以收敛
升级前已经运行的任务，并
严格拒绝 v1、未知字段、身份或时间不一致、错误码/SQLState格式错误、节点状态与错误对象不一致，
以及顶层/节点诊断 ID不一致的结果。节点结果按拓扑执行顺序保存；失败时保留已完成节点并追加
失败节点，未开始节点不写入。SUCCESS禁止携带错误，FAILED、TIMED_OUT和CANCELLED必须携带
安全错误。

模型质检成功结果必须包含至少一个已执行规则，并满足
`totalRules = passedRules + failedRules + skippedRules`。规则指标使用异常量、行数或新鲜度三种
判别联合；Dispatcher 会校验非负范围、异常比例、阈值、规则状态与指标结论的一致性。数据不合格时
Runner 状态仍为 `SUCCESS`，只把质量结论设为 `FAILED`；连接、读取或表达式错误使 Runner 进入技术
失败终态。技术失败仍携带 `qualityResult`，但质量结论和汇总字段为空，不为失败规则伪造质量指标；
它可保留失败前已完成的规则、跳过规则，以及不含数据值的失败规则身份。规则级失败上下文与顶层
`error` 必须使用同一个诊断 ID；如果错误发生在规则执行之前，失败规则上下文可以为空。

v5 为每条质检规则增加样本状态 `NOT_FAILED / NOT_APPLICABLE / DISABLED / AVAILABLE`。只有失败的
11 类行级规则可以是 `AVAILABLE`；行数和新鲜度规则固定为 `NOT_APPLICABLE`。AVAILABLE 只记录样本
行数、异常总数、是否截断、文件大小、SHA-256、行可定位性和字段元数据，不记录对象 Key 或业务值。
Dispatcher 根据执行账本中的规则 ID 和运行身份推导固定对象 Key，并校验对象存在、20 MiB 单文件与
100 MiB 单运行上限、SHA-256 和 Parquet `PAR1` 头尾。v6 增加 Spark JAR 用户作业观测快照；v7
增加普通多目标 Output 的逐写入结果；v8增加批处理 Spark JAR运行期 Catalyst血缘证据。成功的批处理
JAR必须携带证据，无 SDK写入时使用 `UNAVAILABLE/NO_SDK_WRITES`；创建上下文前失败允许为空，其他任务
类型禁止携带该字段。Dispatcher校验数量、字符串长度与 5 MiB结果上限，但不把完整血缘放进 Kafka，终态
事件只携带已校验的 `resultSha256`。Business通过固定结果对象异步读取、核对摘要和发布正式快照。新 Runner
不再写旧版本结果。

v3 为节点结果增加可空的判别联合 `metrics`。成功的 JDBC/模型快照同步节点使用：

```json
{
  "kind": "SNAPSHOT_SYNC",
  "sourceRows": 100,
  "targetRows": 98,
  "insertedRows": 5,
  "updatedRows": 3,
  "deletedRows": 2,
  "unchangedRows": 92,
  "retainedTargetOnlyRows": 1
}
```

必须满足 `sourceRows = insertedRows + updatedRows + unchangedRows`，以及
`targetRows = deletedRows + retainedTargetOnlyRows + updatedRows + unchangedRows`。
Snapshot Sync 的 `rowsWritten` 固定为新增、更新、删除之和；失败或事务回滚节点不得携带成功
指标。

v7 中普通 `JDBC_OUTPUT/MODEL_OUTPUT/FILE_OUTPUT` 无论包含一条还是多条写入，都只生成一条节点
结果，并使用：

```json
{
  "kind": "OUTPUT_WRITES",
  "writes": [
    {
      "writeId": "uuid",
      "sourceTableName": "orders",
      "targetDisplayName": "ods_orders",
      "state": "SUCCESS",
      "affectedRows": 10562,
      "errorCode": null
    }
  ]
}
```

最终制品只允许 `SUCCESS/FAILED/SKIPPED`。执行按配置顺序串行进行；失败前已提交项保留成功行数，
当前项携带与节点错误一致的稳定错误码，当前节点内尚未开始的项标为 `SKIPPED`。一个 Canvas 节点
在 `nodeResults` 中必须唯一，不能为每条写入重复生成相同 `nodeId`。`rowsWritten` 和顶层
`affectedRows` 汇总已成功提交项；只要任一成功项行数未知或汇总溢出，汇总即为 `null`。尚未开始
的其他 Output 节点不伪造节点结果。Kafka 多目标实时写入继续按 `writeId` 隔离 StreamingQuery 与
Checkpoint，其运行明细遵循实时查询事件，不把物理 Checkpoint 或连接信息写入 Result。

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

- 普通标量 JDBC 读取和 APPEND 支持 PostgreSQL、MySQL、openGauss、Kingbase、Oracle、
  SQL Server、ClickHouse 和达梦；TDengine 保持超级表和 TMQ 专用规则。
- Input 使用数据库类型对应的限定名和标识符引用。
- Join 支持 INNER、LEFT、RIGHT、FULL。
- 多条件只支持 EQUALS + AND。
- Output 使用目标字段到来源字段的显式映射。
- APPEND 使用 Spark JDBC append。Oracle、SQL Server、ClickHouse 和达梦第一阶段只承诺写入
  已存在物理表，不自动建表或演进 Schema。
- OVERWRITE 对 PostgreSQL、MySQL、openGauss、Kingbase、Oracle、SQL Server、ClickHouse 和达梦均执行
  数据库 TRUNCATE 后 append，不 drop/recreate，也不承诺两步原子性。TDengine 不支持普通 JDBC 输出；
  外键、权限、ClickHouse 集群表及数据库版本造成的 TRUNCATE 失败由真实运行返回 JDBC 错误。
- UPSERT、JDBC Query Input 和 Snapshot Sync 仅支持 PostgreSQL/MySQL；JDBC 增量输入额外支持
  openGauss/Kingbase；Geometry JDBC 仅支持 PostgreSQL/PostGIS 和 MySQL 8。
- 模型质检允许全部普通 JDBC 数据库的标量读取；Spark JAR 允许标量读取和 APPEND，并复用同一
  OVERWRITE、UPSERT 和 Geometry 能力边界。
- 多个 Output 按稳定拓扑顺序执行，不提供跨库事务。
- `JDBC_SNAPSHOT_SYNC_OUTPUT/MODEL_SNAPSHOT_SYNC_OUTPUT` 只接受 BATCH 有界来源，先按目标
  Schema 完成字段映射和显式 Cast，再按用户指定的 `1..32` 个目标字段 Key 比较来源与目标。
- Snapshot Sync 使用一条 PostgreSQL/MySQL JDBC 连接和严格目标表写锁，在同一事务中读取目标、
  校验两侧 Key 非空且唯一，并按 `DELETE → UPDATE → INSERT` 执行；Geometry 使用拓扑相等。
- Snapshot Sync 删除默认关闭；启用时来源为空且目标非空固定拒绝，并在任何 DML 前同时校验最大
  删除数量和 `deletedRows / targetRows` 比例，任一超限整次回滚。
- 使用 Spark JDBC `save()` 的普通 Output 通过 `Dataset.observe` 将 `rowsWritten=count(1)` 注入同一次
  写入计划，禁止为了指标在写入前独立执行 `count()`。
- `QueryExecutionListener` 按 executionId、attempt 和 nodeId 关联普通 Spark Writer 指标；每个
  Output 使用独立 Spark Job Group。
- 只有 JDBC `save()` 成功后才采纳普通 Output 的 `rowsWritten`；Snapshot Sync 只在事务提交后采纳
  分类指标。多个 Output 的 `affectedRows` 是各输出已提交行数之和。
- 写入成功但指标缺失时任务仍为 SUCCESS，节点 `rowsWritten` 和任务 `affectedRows` 保持 null，不得伪装成 0。
- Canvas 行数表示进入 JDBC Writer 的逻辑输出行，不等同于数据库触发器执行后的物理表行数。
- Snapshot Sync 是例外：其 `rowsWritten/affectedRows` 表示已经提交的新增、更新、删除分类数量之和，
  不使用 JDBC 驱动的 affected-row 语义。
- Runner 显式关闭 Spark 推测执行，降低有副作用 JDBC 写入被并行重复执行的风险；本阶段仍不承诺数据库 Exactly Once。
- 真实任务不自动重试。

## 10. 两种 Runner 制品

暂时继续由 `data-scalpel-task-engine` 构建：

### 10.1 Local Uber JAR

```text
data-scalpel-task-engine-*-runner-local.jar
```

包含：Spark、Scala、Jackson、Kafka Client，以及 PostgreSQL、MySQL、openGauss、Kingbase、
Oracle、SQL Server、ClickHouse、达梦和 TDengine JDBC Driver。可直接：

```text
java -jar task-runner-local.jar
```

### 10.2 Cluster JAR

```text
data-scalpel-task-engine-*-runner-cluster.jar
```

- Spark 和 Scala 使用 provided，由目标 Spark 4.1.1 集群提供。
- 包含 Runner、共享执行契约、Sedona 1.9.0、Jackson、Kafka Client、全部受支持 JDBC Driver、
  ClickHouse 必需运行依赖及 Kafka 所需压缩库。
- 不包含 Spark、Scala、Hadoop、Netty；这些类由目标 Spark 4.1.1 集群提供。
- 不允许把另一版本 Spark/Scala 打进 YARN/Kubernetes Driver classpath。
- 构建验证必须确认 `SedonaContext`、`GeometryUDT` 和全部 JDBC Driver 可加载，同时继续拒绝
  Spark、Scala、Hadoop核心类泄漏。

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

正常取消或实时停止无法收敛时，管理端可发送独立的 `FORCE_TERMINATE_EXECUTION`。强制终止跳过
Runner 正常退出：Docker 直接 kill，YARN 直接 application kill，Kubernetes 使用零宽限期删除
Driver 和 Executor。确认后运行记录进入 `CANCELLED`；30 秒内无法确认 Backend 终态时进入
`FAILED`，错误码为 `EXECUTION_TERMINATION_UNCONFIRMED`，并提示外部 Application 可能仍需人工处理。

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
- PostgreSQL/MySQL/openGauss/Kingbase/TDengine 存量能力回归，以及 Oracle、SQL Server、
  ClickHouse、达梦普通标量读取和 APPEND 冒烟。
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
