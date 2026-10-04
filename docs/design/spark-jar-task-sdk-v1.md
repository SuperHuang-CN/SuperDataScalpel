# Spark JAR 任务与 SDK v1 设计

## 批处理原子写入扩展（2026-09-29）

新增可选 `BatchWriteOptions` / `WriteCondition`，不改变既有 `execute()` 默认直接提交。
在线 Java 与上传 JAR 使用同一 SDK，不要求用户自己创建中间表。典型用法：

```java
context.models().write("output", rows)
    .mapSameName().mode(ModelWriteMode.OVERWRITE)
    .batchWrite(BatchWriteOptions.overwriteWhere(
        WriteCondition.compare("region_id", WriteCondition.Operator.EQ, 1)))
    .execute();
```

完整替换/追加/UPSERT 使用 `BatchWriteOptions.atomic()`；只有显式
`new BatchWriteOptions(condition, true)` 才允许空输入清空范围。原子写入仅支持批处理，
每次 execute 单独提交；目标条件字段必须参与映射，越界数据会拒绝整次目标提交。
首批实测支持 PostgreSQL、MySQL/InnoDB、Oracle、SQL Server、openGauss，不支持的库拒绝，不暗降级。

模型 Geometry 读取/写入使用现有 Sedona/WKB 与模型快照 EPSG，保持 XY/NULL，不自动重投影。
自由 JDBC 写 Geometry 必须 `.geometrySrid("目标空间列", epsg)`；自由 JDBC 原有普通读取接口不自动推断空间元数据。
此扩展不开放实时 Geometry。试运行只捕获结果预览、校验配置，不写真实目标或中间表；因此不能用试运行证明数据库事务可提交。

`WriteResult.affectedRows` 是确认成功处理的输入行数，非净新增/物理变更统计。
TestKit 保存 `batchWrite` / `geometrySrids` 并检查条件、空输入；它不模拟数据库事务、索引、触发器或排序规则。
参见[实施方案](batch-jdbc-write-implementation-20260929.md)及[实际验证](../verification/batch-jdbc-write-20260929.md)。

## 目标与边界

`SPARK_JAR` 和 `SPARK_STREAMING_JAR` 面向 Canvas 难以表达的复杂批处理与 Structured Streaming。
用户下载对应 Maven 模板，实现 `SparkBatchJob` 或 `SparkStreamingJob`并上传小型用户 JAR；平台固定
Task Engine Runner、Spark运行时、调度、停止、日志和运行记录链路。

第一版使用 Java 21，不支持任意 `main()`、JAR版本管理、历史重放、自动重试、JAR字节码扫描或静态代码血缘。
批处理 `SPARK_JAR` 在 SDK Writer 写入前对实际 `Dataset.queryExecution().analyzed()` 做最佳努力的运行期
Catalyst血缘分析；该分析不触发 Spark Action，失败或超限不会阻止真实写入。实时 JAR暂不接入该能力。
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

## 在线 SDK API 说明

在线编辑区的“SDK API”打开统一右侧抽屉。先展示用途和可复制的调用示例，方法重载、参数与限制按需展开；可搜索中文用途、方法名和参数说明，并按当前批处理/实时模式筛选。查看文档不依赖 Java 语义服务就绪，不修改任务，也不执行示例。

展示按用户操作组织：入口先展示“能做什么”，用途导航取自 SDK 的 `apiGroup`，能力卡片提供具体方法用途的快捷入口；选择操作后展示完整用途、示例、签名、参数和后续配置。首页操作标签只展示用途说明的首个短句，完整说明保留在详情及悬停标题中，不要求使用者先认识 Java 类。重载合并为一个操作，在详情选择调用方式；同用途的配置类型和辅助操作按需展开，所有公开类型仍可访问。新增类型、方法和用途无需增加前端白名单。搜索覆盖全部用途，不受先前导航选择限制。

方法示例优先使用方法自己的 `apiExample`；仅当类型示例包含该方法调用时，才作为明确标注“包含相关操作”的完整示例展示，不伪造方法专属示例或将签名拼成可执行代码。无对应示例时保留准确签名与参数说明，类型完整示例仍可从类型入口查看。参数类型和返回类型在 SDK 目录内可继续跳转，类型继承能力也保留入口。

方法详情以“实际调用示例”为主，示例必须包含调用对象（如 `context.spark()`），并由 SDK 注释说明入口参数和放置位置；不得把 `SparkSession spark()` 等方法定义呈现为可直接运行的代码。有示例时，“方法定义与参数（参考）”默认折叠，并明确其不是调用代码；没有对应示例时直接说明缺失，保留展开的定义与所属接口用法入口。常用上下文、模型、JDBC、任务参数及实时上下文入口提供独立方法示例；重载切换同步切换示例，所有示例纳入编译检查，不执行真实读写。

说明的唯一来源是 `data-scalpel-task-sdk/src/main/java` 的公开签名与 Javadoc。构建工具 `src/build/java/SdkApiDocGenerator.java` 使用 JDK 21 Doclet API，自动发现公开类型、方法、枚举和记录组件，不维护方法白名单。SDK Maven `compile` 阶段生成 `META-INF/datascalpel/sdk-api.json`，随 SDK JAR 和 TaskEngine 制品交付；没有新增运行时依赖或独立服务。

- Javadoc 正文：一句话说明用途；所有参数（含 record 组件）维护 `@param`，必要时增加 `@return`。
- `@apiGroup`：用途分类；嵌套类型可继承；`@apiMode` 为 `BOTH`（默认）、`BATCH` 或 `STREAMING`。
- `@apiExample`：最短可用 Java 示例；`@apiNote`：权限、前置条件或副作用；可选 `@apiOrder` 调整入口排列。
- 公共类型、方法或参数缺少必要说明会使文档生成失败，并删除旧输出，避免悄悄交付过期清单。业务意义仍需开发者在修改方法时同步维护注释，不能从方法名自动推断。

读取链路为浏览器 → Admin `GET /api/v1/spark-jar-sdk-api` → 已配置的 TaskEngine 同路径 GET → 配套 SDK 资源。Admin 要求 `task.view`；Engine 沿用 Bearer 认证。成功返回 `SdkApiDocumentation`，包括版本、内容指纹、类型、方法、参数、说明、示例与适用模式。两端使用 `Cache-Control: no-store`；抽屉打开即刷新，打开期间每 30 秒重新读取，也可手动刷新；失败明确提示并重试，不显示旧 API 内容冒充最新。

“最新”是当前 TaskEngine 已部署、可编译使用的 SDK，不是 Git 中未构建的源码。SDK 改动后正常 Maven 构建、部署配套 TaskEngine 即更新文档，无须修改前端；同一 SNAPSHOT 的变化用内容指纹区分。IDEA 开发时也需要通过根 Wrapper 执行 SDK `compile`（仅 IDEA javac 不运行 Doclet），再重启相关应用加载新增接口/类；本次新增接口需要 Admin 和 TaskEngine 各重启一次。缺少文档时 Engine 返回 503，Admin 将其及旧引擎 404 转为可操作的 502，超时为 504，错误沿用 ProblemDetail。

该说明覆盖平台公开 SDK，不展开 Spark 全量 API、TestKit 或 Engine 内部实现。示例中的资源引用名是占位值，使用前替换为任务资源的“代码引用名”；写入示例会产生真实副作用，必须先核对目标和写入方式。

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
`StructType + Row`；JDBC表还可使用`jdbcTableParquet(bindingName, table, path, expectedSchema)`读取生成的Parquet，
并严格校验Schema后注册到已有JDBC Mock。JDBC Query按首尾去空白后的SQL精确匹配并保留调用记录。模型Output声明Target，JDBC Output
声明完整目标表与目标Schema，Writer执行真实字段投影、Cast、必填字段和UPSERT Key校验后捕获行、Schema、Mode、
映射和影响行数，不连接数据库。模型Target可声明主键、允许省略字段和EXTERNAL属性，单次写入默认最多捕获10,000行。
批任务Context累计捕获写入影响行数；实时任务只在每个微批的`WriteResult`和捕获记录中保留行数，不累计任务总行数。

批处理本地开发包使用 `modelInputParquet(bindingName, path, expectedSchema)` 和
`jdbcTableParquet(bindingName, table, path, expectedSchema)` 注册模型或JDBC表输入。TestKit在创建自己的
SparkSession后读取Parquet并严格校验实际 `StructType`；开发包生成的输入Parquet和对应输入`StructType`统一将字段
标记为nullable，以匹配Spark文件源读取语义，字段名称、顺序和数据类型仍严格一致。用户也可以继续手工构造Row或Dataset，
不需要自行创建SparkSession。
带`JdbcReadOptions`的模型、JDBC表和JDBC Query读取继续返回同一Mock Dataset，并可通过
`modelReadCalls(bindingName)`、`jdbcTableReadCalls(bindingName)`和`jdbcQueryReadCalls(bindingName)`断言绑定、
表标识、规范化SQL和读取参数；原有`jdbcQueryCalls(bindingName)`保持兼容。TestKit不模拟真实JDBC连接数、
驱动Fetch或源端分片并发。

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
  EXTERNAL模型禁止 OVERWRITE。`mapSameName()`可在尚未配置手工映射时按Dataset字段名生成同名映射；
  `checkSchema()`仅由TestKit比较输入Dataset与模型Target的字段集合和Spark类型，忽略顺序、nullable和Metadata，
  不触发Action。生产Runner将其作为无操作，真实Cast、数据库约束和写入结果仍在运行时决定。
- `jdbc().readTable()` 和 `readQuery()` 需要 READ；`readTable(bindingName, table)`、
  `readTable(bindingName, schema, table)` 是 `JdbcTableIdentifier` 的便捷重载，catalog场景仍显式使用
  `JdbcTableIdentifier.of(catalog, schema, table)`。Query只允许单条 SELECT/WITH，并可通过
  `readQuery(bindingName, sql, options)`设置该次查询的安全读取参数。
- `jdbc().write()` 支持显式目标表、字段映射和 Key；PostgreSQL/MySQL支持 UPSERT。
- 字段映射统一为 `map(targetColumnName, sourceColumnName)`。
- `kafka().readStream()`返回Spark Kafka Connector标准原始列；Broker与认证参数由平台注入且不通过Map暴露。
- `kafka().writeStream()`只允许写入已绑定Topic，Dataset遵循Spark Kafka Sink的 `value/key`规则。

实时作业自行设置 Trigger、Output Mode和处理语义。模型/JDBC写入只能在用户注册查询的 `foreachBatch`
中调用，实时模式禁止 OVERWRITE和Geometry写入；流式TaskRun不累计总影响行数。

写操作立即执行，多个写操作没有跨目标事务，后续异常可能留下部分写入。平台累计 SDK写入影响行数；
没有 SDK写入为 0，任一指标未知则为 null。原生 Spark Writer不计入平台影响行数。

未设置 `batchWrite` 的 Canvas、SDK/JAR 与在线开发统一调用 Engine 的 `DirectJdbcWriter`，
共用直接写入分支、UPSERT Key 数据校验、数据库能力判断及 JDBC 执行；权限、字段映射和试运行拦截仍在各自入口。
普通列沿用 Spark JDBC，Geometry/UPSERT 沿用分区批量 PreparedStatement，每 500 行执行批次、每分区提交。
OVERWRITE 仍为 TRUNCATE 后写入，不保证整次原子性，也不支持条件覆盖；原子模式仍独立使用 `BatchJdbcWriter`。
openGauss 的直接 UPSERT 使用单行 MERGE，不继承 PostgreSQL 的 ON CONFLICT；这不改变其分区提交边界。

### JDBC 读取参数与分片

任务定义的`Spark Conf`负责全局Spark参数，例如AQE和Shuffle分区。Driver 与 Executor 的 CPU、内存和实例数由
任务定义中的“运行资源”配置，并受计算引擎单次上限约束；不得通过 Spark Conf 重复设置这些资源键。单次模型或JDBC表读取使用
`JdbcReadOptions`，不允许覆盖连接地址、驱动、账号、凭据或平台确定的表标识：

```java
var options = JdbcReadOptions.builder()
        .partitionBy("id", "1", "10000000", 16)
        .fetchSize(10_000)
        .queryTimeoutSeconds(600)
        .option("pushDownPredicate", "true")
        .build();

Dataset<Row> input = context.models().read("source_model", options);
Dataset<Row> orders = context.jdbc().readTable("erp_source", "public", "orders", options);
Dataset<Row> summary = context.jdbc().readQuery(
        "erp_source",
        "SELECT customer_id, SUM(amount) total_amount FROM orders GROUP BY customer_id",
        options);
```

分片必须同时指定列、上下边界和1～256之间的分区数。边界由Spark按实际分片列类型解析；
`lowerBound/upperBound`仅用于计算分片步长，不构成过滤条件。`fetchSize`与`queryTimeoutSeconds`分别支持0～1,000,000
和0～86,400；0保留JDBC默认行为。通用选项只允许受控的下推参数、`preferTimestampNTZ`和非空的
`sessionInitStatement`。平台不自动查询MIN/MAX推导分片边界。

`readQuery`仍先校验单条只读`SELECT/WITH`，再由平台包装为带固定别名的`dbtable`子查询；不向用户开放底层
`dbtable/query`覆盖能力。这样既能让数据库先完成过滤、聚合和Join，也能避开Spark原生`query`与
`partitionColumn`不能同时使用的限制。启用分片时，查询结果必须包含分片列；SQL可访问范围由绑定数据源的
数据库账号权限决定，不能通过SQL切换到其他资源绑定或连接。

调用`Dataset.repartition()`发生在数据进入Spark之后，不能替代JDBC源端分片；`numPartitions`也是该次读取最多的
JDBC并发连接数。

### Driver JVM 参数

批处理和实时 Spark JAR 的“高级 JVM 配置”提供 Driver JVM 参数输入。每行是一个完整参数，保存时收敛为
`spark.driver.extraJavaOptions`，并随任务定义版本和 TaskRun 的 Spark Conf 快照固化；该键不在普通 Spark Conf
编辑器重复出现。

只允许 `-Dkey=value`、`-XX:+Flag`、`-XX:-Flag`、`-XX:Name=value`、`--add-opens=...` 和
`--add-exports=...`。不支持带空格或引号的值，也不允许设置 `-Xms/-Xmx`、Java Agent、classpath、HeapDump、
OnError、`datascalpel.*` 属性或任意 Executor JVM 参数。

Local Docker 在创建容器前将平台固定参数、按 Driver 内存 75% 派生的唯一 `-Xmx`、用户参数依次合并到
`JAVA_TOOL_OPTIONS`；用户参数无法覆盖堆内存策略。YARN 和 Kubernetes 使用 `spark-submit --driver-java-options`
传递同一组参数，始终通过命令参数数组而非 Shell 拼接。

## 定义与制品

`task_spark_jar_definition` 保存当前 JAR元数据、有序参数、有序 Spark Conf、运行资源、超时和定义版本；
`task_spark_jar_resource_binding` 保存资源声明。JAR、参数、Conf、运行资源、绑定或超时变化才递增版本。

上传只验证 100 MiB限制、JAR/ZIP结构、Manifest API版本、Job Class名称和对应 class条目；不加载类、
不实例化、不执行、不扫描依赖或漏洞。发布校验当前 JAR、绑定资源和计算引擎，不连接业务表。

每次运行把当前 JAR复制到：

```text
task-runs/<runId>/attempts/<attempt>/user-job.jar
```

TaskRun保存文件名、SHA-256和大小，但不对外返回对象 Key。终态后幂等清理运行级 JAR；清理失败不改变
运行终态。覆盖当前 JAR后删除旧当前对象，不保留版本列表或旧 Run重放能力。

## 批流本地开发包

配置保存接口接受可选 `developmentConfiguration`（`samples`、`jdbcTables`），与本次资源绑定在同一事务中规范化保存。
省略保留原配置，显式空列表按既有规则规范化。保存不会读取样例、生成制品或改变运行定义版本；生成仍使用独立的异步命令。
因此选择 JDBC 表后可以保存并进入在线开发，不必先生成开发包。重新生成失败仍保留上一份可下载制品。

`SPARK_JAR`和`SPARK_STREAMING_JAR`通过同一异步接口、队列和制品存储生成当前本地开发包。页面提交前自动保存
当前定义并携带定义版本。模型资源绑定是唯一来源：READ生成输入，WRITE生成输出Target，READ_WRITE同时生成两者。
样例固定为Snappy Parquet，支持零行、指定条数、指定比例和全部数据；比例按总数向上取整。存在主键时按完整主键
升序读取，否则保留数据库返回顺序并在README和元数据中标记顺序不稳定。输入样例Parquet及测试输入Schema的字段
统一为nullable；输出模型Target和JSON元数据继续保留模型原始nullable约束。

开发包还可以声明已保存的READ/READ_WRITE `JDBC_DATA_SOURCE`绑定下的物理表。数据源编码只用于页面默认绑定名和
展示；用户作业始终以绑定名授权访问。每张表独立选择样例范围，表声明与模型样例配置保存为Spark JAR定义上的开发辅助JSON，
不影响生产任务定义版本、发布状态或运行语义，也不限制生产JAR读取已授权数据源内的其他表。生成器读取真实表元数据；
只要方言能够给出普通标量/BINARY平台类型即可生成本地Parquet和Spark Schema，因此接受`EXACT`、`NORMALIZED`以及仍有平台类型定义的`LOSSY`映射，
`UNSUPPORTED`或Geometry字段会明确失败。该规则只用于本地开发样例，不放宽受管表导入、建表和Schema校验的类型质量要求。

生成任务使用独立的`task_spark_jar_development_kit_job`持久化队列，不复用TaskRun。Worker通过PostgreSQL
`FOR UPDATE SKIP LOCKED`领取任务并维护租约与心跳；临时故障最多额外重试两次。每个模型最多1,000,000行，ZIP最多
512 MiB。任务定义使用`development_kit_config_json`保存最近提交的规范化配置，使用`current_development_kit_job_id`
指向唯一可下载的当前制品；生成记录只保留队列、重试和失败诊断。新制品成功上传后原子替换该指针，旧制品立即不可下载并进入
异步清理；生成失败不影响原当前制品。当前制品不会因时间自动过期，直到被成功替换或任务删除。生成开始和上传提交前校验任务定义、模型和数据源版本；Geometry模型明确拒绝。

接口为`GET /api/v1/tasks/{taskId}/spark-jar-development-kit`、
`POST /api/v1/tasks/{taskId}/spark-jar-development-kit/actions/generate`和
`GET /api/v1/tasks/{taskId}/spark-jar-development-kit/artifact`。查询返回保存配置、最近生成状态以及当前制品是否匹配
保存配置。任务定义变化不主动禁用已有开发包；保存配置不同于当前成功制品时，旧制品仍可下载，但页面明确提示它是上一次成功
生成的开发包。已有安装中配置或指针为空时，读取最近生成记录作为兼容回退；下一次成功生成后进入单一当前制品语义。

尚未保存定义的新建 Spark JAR 任务，开发包查询返回 HTTP 200：`definitionVersion=0`，
`configuration.samples/jdbcTables` 为空列表，`generation/artifact` 为 null。该查询不创建定义、
不生成制品；真正提交生成仍须先保存定义并携带保存后的版本。

开发包包含Maven工程、按真实绑定生成的示例作业、含完整StructType和输出Target声明的单元测试、
`datascalpel-development-kit.json`以及各输入绑定的Parquet。JDBC表示例位于
`src/test/resources/samples/{bindingName}-{table}-*.parquet`，测试通过`jdbcTableParquet`注册。元数据记录任务版本、模型Schema版本、
JDBC绑定/表标识、抽样方式、实际行数、排序稳定性和文件摘要，不包含连接配置、凭据或对象Key。Parquet、ZIP、对象存储上传和HTTP下载均使用文件或流边界。
示例作业和README保留`JdbcReadOptions`的可取消注释用法；读取参数属于用户代码，不写入任务定义或开发包生成请求。

实时开发包生成`ExampleSparkStreamingJob`及对应TestKit测试。模型和JDBC输入仍使用Parquet样例；Kafka不会读取
线上消息，而是为每个READ绑定创建独立输入`TestKafkaTopic`，为每个WRITE绑定创建独立输出`TestKafkaTopic`。
READ_WRITE必须分别使用输入和输出Topic，避免回读自身输出。生成代码中的UTF-8 key/value是假消息并明确标记可编辑；
测试使用EARLIEST读取、统一注册StreamingQuery、处理当前可用数据并断言Kafka输出。模型输出Target同样注册到
`SparkStreamingJobTestKit.Builder`，以便在线草稿放入开发包后仍可本地运行。

## 批流在线 Java 开发

在线模式的任务定义页按“在线代码 → 任务资源 → 运行设置”排列，不显示本地开发工程和本地样例列；切换到上传 JAR 模式才显示本地开发能力，切换不会清除已有样例配置。在线代码区读取源码接口，区分尚未应用、代码已应用、有修改未应用以及当前包来自上传四种状态；保存草稿和试运行成功不代表已应用。加载失败提供重试，不以缓存成功状态掩盖错误。

当前在线运行包显示入口类及最近应用时间。`online_applied_at` 仅在在线编译成功并替换任务 JAR 时记录，通过源码响应 `appliedAt`（ISO 8601，可空）返回；保存草稿、试运行、配置修改和开发方式切换不更新它，手工上传 JAR 时清空。历史数据没有准确时间时显示“—”，不使用定义更新时间推断。不在此页面展示对象路径或摘要，也不重复编辑器中的检查和试运行操作。

开发方式以可空 `authoring_mode`（`ONLINE` / `UPLOAD`）保存，属于编辑偏好，不改变运行定义版本。
首次未选择时显示开发方式选择；保存后按已选方式进入配置。低频“更换开发方式”需要确认，不删除源码和当前生效 JAR。
旧记录没有该字段时，依次按在线编译摘要、现有 JAR、已存源码推断；三者都没有时才要求选择。实际上传或编译应用同步记录对应方式。
配置页资源统一用右侧 Drawer 编辑，单一用途映射到 READ / WRITE / READ_WRITE，不再重复选择输入输出和访问方式。
JDBC 表选择使用数据源已配置的 Schema，不增加第二处 Schema 设置；它是开发辅助选择，不缩小该绑定的运行时授权范围。

新建批流 Spark JAR 任务可以直接进入在线工作台，无需先保存任务定义或生成本地开发包。
页面读取默认源码和空开发包配置；仅在用户保存源码、编译或保存定义时沿用对应的持久化流程。

两类 Spark JAR 任务共用全页在线 Java 工作台。批处理默认入口为
`com.example.datascalpel.ExampleSparkJob`，实现 `SparkBatchJob`；实时默认入口为
`com.example.datascalpel.ExampleSparkStreamingJob`，实现 `SparkStreamingJob`。包名、主类可修改，编译器从 Java 语法树确定实际入口；主类必须公开并提供公开无参构造器。在线草稿保存在
`task_spark_jar_definition.online_source_code`，保存草稿不增加生产定义版本；最后一次成功成为当前JAR的源码摘要保存在
`online_compiled_source_sha256`。上传本地JAR会清除已编译摘要但保留草稿，因此同一个任务始终只有一个当前生效JAR，同时可以在
在线编译与本地上传之间切换。

在线编辑器提供默认开启的“自动保存”开关，偏好仅保存在当前浏览器。用户停止输入 1.5 秒后，沿用草稿保存接口保存当前源码；进入页面只读取，不因加载默认模板而写入。关闭后显示“保存草稿”按钮并取消尚未发送的自动保存；已提交的请求仍会完成。自动保存不检查代码、不编译、不运行、不替换当前 JAR。

保存请求串行，响应只确认其提交时的源码快照，不回写覆盖编辑区的新输入；请求期间继续修改的内容会在完成后再保存。编译/试运行等业务操作期间不发起自动保存，应用或试运行等待已提交的草稿保存完成，避免旧请求覆盖其源码。保存成功采用状态提示，不逐次弹成功消息；失败保留编辑内容、显示失败及“重试保存”，暂停自动重试，用户重试或重新开启开关后恢复。未保存或正在保存时，保留刷新/离开提醒；不承诺关闭窗口后仍能完成异步保存。该能力不新增多人保存版本控制，现有同任务保存冲突边界不变。

接口为`GET /api/v1/tasks/{taskId}/spark-jar-online-source`、
`POST /api/v1/tasks/{taskId}/spark-jar-online-source/actions/save`和
`POST /api/v1/tasks/{taskId}/spark-jar-online-source/actions/compile`。编译操作先保存完整草稿，再在管理数据库事务外请求Task Engine；
编译成功后校验 Manifest 契约、实际入口、JAR 大小和摘要，最后锁定任务并确认源码在编译期间没有变化，再原子替换当前JAR。编译错误作为带行列
范围的正常失败结果返回；超时、Task Engine不可用或对象存储失败均不覆盖之前的可运行JAR。

`POST /api/v1/tasks/{taskId}/spark-jar-online-source/actions/check` 单独检查编辑缓冲区，沿用编译接口及诊断契约，
但不保存源码、不创建定义、不替换 JAR、不运行任务。响应 `source` 仍是已保存状态，不能用它覆盖当前编辑内容。
页面把“检查代码”和“应用到任务”分开；应用沿用原编译命令并确认替换，检查期间编辑变更时丢弃过时检查结果。
编辑器字体随前端离线交付 JetBrains Mono；每个编辑器实例独立创建 Monaco 文档，不共享同任务的未保存缓冲区。

任务配置与在线资源栏共用右侧资源抽屉。编辑器中确认绑定会保存资源及本地样例配置，保留运行参数和开发方式，不保存或改写当前源码。历史同一 JDBC 绑定的多表声明不因编辑首张表而被静默丢弃。
读取片段包含变量声明与分号；模型及 JDBC 表可插入 `show(20, false)` 便于从日志查看，Kafka 流不提供同步打印。写入片段仍需用户补全 Dataset 与映射，不自动执行输入到输出的数据搬运。

在线资源卡片主体仅展示引用名、资源和用途，不再点击插入代码；移除卡片右箭头。一级操作固定为“编辑绑定、复制引用名、代码操作”。代码菜单支持悬停、点击和键盘打开，按读写权限及资源种类提供插入读取、读取并打印前 20 行、写入模板；非 Kafka 资源的查看/收起字段也从菜单进入。只有明确选择插入操作才修改编辑器，复制只复制绑定名，打开菜单不执行代码或查询数据。字段按钮仍明确以插入 `col(...)` 为语义。
检查、应用和试运行的诊断只标注对应源码；请求期间继续编辑时不把旧诊断覆盖到新源码。实施进度与未完成验证见[本次记录](../verification/spark-jar-editor-optimization-20260925.md)。

Task Engine使用JDK 21`JavaCompiler`、受控Spark 4.1.1/SDK classpath、禁用Annotation Processor且不执行用户代码。
源码上限256 KiB、诊断最多200条、编译超时30秒、产物上限5 MiB。Monaco 通过 Admin 中继连接 Task Engine 管理的 JDT LS，提供 Java 类型补全、参数、悬浮说明、诊断、格式化及符号重命名。服务不可用时明确显示降级状态，保留静态 Spark/SDK 索引和资源提示；最终以平台编译结果为准。绑定名、模型字段及已配置 JDBC 表字段始终来自任务资源元数据。部署、隔离与资源限制见 [Java 语言服务设计](spark-jar-online-java-language-service.md)。
生成开发包时，在线草稿按实际包名和公开主类写入对应源码路径，并同步 Maven 入口、测试入口及说明；无草稿时沿用批流默认模板。编译和试运行显式携带 `BATCH/STREAMING` 模式，Task Engine 校验实际入口的接口，并在 Manifest 写入匹配的 `DataScalpel-Job-Mode`，不通过源码内容猜测模式。

### 在线源码真实数据试运行

在线工作台可以把当前草稿保存并临时编译为一次`TRIAL` TaskRun。临时JAR只属于该次运行，不替换当前生效JAR，也不增加
`definitionVersion`。运行使用任务当前保存的真实模型、JDBC凭据、计算引擎、运行资源、参数和超时。

模型和JDBC Writer继续完成字段映射、Cast、主键规则、目标数据库能力检查和Catalyst分析，但在真实写入前被预览采集器拦截。
每次调用执行`limit(101).toJSON().collectAsList()`，展示前100行并标记是否截断；批任务最多保留20次写入。试运行的
`affectedRows`为空，血缘证据只留在运行结果中，不创建正式血缘摄取任务。`sessionInitStatement`在试运行中被拒绝。

用户代码显式调用`show/count/collect`仍按真实Spark语义执行，聚合和Join可能扫描完整输入。无写入保证只覆盖平台SDK Writer；
用户自行携带连接信息产生的外部副作用不属于第一版拦截范围。

实时在线试运行同样读取任务绑定的真实模型、JDBC和Kafka输入，但平台SDK的模型/JDBC写入会采集映射后的Dataset，
Kafka SDK Writer会改为本地`foreachBatch`采集，不连接输出Topic。预览按“资源类型、绑定名、完整目标”分组，最多20个
输出目标；同一目标跨微批追加并滚动保留最近100条已捕获样例，空批次不清空，Schema变化时清空旧样例。该顺序是捕获顺序，
不表示源数据时间顺序。整体快照受4 MiB限制。
正常停止和执行失败都生成v11 `result.json`；正常停止状态为`STOPPED`，失败可返回失败前已采集的部分预览。
Runner先上传结果并报告结果可用，再报告停止或失败；强制终止可能来不及生成最新预览。

批流试运行在输出发生变化后，由Runner单线程每3秒覆盖Attempt固定对象
`task-runs/{executionRunId}/attempts/{attempt}/trial-preview.json`。页面仅在输出预览可见时按3秒读取；查询和上传不触发
额外Dataset Action。快照上传失败只会使页面数据暂时变旧，不影响试运行状态。终态优先展示`result.json`中的最终预览，
归档尚未完成时继续展示最后一份运行快照。

每次实时Trial固定创建新的`TRIAL + FRESH` Deployment，Checkpoint前缀为
`streaming-jar-trials/{taskId}/{runId}`，最长运行30分钟。Trial与正式`REAL` Deployment互斥且Checkpoint完全隔离，
不会成为正式`CONTINUE`来源。到期由Admin发起正常停止并给予60秒宽限，仍未停止时沿用Dispatcher Backend强制终止。
正常停止或失败后Runner最佳努力删除Trial Checkpoint；强杀残留由运维按`streaming-jar-trials/`前缀定期清理。

## Runner

Manifest v17使用互斥 `sparkJarJob/streamingSparkJarJob`；Launch使用独立 `userJar`短期下载描述。Dispatcher和Runner都
持久化/校验 JAR摘要、大小和 Spark Conf，幂等指纹包含定义版本、JAR SHA和 Spark Conf。

Runner下载 JAR后校验准确大小与 SHA-256，调用 `SparkContext.addJar()`，再用以 SDK加载器为父级的
URLClassLoader加载 Job Class。Java默认父优先保证 Spark、Scala、Hadoop、SDK和 Task Engine类不能被
用户 JAR覆盖。一个 Runner进程只执行一个用户作业，不热加载或复用 ClassLoader。

批 Job Class必须 public、实现 `SparkBatchJob`并提供 public无参构造。执行结果使用 result v9，
`taskType=SPARK_JAR` 且 `nodeResults=[]`。JAR下载、摘要、类加载、构造和用户执行失败使用独立稳定错误码；
Cause链中的真实 Spark/JDBC错误优先分类。所有日志和错误必须隐藏签名 URL、对象 Key、凭据和Manifest。
Dispatcher继续读取v2～v9结果；v6及以上允许携带 `userJobObservability`，v8为批处理 JAR增加可空的
`lineage`运行证据。成功批任务必须返回证据；没有 SDK 写入时明确返回 `UNAVAILABLE/NO_SDK_WRITES`。
v9增加仅供在线试运行使用的可空`trialPreview`，该载荷不会进入Dispatcher终态事件。

模型与 JDBC读取会给 Catalyst属性附加稳定资源和字段身份。`readQuery`只使用数据源身份与规范化 SQL的
SHA-256表示查询结果资产，不保存 SQL正文或 Literal。模型/JDBC Writer在字段映射和 Cast完成后分析投影
Dataset，真实写入成功后才确认对应 Flow；写入失败或任务后来失败的 Flow只作为该次运行诊断。原生 Spark
Reader/Writer、RDD截断、复杂 UDF和无法解释的计划只降低字段覆盖度，不猜测来源。

血缘契约中的 `nodeKey` 表示通用的血缘操作标识，不限定为 Canvas节点 ID。Canvas继续使用真实节点 ID；
JAR代码没有业务节点时，字段用途统一回退到当前 SDK Writer的稳定 `jar:output:{flowHash}` 写入边界。
运行期元数据标记、Catalyst分析、Flow确认或最终证据组装发生异常时只降级血缘，不得阻止或推翻真实业务
写入。Dispatcher仍校验不受信任的结果制品，但v8 Spark JAR的可选血缘片段不合规时只把该片段替换为
`UNAVAILABLE/LINEAGE_RESULT_INVALID`，核心任务结果继续按 Runner声明的终态处理。

Dispatcher仅在终态事件中传递经过校验的 `resultSha256`，完整证据不进入 Kafka。Business异步读取固定
`resultObjectKey`并核对摘要；成功运行按 `taskId + definitionVersion + jarSha256` 合并已观察到的 Flow并集，
内容变化时发布下一代不可变正式快照。失败运行只保留诊断；定义或 JAR已经变化的结果标记为过期，不更新
正式血缘。上传新 JAR或定义版本变化后，旧当前快照退休，并等待首次成功运行重新积累。
运行详情通过 `GET /api/v1/task-runs/{runId}/lineage` 查询摄取状态、覆盖度、Flow数量、安全警告及是否已
并入正式快照；接口不返回完整结果证据、SQL正文、数据内容、对象 Key或连接信息。

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

Local Docker固定使用 `local[*]`，将任务 Driver CPU/内存映射为 Docker `--cpus`、`--memory`，并以容器内存的
75%派生 Runner `-Xmx`。YARN/Kubernetes 将五项运行资源映射为对应 `spark-submit` 的 Driver/Executor 参数。
允许的 Spark Conf 在三种 Backend 中保持一致；三种 Backend复用同一 Runner和 SDK执行实现。

立即运行、Cron、FORBID/ALLOW、超时、取消、日志、Tracking URL和任务运行记录复用现有 Spark批任务闭环。
定时 JAR运行是真实执行，不使用 LOCAL_SQL计划的模拟成功路径。

## 数据库迁移

- Admin：`docs/operations/spark-jar-admin-postgresql.sql`
- Dispatcher：`docs/operations/spark-jar-dispatcher-postgresql.sql`
- 实时Admin：`docs/operations/spark-streaming-jar-admin-postgresql.sql`
- 实时Dispatcher：`docs/operations/spark-streaming-jar-dispatcher-postgresql.sql`
- 用户作业观测：`docs/operations/spark-jar-observability-admin-postgresql.sql`

脚本只增量更新 Check Constraint、列、索引和两张 `task_spark_jar_*` 表，不重建或清空现有任务与运行数据。
