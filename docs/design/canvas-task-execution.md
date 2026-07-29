# Canvas 真实执行设计

## 1. 范围与调用链

已发布的 `SPARK_CANVAS` 可以手动运行，也可以由 Quartz 定时计划触发真实运行。JDBC 输入/输出支持 PostgreSQL 和 MySQL，HTTP/JSON API 支持只读输入：

```text
Admin → 私有 MinIO manifest + Kafka command → Task Dispatcher → Runner
Admin ← Kafka execution event ← Task Dispatcher ← Kafka runner event
                                      └─ Local Docker / YARN / Kubernetes
```

Admin负责读取权威元数据、调用 Task Engine最终预检、生成不可变 manifest，并原子持久化 `TaskRun + Kafka Outbox`。Task Engine只负责预检。Dispatcher使用自己的 PostgreSQL账本负责准入、调度、恢复和终态收敛；Runner负责一次真实 Spark作业，完成后退出。手动和定时触发复用同一条提交链路；定时 TaskRun 使用 `SCHEDULED + REAL`，并以 `(scheduleId, scheduledFireAt)` 幂等。`FORBID` 在存在活动实例时记录 `SKIPPED`，`ALLOW` 为每个触发点提交独立 Spark Application，不检查其他运行、任务或节点是否使用相同模型和物理 Sink。当前不支持自动重试和跨输出事务。

## 2. Manifest 边界

manifest 当前写出 `manifestVersion: 7`；Runner 兼容读取 v6，v6 维持原有行为，v5 及更早版本不再兼容。顶层分为 `execution`、`task`、`metadataSnapshot`、`runtimeDataSources`、可空 `runtimeFileStorage` 和 `runtimeFileInputs`：

- `task.definition` 是原始稳定 Canvas 定义，绝不追加连接字段。
- `metadataSnapshot` 是 Admin 在发布、启用或运行时读取的权威表结构和模型快照；`models` 只服务 `MODEL_INPUT/MODEL_OUTPUT`，没有模型节点时必须是空数组。Kafka Value Schema 已内联在节点定义中，不进入模型快照。
- `metadataSnapshot.fileDatasetTables` 只包含文件表 ID、稳定 code、展示名、数据集类型、表/文件状态和按顺序排列的平台字段 Schema。
- `runtimeDataSources` 只包含当前定义实际引用的数据源，并以 `dataSourceId` 去重。
- JDBC URL、Catalog、Schema、用户名、密码和白名单连接参数，HTTP API 连接配置、运行凭据和被引用的 API 资源定义，以及外部 S3 的 endpoint、region、bucket、rootPrefix、pathStyleAccess 和凭据，仅位于 `runtimeDataSources`。
- 只有任务实际引用文件输入时才生成 `runtimeFileStorage`。`runtimeFileInputs` 按 Table ID 去重；表级保存数据集/表 ID、`schemaFingerprint`、权威 Schema 和强类型解析参数，每个输入再保存按当前顺序排列的来源列表。来源项保存稳定来源 ID、文件 ID、格式、压缩、存储形态、私有读取位置和来源键。

manifest第一阶段为明文 JSON，存放于私有 Bucket，便于排查。生产环境的对象存储 Endpoint 和短期预签名 URL必须使用 TLS；Bucket不得开放匿名读取。Admin向 Dispatcher只发送对象 Key和 manifest SHA-256；Dispatcher在真正提交时生成短期预签名 URL。对象路径固定为：

```text
task-runs/{runId}/attempts/1/manifest.json
task-runs/{runId}/attempts/1/result.json
task-runs/{runId}/attempts/1/console.log
```

密码、预签名 URL、完整 JDBC Properties、文件对象 Key、物化前缀、来源 Key和执行器临时路径不得进入日志、回执、`result.json`、Kafka终态事件或 `TaskRun` 字段。Manifest 不提供用户下载接口；Result 和 Console Log 由 Admin鉴权后代理下载，单次分别限制为 5 MiB和 20 MiB。对象保留期由 Bucket生命周期策略控制；运行最终事务失败时 Admin会最佳努力删除已经上传的孤立 Manifest，生命周期策略负责兜底。

## 3. Admin 准备与状态

发布、重新启用和每次手动运行都会在管理数据库事务外：

1. 读取当前 Canvas 定义快照。
2. 解析 Input/Output 引用的数据源、模型和物理表；Kafka 节点只解析数据源与 Topic，不解析模型。
3. 校验模型节点引用的模型发布状态和物理结构，以及数据源/API 资源启用状态、连接类别和 SOURCE/STORAGE/DISTRIBUTION 用途。
4. 读取真实 JDBC 表元数据、模型字段和物理位置；HTTP API 使用资源声明的输出 Schema；文件输入读取当前来源、平台 Schema 和 Schema 指纹，然后构建 `metadataSnapshot` 与有序来源 Manifest，不读取文件内容。
5. 调用 Task Engine 编译接口，要求 `valid=true`。
6. 在短事务中重新检查定义版本、模型 `schemaVersion/updatedAt/status` 和数据源 `updatedAt`，然后保存已经生成的文件来源快照。文件数据不执行二次版本比对。

运行时 Admin先上传 manifest，再在短事务内原子创建 `QUEUED` TaskRun 和 `SUBMIT_EXECUTION` Outbox。TaskRun保存独立的 executionRunId、executionId、attempt、deadline、计算引擎路由快照和三个对象 Key，不保存密码。Kafka暂时不可用时 Outbox保留并重试。

状态流转为：

```text
QUEUED → RUNNING → SUCCESS | FAILED | TIMED_OUT
                 → CANCEL_REQUESTED → CANCELLED
```

Dispatcher和Runner不反向调用 Admin HTTP。Runner向 Kafka发送状态，Dispatcher校验并落账后通过 Admin事件 Topic发布；Admin按 engineId、runId、executionId、attempt和递增 sequence幂等应用。Admin低频通过 Dispatcher HTTP查询长期未收敛执行，作为 Kafka事件丢失时的修复路径。

## 4. Dispatcher 执行管理

Admin允许主动查询和管理 Dispatcher，但正式提交与取消使用 Kafka。Dispatcher HTTP仅保留控制面查询：

```http
GET /api/v1/task-executions/{executionId}
```

取消命令与提交命令一样只通过 Kafka发送，不提供 Dispatcher HTTP取消旁路。

Dispatcher根据部署时固定的 Backend选择 Local Docker、YARN cluster或 Kubernetes cluster。默认并发和队列由计算引擎注册策略控制。动态预签名 URL只进入受限 launch文件，不写入日志或命令行。超时、取消、日志采集和重启恢复均由 Backend实现，Dispatcher在上传日志并校验 result后发布最终 Admin事件。

## 5. Runner 语义

Runner读取 launch描述、下载 manifest、校验 SHA-256、严格反序列化并验证执行身份和 deadline，然后使用 Backend提供的 Spark环境（Local为 `local[*]`，集群模式使用现有 SparkContext）。它先使用零行编译器再次验证完整 Canvas，再读取真实 JDBC Schema；所有 Input 和 Output计划准备完成前不发生写入。Compiler 与 Runner 通过同一个内置 Registry 调用同一组 Input、Processor、Output Operator，只分别注入零行无副作用 I/O 与真实运行 I/O。

- `JDBC_INPUT`：按 PostgreSQL Schema 或 MySQL Database 限定并引用物理表。
- `FILE_DATASET_INPUT`：仅用于批任务，使用 Manifest 的私有 S3 配置和逻辑表运行快照读取提交时的有序来源，输出以逻辑表 code 命名的 `BOUNDED` Dataset。CSV/TSV/TXT/JSON/JSONL/Parquet/Avro/SHP 对每个来源使用同一权威 Schema 与 FAILFAST Reader，并按 Manifest 顺序 `unionByName`，语义为 `UNION ALL`；Excel/GDB 当前仍为单物理文件下的 Sheet/图层来源。覆盖、替换或删除会立即清理旧对象，因此旧任务允许以文件读取错误失败。
- `HTTP_API_INPUT`：按资源快照执行鉴权、签名、分页或异步轮询；每个结果页按最多 10,000 行分批转换，并立即通过 `DISK_ONLY` eager Local Checkpoint 物化到 Spark Executor 磁盘，全部批次成功后再按显式 Schema 合并为 DataFrame，以节点的 `outputTableName` 注册逻辑表。详细内存和失败语义见 [HTTP API 数据源第二阶段：分批读取设计](http-api-data-source-phase-two-batched-reading.md)。
- `MODEL_INPUT`：按模型快照的精确物理位置读取，以模型 code 作为逻辑表名。
- `KAFKA_INPUT`：直接使用节点内联 Value Schema 解析消息并生成无界表；运行时连接只来自 Manifest，不查询数据模型。
- `JOIN`：支持 INNER、LEFT、RIGHT、FULL；多个 EQUALS 条件固定使用 AND。
- `MODEL_OUTPUT`：目标和字段来自模型快照，APPEND 可写受管或外部模型，OVERWRITE 只允许受管模型。
- `JDBC_OUTPUT`：BY_NAME/EXPLICIT 都由共享 Operator 使用 `select + alias + 显式 cast`；APPEND 使用 Spark JDBC append。
- `KAFKA_OUTPUT`：目标字段来自节点内联 Value Schema，BY_NAME/EXPLICIT 与其他 Output 复用映射和 Cast；可选 Key 字段来自上游表。
- `FILE_OUTPUT`：仅用于批任务，将来源表写到精确的 `s3a://{bucket}/{rootPrefix}/{targetPath}/`。CSV、JSON Lines 固定 UTF-8，Parquet 固定 Snappy；输出采用 Spark 目录数据集语义，允许多个 `part-*` 文件和 `_SUCCESS`。`FAIL_IF_EXISTS` 保留既有目录并失败，`OVERWRITE` 删除旧前缀后写入，且对象存储上的覆盖不是原子替换，失败时可能留下不完整目录。
- `OVERWRITE`：使用 JDBC `TRUNCATE TABLE` 后 Spark append，不 drop/recreate。

Join 等 Processor 的兼容性只以共享 Operator 建立的真实 Spark 表达式及 Analyzer 结果为准，不按平台字段类型另建兼容矩阵或风险警告；Analyzer 接受即通过，拒绝才阻止任务。Output 继续使用专用的轻量转换风险策略：安全转换自动 Cast 且不提示，Spark 支持但可能因实际值失败的转换产生预检警告并继续发布/执行，Analyzer 不支持的 Cast 才阻止任务。Runner 保持 ANSI 模式，风险转换遇到非法值、溢出或精度问题时明确失败，不静默转成 null。

每个输出通过 `Dataset.observe` 在同一次写入计划中采集输出行数，不为日志或统计单独触发 `count()`。多个 Output 按稳定拓扑顺序执行，先前成功写入不会因后续失败回滚；不同目标表或不同数据源之间没有分布式事务。JDBC OVERWRITE 先执行 `TRUNCATE`；S3 OVERWRITE 删除旧前缀后写入。后续失败可能留下空目标或部分数据，因此真实运行不自动重试，页面在提交真实运行前必须明确提示该风险。

外部 S3 使用 `fs.s3a.bucket.<bucket>.*` 的 bucket 级 Hadoop 配置，不读取或覆盖平台 Spark 的默认 S3 连接。相同 bucket 在单次任务中不得出现 endpoint、region、path-style 或凭据不同的配置，也不得与平台文件输入存储发生同 bucket 配置冲突。执行身份至少需要目标前缀的列举、写入、删除和分片上传权限。

Runner 的 `result.json` 固定使用 `schemaVersion: 2`，Dispatcher拒绝 v1。所有 Input（包括 Kafka）、Processor 和 Output（包括 Kafka）分别以 `READ`、`PROCESS`、`WRITE` 阶段记录节点开始、成功或失败；失败结果保留此前已完成的节点，并让顶层错误与失败节点错误共享同一个诊断 ID。结构化错误包含稳定错误码、类别、可重试标记、节点身份、SQLState和诊断 ID，不包含异常堆栈。

HTTP API 请求在最终 URL、Header 和 Body 确定后签名，每页和每次重试重新生成时间戳、Nonce 和签名。OAuth2 Client Credentials 或自定义 Token Endpoint 的 Token 按有效期缓存；业务请求返回 `401/403` 时最多刷新并重试一次。同步分页支持页码、Offset/Limit、Cursor 和 Next URL；异步接口先提交并轮询，只有成功后才进入结果读取及分页。最大页数、行数、响应字节数、持续时间和重复游标/URL检测都是硬限制。

HTTP API 批次的 Local Checkpoint 用于限制 Driver 堆占用，不是容错 Checkpoint。Executor 本地块丢失时本次任务失败并由平台按整次任务处理。批次 Dataset 保留到所有下游 Output Action 完成，随后统一 `unpersist`；Runner 的 `spark.stop()` 负责最终兜底。任务取消仍由 Dispatcher 终止独立 Runner JVM、容器或集群 Application，不增加连接器级取消控制面；已经提交给远端 API 的异步任务不会因此自动取消。

控制台日志记录 `TASK_START/TASK_SUCCESS/TASK_FAILED` 和 `NODE_START/NODE_SUCCESS/NODE_FAILED`。文件 Input 的安全摘要只包含节点 ID、Table ID、table code、格式和字段数；成功只表示读取计划建立完成，不得额外触发 `count()`。节点失败只打印一次经过脱敏的异常链和调用栈，最大 64 KiB；任务级失败只打印摘要。密码、Secret、Token、Credential、Access Key、签名参数、预签名 URL、完整 JDBC Properties、文件对象 Key、物化前缀、来源 Key、临时路径、HTTP 响应业务数据、数据行和 SQL参数值必须被屏蔽。

## 6. 管理端运行观察

Canvas 与 LOCAL_SQL 共用任务运行记录和详情入口。活动状态包括 `QUEUED`、`RUNNING`、`CANCEL_REQUESTED`，详情页每 2 秒刷新；终态停止详情轮询，任务运行列表在无活动实例时降低到每 10 秒刷新。

`task_run` 为 Spark Canvas 保存结构化错误列：错误码、类别、可重试标记、阶段、SQLState、节点 ID/类型/名称和诊断 ID。现有 `message/error_detail` 继续服务 LOCAL_SQL 和通用错误表达。运行详情优先展示结构化错误，不展示 Java 调用栈；用户仍可下载原始 `result.json` 和经过脱敏的控制台日志。

活动 Canvas运行可通过以下 Action取消：

```http
POST /api/v1/task-runs/{runId}/actions/cancel
```

`CANCEL_REQUESTED`重复取消幂等；终态或不支持外部取消的运行返回 `409`。制品接口为：

```http
GET /api/v1/task-runs/{runId}/artifacts/result
GET /api/v1/task-runs/{runId}/artifacts/log
```

两个接口都要求 `task.view`，且只能读取 TaskRun固化的对象 Key，不接受客户端指定任意 Key。Tracking URL只在协议为 HTTP或 HTTPS时渲染为外部链接。

## 7. 构建与本地运行

`data-scalpel-task-engine` 同时生成 Daemon薄 JAR、分发包和两种 Runner制品：

```text
data-scalpel-task-engine-0.1.0-SNAPSHOT-runner-local.jar
data-scalpel-task-engine-0.1.0-SNAPSHOT-runner-cluster.jar
```

本地 `start-local-dev.sh` 会检查 Kafka、MinIO、Docker 和 Local Runner 制品，启动只负责预检的 Task Engine 及 Local Docker Dispatcher，并登记计算引擎。MinIO 或数据库位于宿主机时，应使用容器可访问的 `host.docker.internal` 或实际 DNS；系统不会自动改写数据源地址。
