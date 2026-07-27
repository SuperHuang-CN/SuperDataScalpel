# Task Engine Daemon 与 Canvas 编译设计

## 1. 范围

`data-scalpel-task-engine` 提供彼此隔离的长期预检 Daemon和一次性 Runner构建产物：

- Java 21、Spark 4.1.1、Scala 2.13。
- 普通 Java Main 和 JDK `HttpServer`，不使用 Spring、Servlet、Thrift 或 gRPC。
- 默认以 `local[*]` 长期运行，也可由未来的 `spark-submit --master yarn --deploy-mode client` 启动同一个 Main。
- 兼容读取 Canvas `1.0`～`1.5` 并以 `1.5` 写出；`1.5` 的 `KAFKA_INPUT/KAFKA_OUTPUT` 使用节点内联 Value Schema，不再引用数据模型。
- 根据请求携带的元数据快照创建零行 DataFrame，只构造并分析 Spark 逻辑计划。
- 编译接口不连接 JDBC、不调用 Spark Action、不创建 `DataFrameWriter`。
- Daemon不包含真实执行、Docker、Kafka、MinIO或回执职责；Runner第一阶段只连接 PostgreSQL/MySQL。

模块保留在根 Maven reactor 中，不继承 Spring Boot parent；只复用稳定 execution contracts，不依赖 dialect、business、admin 或 service-engine。这样可避免控制面依赖管理覆盖 Spark官方运行时依赖。

## 2. 结构与职责

```text
TaskEngineDaemon
├─ EngineConfiguration
├─ SparkRuntime
├─ TaskCompilationService
│  └─ CanvasTaskCompiler
│     └─ CanvasGraphPlan
├─ CanvasNodeOperatorRegistry
│  ├─ ModelInputNodeOperator
│  ├─ JdbcInputNodeOperator
│  ├─ FileDatasetInputNodeOperator
│  ├─ HttpApiInputNodeOperator
│  ├─ KafkaInputNodeOperator
│  ├─ JoinNodeOperator
│  ├─ StreamJoinNodeOperator
│  ├─ RenameNodeOperator
│  ├─ ModelOutputNodeOperator
│  ├─ JdbcOutputNodeOperator
│  └─ KafkaOutputNodeOperator
└─ TaskEngineHttpServer

TaskRunnerMain
└─ CanvasTaskExecutor
   └─ 同一个 CanvasNodeOperatorRegistry
```

主要包：

- `contract`：HTTP、任务、Canvas、元数据和编译结果的稳定 record/enum/sealed interface。
- `compiler`：请求并发、超时、取消和元数据索引。
- `canvas`：Compiler 与 Runner 共用的无状态节点 Operator、统一 Registry、执行上下文和预检/运行 I/O 端口。
- `compiler.canvas`：容错图分析、稳定拓扑编排、Schema 传播和编译问题汇总。
- `spark`：基础 SparkSession、请求级 child session、Job Group 和平台类型转换。
- `http`：认证、路由、10 MiB 请求限制、严格 JSON 和 Problem Detail。
- `config`：默认配置、外部 properties 和环境变量覆盖。
- `runner`：manifest 校验、真实 Spark JDBC 计划和结果生成。

第一阶段直接绑定 Spark，不提供计算引擎 SPI 或插件层。

## 3. Spark 生命周期与隔离

进程启动时创建唯一基础 `SparkSession` 和共享 `SparkContext`。每次编译：

1. 取得公平 Semaphore 许可。
2. 拒绝已经处于活动状态的相同 `requestId`。
3. 在编译线程创建 `baseSession.newSession()`。
4. 设置 `task-compilation-{requestId}` Job Group。
5. 按稳定拓扑序编译节点，并在节点之间检查取消和线程中断。
6. 完成后清理 Job Group 和活动请求，不关闭 child session，也不清共享 CacheManager。

只有 Daemon 退出时才停止基础 SparkSession。超时或显式取消会设置请求取消标志、取消对应 Job Group 并中断编译 Future。

默认 Spark 配置：

```properties
spark.master=local[*]
spark.app.name=DataScalpel Task Engine
spark.ui.enabled=false
spark.sql.shuffle.partitions=4
spark.sql.caseSensitive=true
spark.sql.ansi.enabled=true
spark.sql.session.timeZone=UTC
```

`SparkConf` 已有的 `spark.*` 属性优先，因此 `spark-submit` 注入的 master、application name 等不会被默认值覆盖。

## 4. HTTP API

### 4.1 健康检查

以下接口不认证：

```http
GET /health/live
GET /health/ready
```

`live` 表示 HTTP 进程可响应。`ready` 仅在基础 SparkContext 存在且未停止时返回 `200`，响应包含 Spark 版本、Application ID 和 master。

### 4.2 编译任务

```http
POST /api/v1/task-compilations
Authorization: Bearer <token>
Content-Type: application/json
```

请求顶层固定为：

```json
{
  "requestId": "e1ec3ef6-170b-4dc4-bd22-87c482e7af4a",
  "task": {
    "type": "CANVAS",
    "definition": {
      "schemaVersion": 1,
      "schemaMinorVersion": 2,
      "nodes": [],
      "edges": []
    }
  },
  "metadataSnapshot": {
    "dataSources": [],
    "models": []
  }
}
```

JSON 未声明字段、未知任务类型、未知节点类型、非法 enum/UUID 和歧义元数据快照返回 HTTP `400`。配置缺失、图冲突或 Schema 冲突是可分析的业务结果，返回 HTTP `200` 和 `valid=false`。

响应将画布级问题放在 `canvasIssues`，每个定义节点按原数组顺序返回一个 `nodeResults` 条目。`valid` 仅在所有问题都没有 `ERROR` 时为 true。编译问题不写回 Canvas 定义。

### 4.3 取消

```http
POST /api/v1/task-compilations/{requestId}/actions/cancel
Authorization: Bearer <token>
```

活动请求返回 `202` 和 `CANCEL_REQUESTED`；不存在的活动请求返回 `404` Problem Detail。请求完成后可以再次使用相同 requestId。

### 4.4 HTTP 错误

所有 Engine HTTP 错误使用 `application/problem+json`，扩展字段固定为 `code` 和 `timestamp`。未预期异常记录完整堆栈，但响应不暴露类名、Spark 计划、元数据内容或文件路径。

### 4.5 Admin 编译网关

浏览器不直接访问 Task Engine。Canvas Designer 统一调用 Admin 的同路径网关：

```http
POST /api/v1/task-compilations
POST /api/v1/task-compilations/{requestId}/actions/cancel
```

两个接口都要求 `task.view` 权限。Admin 使用明确的 Java record、enum 和 Canvas 节点判别联合接收并转发契约，不依赖 `data-scalpel-task-engine` 模块，也不保存定义、编译结果或元数据快照。

Admin 每次请求都从系统配置 `task.engine.base-url` 读取地址，因而地址修改可以立即生效。Bearer Token 只从 Admin 进程环境变量 `DATASCALPEL_TASK_ENGINE_TOKEN` 读取，不返回浏览器，也不得写入日志。默认连接超时为 3 秒，请求超时为 35 秒。

Canvas Designer 不保留前端 Schema 传播或业务校验器。当前请求返回的 `canvasIssues`、节点 `issues`、`inputTables` 和 `outputTables` 是节点状态、配置选项和定义有效性的唯一来源；定义语义变化后旧结果立即失效，等待元数据和编译期间遮罩工作区。

Engine 返回的 `200 valid=false` 仍作为正常编译结果原样返回。`400/404/409/429/504` 保留状态和安全 detail；Engine 的认证失败、5xx、连接错误统一转为 `502 UPSTREAM_UNAVAILABLE`，响应读取超时转为 `504 UPSTREAM_TIMEOUT`。取消已经结束的请求得到 `404`，设计器将其作为最佳努力取消的可忽略结果。

## 5. 元数据快照

`metadataSnapshot` 是单次编译的临时输入，不属于 Canvas 定义。它只携带：

- 数据源 ID、启用状态、`JDBC` 连接类型和 `SOURCE/STORAGE/DISTRIBUTION` 用途。
- 数据源内的 `TABLE/VIEW`、物理表名和稳定平台字段 Schema。
- 模型 UUID、code、名称、schemaVersion、状态、物理模式、数据源 UUID、精确物理位置和字段 Schema。
- Kafka Value Schema 不属于元数据快照，直接来自 Canvas 节点的内联 `valueSchema`；Kafka 数据源快照只表达数据源状态、连接类型和用途。
- 不包含 URL、账号、密码或其他连接内容。

同一数据源 ID、同一数据源中的表名、同一模型 UUID、同一模型 code 或同一表/模型中的字段名重复会使快照产生歧义并返回 HTTP `400`。JDBC Input 可以读取 TABLE 或 VIEW；Output 目标必须是 TABLE。模型节点的完整快照规则见 [Canvas ModelInput 与 ModelOutput 设计](canvas-model-nodes.md)。

平台类型固定为：

```text
BOOLEAN BYTE SHORT INTEGER LONG FLOAT DOUBLE DECIMAL
STRING BINARY DATE TIMESTAMP TIMESTAMP_NTZ
```

DECIMAL precision 必须在 `1..38`，scale 必须在 `0..precision`；只有 STRING 可以设置正整数 length。

当前设计器从已经加载的真实数据源详情和物理表字段元数据组装临时快照，只包含当前 Canvas 节点引用的数据源和表。同一数据源或物理表重复引用会合并，数据源用途保留真实的集合语义。元数据尚在读取时暂停编译；读取失败时不提交残缺快照。该前端快照只用于本阶段零行预编译，未来保存或执行任务时必须由 Admin 重建可信快照。

## 6. Canvas 图与 Schema 编译

图分析在 ID 校验完成前使用节点数组位置作为身份。依次检查协议版本、公共字段、节点/边 UUID、重复 ID、端点、自连接、重复方向边和节点度数，再使用 Kahn 算法得到稳定拓扑序。未进入拓扑序的节点标记 `CANVAS_CYCLE`。一个分支失败不会阻止无依赖分支，失败分支的下游得到 `UPSTREAM_INVALID`。

编译期表模型为：

```text
Map<tableName, SparkCanvasTable>
```

Map 按定义边顺序无覆盖合并。重复 Key 返回 `DUPLICATE_TABLE_NAME`。Schema、字段列表和表 Map 对处理器只读，处理器总是生成新对象。

### 6.1 JDBC_INPUT

按精确字符串在快照中查找启用的 JDBC SOURCE 数据源和物理表，将平台 Schema 显式转成 Spark `StructType`，再创建零行 DataFrame。输出 Map 只有物理 `tableName` 一个 Key。

### 6.2 MODEL_INPUT

按模型 UUID 查找已发布模型快照，校验数据源可读和物理结构可用，将模型字段转成零行 DataFrame。输出 Map 只有模型不可修改 `code` 一个 Key；模型名称、物理表名和数据源不参与 Key。

### 6.3 KAFKA_INPUT

检查启用且具有 `SOURCE` 用途的 Kafka 数据源、Topic、输出逻辑表名、首次启动位置和节点内联 Value Schema。Value Schema 必须非空、字段名唯一且类型参数合法；Operator 直接用这些字段创建零行无界 Dataset Schema，不查询模型快照。模型仅可由前端作为一次性字段复制来源，不进入编译请求的 Kafka 配置。

### 6.4 JOIN

取得两个直接上游表，检查左右表、输出表名、Join 类型和至少一个 EQUALS 条件。多个条件使用 Spark `Column.equalTo().and()`。左右表字段重名直接返回 `DUPLICATE_COLUMN_NAME`。

编译器实际调用零行 Dataset 的 `join` 构造逻辑计划，并从 Spark 输出 StructType 恢复字段顺序、扩展元数据和外连接可空性。支持 INNER、LEFT、RIGHT、FULL。输出 Map 保留所有输入表并新增 Join 结果表。

Processor 不维护平台类型兼容矩阵，也不按字段类型产生风险警告。Spark Analyzer 接受共享 Operator 建立的表达式时编译通过，Analyzer 拒绝时返回 `SPARK_ANALYSIS_ERROR`。

### 6.5 MODEL_OUTPUT

检查来源逻辑表、目标模型、数据源 STORAGE 用途、物理模式、写入模式和字段映射。字段匹配使用模型字段 code，BY_NAME/EXPLICIT 与 JDBC_OUTPUT 复用同一个字段映射和显式 Spark Cast 实现。APPEND 可写受管或外部模型，OVERWRITE 只允许受管模型。

### 6.6 JDBC_OUTPUT

检查 sourceTableName、启用且具有 `STORAGE`（数据存储）用途的 JDBC 数据源、TABLE 目标、APPEND/OVERWRITE 和 BY_NAME/EXPLICIT 映射。共享 Operator 使用 Spark `select/alias/cast` 构造映射计划并触发 Analyzer；预检 I/O 只接收零行 Dataset，不创建 Writer。

Output 专用字段转换策略分为安全、风险和不支持三类：可证明无损的扩大转换自动 Cast 且不提示；Spark 支持但可能受实际值、nullable、STRING length 或 DECIMAL 精度影响的转换自动 Cast 并产生警告；Spark Analyzer 不支持的 Cast 才产生错误。BY_NAME 的额外来源字段产生警告，目标必填字段缺失和显式重复目标映射仍是错误。该策略不得供 Processor 判断字段类型兼容性。

### 6.7 KAFKA_OUTPUT

检查来源无界表、启用且具有 `DISTRIBUTION` 用途的 Kafka 数据源、Topic、可选 Key 字段以及 BY_NAME/EXPLICIT 映射。目标字段直接取自节点内联 Value Schema，并与其他 Output 复用同一个字段映射和显式 Spark Cast 实现。Compiler 不查询模型、不建立 Kafka Writer；Runner 才使用 Manifest 中的 Kafka 连接信息准备真实流式输出。

## 7. 配置与认证

优先级为“环境变量 > 外部 properties > 内置默认值”。外部配置由 `DATASCALPEL_TASK_ENGINE_CONFIG` 指定；分发脚本默认读取 `conf/task-engine.properties`。

主要环境变量：

```text
DATASCALPEL_TASK_ENGINE_HOST
DATASCALPEL_TASK_ENGINE_PORT
DATASCALPEL_TASK_ENGINE_TOKEN
DATASCALPEL_TASK_ENGINE_CONFIG
DATASCALPEL_TASK_ENGINE_MAX_CONCURRENCY
DATASCALPEL_TASK_ENGINE_ACQUIRE_TIMEOUT_SECONDS
DATASCALPEL_TASK_ENGINE_COMPILE_TIMEOUT_SECONDS
DATASCALPEL_TASK_ENGINE_JAVA_OPTS
```

Token 没有默认值且只允许从环境变量读取；为空时拒绝启动。所有 `/api/v1/**` 使用常量时间 Bearer Token 比较，健康检查不认证。

默认容量：最大并发 2、取得许可超时 10 秒、编译超时 30 秒、HTTP 线程 8。资源取得超时返回 `429`，编译超时返回 `504`，相同活动 requestId 返回 `409`。

## 8. 构建、分发与启动

```bash
./mvnw -pl data-scalpel-task-engine test
./mvnw -pl data-scalpel-task-engine package
```

`package` 同时生成普通薄 JAR，以及目录、zip、tar.gz 三种分发结果。分发内容为：

```text
data-scalpel-task-engine/
├─ bin/task-engine
├─ conf/task-engine.properties
├─ conf/log4j2.properties
├─ examples/*.json
└─ lib/*.jar
```

本地启动：

```bash
export DATASCALPEL_TASK_ENGINE_TOKEN='<engine-token>'
./bin/task-engine
```

未来 YARN client 方式使用相同 Main，Driver 和 HTTP Server 仍位于提交命令所在的固定服务器：

```bash
spark-submit \
  --master yarn \
  --deploy-mode client \
  --class cn.superhuang.datascalpel.taskengine.TaskEngineDaemon \
  lib/data-scalpel-task-engine.jar
```

Daemon始终只提供编译，readiness只检查基础 SparkContext。Runner由 Task Dispatcher提交，真实执行协议与安全边界见 [Canvas 真实执行设计](canvas-task-execution.md)和[执行平台开发文档](task-execution-platform/03-task-dispatcher-foundation.md)。
