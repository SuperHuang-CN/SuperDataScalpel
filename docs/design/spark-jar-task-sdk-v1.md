# Spark JAR 任务与 SDK v1 设计

## 目标与边界

`SPARK_JAR` 和 `SPARK_STREAMING_JAR` 面向 Canvas 难以表达的复杂批处理与 Structured Streaming。
用户下载对应 Maven 模板，实现 `SparkBatchJob` 或 `SparkStreamingJob`并上传小型用户 JAR；平台固定
Task Engine Runner、Spark运行时、调度、停止、日志和运行记录链路。

第一版使用 Java 21，不支持任意 `main()`、JAR版本管理、历史重放、自动重试、代码扫描或代码血缘。
实时SDK只封装 Kafka Topic，不提供 S3、HTTP、CDC、JDBC增量或Kafka JSON Schema解析。SDK资源绑定是
授权声明和凭据最小化，不是 JVM沙箱；用户被视为可信实施人员。

## 公开兼容面

唯一公开依赖是 `data-scalpel-task-sdk`：

```java
public interface SparkBatchJob {
    void execute(SparkJobContext context) throws Exception;
}

public interface SparkStreamingJob {
    void start(SparkStreamingJobContext context) throws Exception;
    default void onStop(SparkStreamingJobContext context) throws Exception {}
}
```

Context提供平台创建的 `SparkSession`、运行身份、有序原始字符串参数、模型资源和 JDBC资源；实时Context
另提供 Kafka资源和平台托管的查询注册器。
用户不得停止根 SparkSession、创建新根 Session或调用 `System.exit()`。

用户 POM 将 SDK和 `spark-sql_2.13` 声明为 `provided`；Spark、Scala、Hadoop、SDK不进入用户 JAR。
Task Engine Uber JAR只是平台 Runner制品，不是用户工程依赖。其他第三方依赖由用户使用 Shade打包。

## 用户作业可观测能力

`SparkJobContext.observability()`提供结构化事件、最新阶段、Counter、Gauge和Operation Timer。名称使用
`[A-Za-z][A-Za-z0-9._-]{0,99}`，`datascalpel.`前缀由平台保留；最多100个用户指标。Operation在当前线程
设置Spark Job Description并在关闭时恢复，支持嵌套和幂等关闭。

观测接口线程安全但只供Driver端调用，不可捕获到Executor闭包。消息最长1000字符；单个事件最多20个属性，
属性名最长100字符、属性值最长1000字符。Counter只允许非负增量，Gauge必须为有限数值，同名指标不能切换类型。
模型/JDBC写入自动维护attempts、successes、rows和duration；实时查询注册维护
`datascalpel.streaming.registered_queries`。用户事件和属性原样进入任务日志，禁止写入敏感信息。

Runner在内存中累计当前Attempt快照，最多每5秒通过严格Runner/Dispatcher事件上报一次，并在批任务终态或
实时任务停止前刷新。结构化事件直接写入现有 `console.log`，不建立日志表。模型/JDBC写入和实时查询注册自动
产生安全事件及保留指标，不为观测增加Dataset Action。实时指标不写入Checkpoint，恢复后从0开始。

TestKit提供事件、阶段和指标快照读取，以及Counter、Gauge和Timer断言；生产Runner仍是最终行为依据。用户不得
在自定义消息或属性中记录SQL、数据内容、密码、Token或其他敏感信息。

## SDK TestKit

`data-scalpel-task-sdk-testkit` 是独立的公开测试辅助模块，用户工程只以 `test` 作用域引入。它依赖公开SDK和
Spark公共API，不依赖Spring、JPA、Canvas、Manifest、Contracts、Task Engine或Testcontainers，也不会进入
Shade后的用户JAR。Batch与Streaming下载模板默认包含JUnit示例测试和Java 21运行本地Spark所需的Surefire参数。
模板的测试作用域固定Servlet API 5.0，以兼容Spark 4.1.1内嵌指标Servlet仍使用的旧接口；该依赖不进入用户JAR。

TestKit默认创建`local[2]` SparkSession，也允许使用测试已有Session。模型和JDBC输入可绑定Dataset或
`StructType + Row`；JDBC Query按首尾去空白后的SQL精确匹配并保留调用记录。模型Output声明Target，JDBC Output
声明完整目标表与目标Schema，Writer执行真实字段投影、Cast、必填字段和UPSERT Key校验后捕获行、Schema、Mode、
映射和影响行数，不连接数据库。模型Target可声明主键、允许省略字段和EXTERNAL属性，单次写入默认最多捕获10,000行。
批任务Context累计捕获写入影响行数；实时任务只在每个微批的`WriteResult`和捕获记录中保留行数，不累计任务总行数。

实时TestKit使用本地文件流模拟Kafka原始行，支持向假Topic推送消息以及EARLIEST/LATEST订阅语义；Kafka输出写入
本地文件Sink供测试读取。所有查询仍通过SDK Registry注册，TestKit生成稳定Query Name和Checkpoint，验证逻辑名、
唯一性、Active状态、零查询和绕过SDK的查询，并统一执行`processAllAvailable()`、健康检查、停止和`onStop()`。
显式Checkpoint目录会保留，复用同一目录可测试CONTINUE路径，新目录代表FRESH。

TestKit不是生产连接器模拟器，不验证Kafka认证、Broker Offset细节、数据库方言、物理约束、事务、网络故障、
Exactly Once或Dispatcher Backend。生产Runner仍是最终执行语义来源，这些场景需要单独的真实环境集成测试。

## 资源与写入语义

绑定名大小写敏感且任务内唯一，批任务支持 `MODEL/JDBC_DATA_SOURCE`，实时任务另支持 `KAFKA_TOPIC`，访问模式为
`READ/WRITE/READ_WRITE`。运行时每次访问都校验绑定类型和访问模式。

- `models().read(name)` 按模型快照读取物理表。
- `models().write(name, dataset)` 支持 APPEND、OVERWRITE、UPSERT；UPSERT使用完整模型主键，
  EXTERNAL模型禁止 OVERWRITE。
- `jdbc().readTable()` 和 `readQuery()` 需要 READ；Query只允许单条 SELECT/WITH。
- `jdbc().write()` 支持显式目标表、字段映射和 Key；PostgreSQL/MySQL支持 UPSERT。
- 字段映射统一为 `map(targetColumnName, sourceColumnName)`。
- `kafka().readStream()`返回Spark Kafka Connector标准原始列；Broker与认证参数由平台注入且不通过Map暴露。
- `kafka().writeStream()`只允许写入已绑定Topic，Dataset遵循Spark Kafka Sink的 `value/key`规则。

实时作业自行设置 Trigger、Output Mode和处理语义。模型/JDBC写入只能在用户注册查询的 `foreachBatch`
中调用，实时模式禁止 OVERWRITE和Geometry写入；流式TaskRun不累计总影响行数。

写操作立即执行，多个写操作没有跨目标事务，后续异常可能留下部分写入。平台累计 SDK写入影响行数；
没有 SDK写入为 0，任一指标未知则为 null。原生 Spark Writer不计入平台影响行数。

## 定义与制品

`task_spark_jar_definition` 保存当前 JAR元数据、有序参数、有序 Spark Conf、超时和定义版本；
`task_spark_jar_resource_binding` 保存资源声明。JAR、参数、Conf、绑定或超时变化才递增版本。

上传只验证 100 MiB限制、JAR/ZIP结构、Manifest API版本、Job Class名称和对应 class条目；不加载类、
不实例化、不执行、不扫描依赖或漏洞。发布校验当前 JAR、绑定资源和计算引擎，不连接业务表。

每次运行把当前 JAR复制到：

```text
task-runs/<runId>/attempts/<attempt>/user-job.jar
```

TaskRun保存文件名、SHA-256和大小，但不对外返回对象 Key。终态后幂等清理运行级 JAR；清理失败不改变
运行终态。覆盖当前 JAR后删除旧当前对象，不保留版本列表或旧 Run重放能力。

## Runner

Manifest v17使用互斥 `sparkJarJob/streamingSparkJarJob`；Launch使用独立 `userJar`短期下载描述。Dispatcher和Runner都
持久化/校验 JAR摘要、大小和 Spark Conf，幂等指纹包含定义版本、JAR SHA和 Spark Conf。

Runner下载 JAR后校验准确大小与 SHA-256，调用 `SparkContext.addJar()`，再用以 SDK加载器为父级的
URLClassLoader加载 Job Class。Java默认父优先保证 Spark、Scala、Hadoop、SDK和 Task Engine类不能被
用户 JAR覆盖。一个 Runner进程只执行一个用户作业，不热加载或复用 ClassLoader。

批 Job Class必须 public、实现 `SparkBatchJob`并提供 public无参构造。执行结果使用 result v6，
`taskType=SPARK_JAR` 且 `nodeResults=[]`。JAR下载、摘要、类加载、构造和用户执行失败使用独立稳定错误码；
Cause链中的真实 Spark/JDBC错误优先分类。所有日志和错误必须隐藏签名 URL、对象 Key、凭据和Manifest。
Dispatcher继续读取v2～v7结果；v6及以上允许携带 `userJobObservability`。

## 实时查询托管与Checkpoint

实时Job Class必须实现 `SparkStreamingJob`并提供public无参构造。全部查询必须通过
`context.queries().start(logicalName, sinkType, starter)`注册；逻辑名大小写敏感且唯一。平台生成稳定Query UUID、
Spark Query Name和Checkpoint位置，Starter必须使用这两个值，`start()`完成注册后返回且不得调用
`awaitTermination()`。零查询、重复或非法名称、非Active查询以及绕过SDK创建的查询都会使Application失败。

Runner监控全部注册查询并上报明确的最新Progress字段，不保存Spark原始Progress JSON。任一查询失败或意外停止
时停止其他查询；正常停止先停止查询再调用一次 `onStop()`。已有执行错误优先于清理错误。

首次启动只能使用 `FRESH`创建Checkpoint世代；后续可以 `CONTINUE`复用最近Checkpoint，也可以 `FRESH`
创建新世代。跨定义版本继续不做代码、查询或状态Schema兼容分析，由实施人员确认。历史Checkpoint不会被新启动删除。
实时任务不支持Cron、TaskRun Cancel和自动重启。

## Backend一致性

Local Docker在创建 SparkSession前应用允许的 Spark Conf；YARN/Kubernetes在 `spark-submit --conf`
追加同一快照，Runner再次应用相同配置。三种 Backend复用同一 Runner和 SDK执行实现。

立即运行、Cron、FORBID/ALLOW、超时、取消、日志、Tracking URL和任务运行记录复用现有 Spark批任务闭环。
定时 JAR运行是真实执行，不使用 LOCAL_SQL计划的模拟成功路径。

## 数据库迁移

- Admin：`docs/operations/spark-jar-admin-postgresql.sql`
- Dispatcher：`docs/operations/spark-jar-dispatcher-postgresql.sql`
- 实时Admin：`docs/operations/spark-streaming-jar-admin-postgresql.sql`
- 实时Dispatcher：`docs/operations/spark-streaming-jar-dispatcher-postgresql.sql`
- 用户作业观测：`docs/operations/spark-jar-observability-admin-postgresql.sql`

脚本只增量更新 Check Constraint、列、索引和两张 `task_spark_jar_*` 表，不重建或清空现有任务与运行数据。
