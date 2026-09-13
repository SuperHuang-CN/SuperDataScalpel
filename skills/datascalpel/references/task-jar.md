# 在线 JAR 任务开发

先读所选[批处理](task-batch.md)或[实时](task-streaming.md)指南。首版只使用 JSON 在线单文件 Java 源码与编译接口；不上传 JAR、不下载模板/开发包、不运行 TestKit、Trial 或真实任务。

流程：**需求与资源分析 → 用户确认设计和创建范围 → 创建草稿及绑定 → 读取模板并编写源码 → 在线编译修正 → 回读并交付**。在线编译需要已存在任务，因此确认发生在创建之前，不套用 Canvas 的先预校验后创建顺序。

## 设计与本地记录

在当前工作目录 `task-development/<任务标识>/` 保存需求、元数据、设计、完整 Java 源码、诊断、确认版本与执行记录。快照记录系统、资源 UUID、采集时间、可获取的结构版本；MCP 契约指纹、任务定义版本、服务端源码摘要及已编译摘要分别保存。不保存连接凭据，不调用预览获取样本，不默认落盘业务数据。

通过 MCP 读取模型详情中的 model/fields、来源和目标数据源结构、计算引擎能力及 `TASK` 目录；必要时读取 Topic 元数据。区分模型编码与物理表位置，检查实际字段、目标主键和类型兼容性。寻找已有相近任务时只说明复用可能，默认新建，不修改已有任务或创建基础设施。

形成可审阅的版本：批流类型、name/description、目录、computeEngineId、业务粒度、输入输出、关联/转换口径、普通参数、资源绑定、写入语义、计算资源及超时。列出将创建的任务和目录、缺失依赖、编译不能验证的内容。取得用户对该版本和创建范围的确认后再操作。

## 创建与在线接口

下表用于发现能力；读取当前 `api_describe` 后再构造输入，编译属于有副作用操作，会保存源码与制品，不只检索 READ。

| 动作 | 接口与行为 |
| --- | --- |
| 创建目录/任务 | `POST /api/v1/directories`（scope=TASK）、`POST /api/v1/tasks`；批为 `SPARK_JAR`，流为 `SPARK_STREAMING_JAR` |
| 获取当前定义 | `GET /api/v1/tasks/{id}/spark-jar-definition` |
| 保存运行定义 | `POST /api/v1/tasks/{id}/actions/update-spark-jar-definition` |
| 获取默认模板或已存源码 | `GET /api/v1/tasks/{id}/spark-jar-online-source` |
| 在线编译 | `POST /api/v1/tasks/{id}/spark-jar-online-source/actions/compile`，请求 `{sourceCode: 完整源码}` |
| 回读任务 | `GET /api/v1/tasks/{id}` |

创建任务使用当前契约的 name、directoryId、type、description、computeEngineId，没有 task code。类型不能切换；每创建成功一项立即记录 UUID。获取当前模式默认模板后保留固定类名与入口。

保存 JAR 运行定义使用 `parameters`、`sparkConf`、`resourceBindings` 数组、可选 `executionResources` 及显式 `timeoutSeconds`（当前 1–86400 秒）。参数项是 name/value，值为字符串；超时需适合用户预期生命周期。Spark 配置不能覆盖平台控制项，不借配置注入凭据或任意运行依赖。资源规格遵守所选引擎上限。

资源绑定包含 bindingName、resourceType、resourceId、accessMode，Topic 类型另有 topicName。当前公开 SDK 的批处理使用 `MODEL`、`JDBC_DATA_SOURCE`；实时另有 `KAFKA_TOPIC`。以运行契约和资源验证结果确定允许的 READ/WRITE/READ_WRITE 组合，不把枚举里出现的其他资源当作已存在 SDK 访问能力。bindingName 是代码引用名，resourceId 是系统 UUID；Kafka 绑定 resourceId 指向数据源，topicName 指向 Topic。只声明批准的资源与访问范围。

## SDK 常用接口

公开包为 `cn.superhuang.datascalpel.sdk`。下面指引供独立复制后的 Skill 使用；以在线模板、当前公开 API 和编译诊断校准，不导入 Contracts、Canvas、Engine、Spring 或 JPA 的内部类型。

| 用途 | 当前公开 API |
| --- | --- |
| 批入口 | `SparkBatchJob.execute(SparkJobContext context)` |
| 流入口 | `SparkStreamingJob.start(SparkStreamingJobContext context)`；可选 onStop 生命周期回调 |
| Spark 与上下文 | `context.spark()`、`identity()`、`observability()`；平台管理根 SparkSession |
| 参数 | `context.parameters().find(name)` 返回 Optional<String>；`require(name)` 返回必需字符串；`asMap()` 读取普通参数。自行进行业务类型解析和边界校验 |
| 模型读取 | `context.models().read(bindingName)`，或传 `JdbcReadOptions` |
| 模型写入 | `context.models().write(bindingName, dataset).mode(ModelWriteMode.APPEND).map(target, source).execute()`；可用 `mapSameName()` 和 `checkSchema()` |
| JDBC 读取 | `context.jdbc().readTable(binding, table)` / `(binding, schema, table)` / 使用 `JdbcTableIdentifier`；`readQuery(binding, sql)`；可传 `JdbcReadOptions` |
| JDBC 写入 | `context.jdbc().write(binding, dataset).table(JdbcTableIdentifier).mode(JdbcWriteMode.APPEND).map(target, source).execute()`；UPSERT 用 `upsertKeyColumns(String...)` 明确目标键 |
| Kafka 读取 | 实时上下文的 `kafka().readStream(binding, KafkaStartingOffsets.EARLIEST/LATEST)`；按实际 key/value Schema 解析，不把消息 value 当成模型字段 |
| Kafka 写入 | `context.kafka().writeStream(binding, dataset)` 返回 DataStreamWriter<Row>；输入需要符合 Kafka key/value 结构 |
| 注册流查询 | `context.queries().start(logicalName, StreamingSinkType.KAFKA/JDBC/CUSTOM, spec -> ...)`；starter 返回实际 StreamingQuery |

写入方式有 APPEND/OVERWRITE/UPSERT，但需符合目标方言和模式。SDK `.map(目标列, 来源列)`；不能照搬 Canvas 映射对象的视觉顺序。模型 UPSERT 使用完整模型主键，JDBC UPSERT 使用明确目标键。实时微批不要使用 OVERWRITE。

`JdbcTableIdentifier.of(catalog, schema, table)` 或 `.schemaTable(schema, table)` 分别表达物理位置，不把带点字符串猜成已解析命名空间。`readQuery` 接收 SQL 字符串，没有命名参数绑定重载；参数先按批准类型解析，适合时在已读取 Dataset 上使用 Spark Column/lit 过滤，不把用户文本直接拼成 SQL 片段。

用户代码可以定义未来运行时写入，但本流程只编译，不调用代码里的 execute 或 Spark action 来验证。`checkSchema()` 仅在上下文支持本地 Schema 检查时校验，生产路径仍将转换与物理约束交给 Spark/目标系统；在线编译既未执行此检查，也不证明运行时写入兼容。资源绑定控制可访问资源与凭据注入范围，不是 JVM 沙箱。

## 批入口示例

前提：批准了 READ 模型绑定 `source`、WRITE 模型绑定 `target`，来源与目标都有兼容的 `id` 字段。示例演示 API 与完整固定入口，实际字段和写入语义必须替换为已批准设计。

```java
package com.example.datascalpel;

import cn.superhuang.datascalpel.sdk.ModelWriteMode;
import cn.superhuang.datascalpel.sdk.SparkBatchJob;
import cn.superhuang.datascalpel.sdk.SparkJobContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

public final class ExampleSparkJob implements SparkBatchJob {
    @Override
    public void execute(SparkJobContext context) throws Exception {
        Dataset<Row> result = context.models().read("source").select("id");
        context.models().write("target", result)
                .mode(ModelWriteMode.APPEND)
                .map("id", "id")
                .execute();
    }
}
```

## 实时入口示例

前提：批准了 Kafka READ 绑定 `source` 与 WRITE 绑定 `sink`，平台已具备 Kafka 访问与持久化 Checkpoint 配置。示例原样传递消息，不证明任意业务消息格式正确；EARLIEST 是示例初始位置，实际选择需要在设计中确定。

```java
package com.example.datascalpel;

import cn.superhuang.datascalpel.sdk.KafkaStartingOffsets;
import cn.superhuang.datascalpel.sdk.SparkStreamingJob;
import cn.superhuang.datascalpel.sdk.SparkStreamingJobContext;
import cn.superhuang.datascalpel.sdk.StreamingSinkType;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

public final class ExampleSparkStreamingJob implements SparkStreamingJob {
    @Override
    public void start(SparkStreamingJobContext context) throws Exception {
        Dataset<Row> events = context.kafka().readStream(
                "source", KafkaStartingOffsets.EARLIEST);
        context.queries().start("events", StreamingSinkType.KAFKA, spec ->
                context.kafka().writeStream("sink", events)
                        .queryName(spec.queryName())
                        .option("checkpointLocation", spec.checkpointLocation())
                        .outputMode("append")
                        .start());
    }
}
```

所有持续查询通过 SDK 注册，logicalName 在任务内稳定唯一，使用 spec.queryName()/checkpointLocation()；不绕过注册直接启动查询、不自行阻塞 awaitTermination、不停止根 SparkSession、不调用 System.exit，也不创建另一个根 SparkSession。模型/JDBC 的流式写入在已注册查询的 foreachBatch 中调用对应批写 API，处理重放语义。

## 编译、修正与交付

提交完整单文件源码；当前请求限制为 256 KiB UTF-8，服务端规范化换行为 LF。编译接口**先保存源码，成功后替换当前 JAR**；FAILED 保留旧 JAR（如有），源码草稿仍已保存。不能把 FAILED 或请求超时解释为“没有任何变更”。

检查 `status`、`durationMs`、`diagnostics` 与 `source`。只有 `SUCCEEDED` 才算编译通过；按编译位置和错误修正类型、导入、固定入口及 API 使用。若缺少依赖、Engine 或权限则明确阻塞，不能绕过系统改成自行下载 SDK、本地打包或运行任务。

不改变已批准业务设计的编译修复可自主继续；改变来源、目标、口径、参数含义或资源范围需更新版本并重新确认。每次改源码都使旧编译结果失效，最终版本必须重新编译。

回读最新 online-source、JAR definition 和任务：`sourceSha256` 与 `compiledSourceSha256` 一致，`hasUncompiledChanges=false`，`currentJar` 存在，源码已持久化，定义中的绑定、参数、计算资源与批准版本一致，任务仍为 `DRAFT`。不要仅凭制品存在认定它属于当前源码。若之后又修改设计/绑定，核对最终版本并重新完成适用编译。

创建、保存或编译响应不确定时先查询实际任务、定义、摘要与制品状态；请求可能仍在进行时暂停受影响操作，不能直接重复提交。已有成功目录/任务保留，不自动删除或覆盖。同名任务不能自行认领；用已记录 UUID 和详情核对。恢复时也要核实当前用户授权，本地确认标记不产生授权。

交付资源 UUID、可确定的页面入口、本地源码与设计位置、编译诊断摘要，注明“已编译，未运行”。运行时绑定访问、外部数据库约束、恢复与业务结果未验证；上线及结果验收由用户完成。缺少文件能力时说明资料无法按约定保存，不伪造保存或编译成功。
