# HTTP API 数据源第二阶段：分批读取设计

## 1. 目标

HTTP API 输入在分页读取期间不得把全部记录长期保存在 Runner Driver 的 Java 堆中。每一页响应中的记录按固定上限转换为小批次，批次立即交给 Spark 并物化到 Executor 的 BlockManager 磁盘；当前批次物化完成后，连接器释放对应的 Java 行对象，再继续读取下一批或下一页。

本阶段必须保持 HTTP API 第一阶段已经确定的鉴权、运行时 Token、签名、重试、同步分页、异步轮询、显式输出 Schema、执行限制和日志脱敏语义不变。

## 2. 非目标

- 不把任意脚本、消息队列、流计算框架或新的共享存储引入 Task Engine。
- 不实现 JSON Token 级流式解析。单个 HTTP 响应仍整体读取和解析，大小由现有 `maxResponseBytes` 硬限制约束。
- 不实现远端异步任务取消。Runner 停止轮询后，已经提交给远端系统的任务是否继续运行由远端系统决定。
- 不增加连接器级取消令牌或新的取消控制面。当前一次执行对应一个独立 Runner JVM、容器或集群 Application，取消仍由 Dispatcher 终止该运行单元。
- 不保证 Local Checkpoint 在 Executor 丢失后可恢复。发生 Executor 丢失或本地块丢失时，本次任务失败，后续按平台策略整次重新执行。

## 3. 当前问题

现有 `GenericHttpApiPullConnector.pull()` 在分页循环中持续向 `List<List<Object>>` 追加记录，全部页面成功后才返回 `HttpApiPullResult`。`CanvasTaskExecutor` 随后又把完整结果转换为 `List<Row>`，再一次性调用 `SparkSession.createDataFrame`。

因此 Driver 在峰值阶段可能同时保存：

1. 当前 HTTP 响应的字节数组和 JSON 树；
2. 所有历史页面的 Java 字段值；
3. 所有记录对应的 Spark `Row`；
4. `createDataFrame` 尚未截断的本地集合执行计划。

总体 Driver 堆占用随总记录数线性增长，任务正常运行期间就可能在达到 `maxRows` 前发生 OOM。终止 JVM 只能停止已经取消的任务，不能解决正常执行任务的内存增长。

## 4. 运行与集群约束

Local Docker、YARN cluster 和 Kubernetes cluster 都使用一次执行一个独立 Runner。YARN/Kubernetes 的 Driver 与 Executor 不保证共享本地文件系统，因此不能由 Driver 写临时文件后让下游 Executor 直接读取。

Spark 已经提供适合当前边界的本地物化机制：

```text
HTTP page response
  -> 最多 10,000 行的转换批次
  -> Spark createDataFrame
  -> eager localCheckpoint(DISK_ONLY)
  -> 释放本批 Java 行对象
  -> 读取下一批/下一页
  -> 所有批次成功后 balanced unionByName
  -> 下游 Processor / Output Action
```

`localCheckpoint(true, StorageLevel.DISK_ONLY())` 由 Spark 把批次物化到 Executor BlockManager 磁盘，并截断继续持有 Driver 本地集合的 lineage。它不需要配置共享 Checkpoint 目录，也不会把业务数据写入 Runner 自己管理的 Driver 临时路径。

## 5. 批次契约

Task Engine 内部增加三个类型：

- `HttpApiPullBatch`：批次序号、来源结果页序号以及本批行；内置通用连接器的结果页序号从 1 开始。
- `HttpApiPullSummary`：结果页数、批次数、总行数和所有 HTTP 响应累计字节数。
- `HttpApiPullBatchConsumer`：同步消费一个批次；方法正常返回表示该批已经安全接收。

`HttpApiPullConnector` 保留原有 `pull()`，并增加 `pullBatches()`。默认实现可把旧连接器的完整 `pull()` 结果作为一个兼容批次交出；因此旧的随应用发布连接器不会立即失效，但只有覆盖 `pullBatches()` 的连接器才具有内存有界保证。兼容批次的来源页序号为 `0`，表示它可能聚合了多个页面。

内置 `GenericHttpApiPullConnector` 必须覆盖 `pullBatches()`。原有 `pull()` 改为基于批次接口重新累计，只服务兼容调用和既有测试；Canvas Runner 不再调用完整结果入口。

## 6. Generic Connector 分批算法

内置常量 `MAX_BATCH_ROWS = 10_000`，第一版不暴露成数据源配置，避免形成缺乏依据的调优项。每个结果页按以下顺序处理：

1. 在请求前检查持续时间和线程中断状态。
2. 执行当前页请求并按现有规则校验 HTTP 状态、JSON 和累计响应字节数。
3. 逐条按显式 Schema 转换记录。加入批次前先检查 `maxRows`，禁止把超限的第 `maxRows + 1` 行交给消费者。
4. 批次达到 10,000 行时同步调用消费者；消费者返回后丢弃本批集合，创建新集合。
5. 当前页处理完成后立即交出不足 10,000 行的尾批，再推进分页状态。
6. 所有结果页完成后返回汇总。

异步接口的提交响应和状态轮询响应只更新累计响应字节数，不产生数据批次；远端任务成功后，结果请求及其分页才进入上述批次算法。

消费者抛出异常时，连接器不得请求下一页。已经物化的部分批次不会作为节点输出暴露；调用方负责清理它们，节点整体失败。

## 7. Spark 批次暂存

`HttpApiBatchDatasetStager` 只负责 HTTP API 批次到 Spark Dataset 的桥接：

1. 将一个批次的字段列表转换为 Spark `Row`。
2. 使用资源的显式 `StructType` 创建 DataFrame。
3. 立即调用 `localCheckpoint(true, DISK_ONLY)`，等待物化完成。
4. 只保存 Checkpoint 后的 Dataset 句柄，不保存 `HttpApiPullBatch`、字段列表或原始 JSON。
5. 所有批次成功后使用成对、逐层的 `unionByName` 合并，避免按批次数形成特别深的线性 Union 计划。
6. 没有记录时，使用同一个显式 `StructType` 返回零行 DataFrame。

暂存器在取出最终 Dataset 后不允许再接收批次，避免下游拿到不完整视图。批次编号必须严格从 1 连续递增，空批次视为连接器错误。

Local Checkpoint 的 Dataset 必须保留到所有下游 Output Action 完成。`CanvasTaskExecutor.executeCompiled()` 统一登记本次执行创建的暂存器，并在成功、输入失败、处理失败或输出失败后的 `finally` 中执行 `unpersist(false)`；最外层 `spark.stop()` 是进程退出前的最终清理兜底。清理失败只能记录不含业务数据的警告，不能覆盖任务的主失败。

## 8. 内存上界

内置连接器正常执行时，Driver 不再保存 O(总行数) 的 Java 对象。长期状态变为 O(批次数) 的轻量 Dataset 句柄，Driver 数据峰值主要由以下部分构成：

- 一个 HTTP 响应的字节数组和 JSON 树，受 `maxResponseBytes` 的剩余额度约束；
- 当前页中最多 10,000 行的转换批次及其短暂 Spark `Row` 表示；
- Spark 构建和物化当前批次所需的临时对象；
- 已物化批次的 Dataset 元数据。

Executor 磁盘占用仍随总结果量增长，这是用磁盘换取 Driver 堆稳定性的明确取舍。Spark 本地目录容量不足时，节点以稳定的批次暂存错误失败，不退化为重新把全部数据放回 Driver 内存。

## 9. 失败、可见性与取消

- 分页、字段转换、批次物化或 Union 准备任一步失败，`HTTP_API_INPUT` 节点失败，下游节点不得开始。
- 批次物化失败使用稳定错误码 `API_BATCH_STAGING_FAILED`，阶段为 `READ`，类别为 `RESOURCE`，对外消息为“HTTP API 分批暂存失败”。原始异常只进入既有脱敏 Runner 日志。
- 已物化的部分批次仅在完整拉取成功且最终 Dataset 构造完成后写入节点传播表；不存在部分成功输入。
- 日志、节点结果和汇总不得包含响应行、字段实际值、Token、签名或请求敏感参数。
- 当前取消由 Dispatcher 终止独立 Runner JVM/容器/Application。JVM 停止后当前连接断开，分页和轮询自然停止；连接器只在请求、轮询睡眠和批次边界响应线程中断，作为防御性行为，不增加独立取消协议。

## 10. 兼容性

- 鉴权、Token 缓存和 401/403 刷新重试保持不变。
- 每页和每次重试仍在最终 URL、Header、Body 确定后重新生成时间戳、Nonce 和签名。
- 页码、Offset/Limit、Cursor、Next URL 和异步结果分页的停止条件保持不变。
- `maxPages`、`maxRows`、`maxResponseBytes` 和 `maxDurationSeconds` 仍是整次节点拉取的硬限制。
- 输出字段顺序、类型转换、Null 语义和显式 Schema 保持不变。
- 原有 `HttpApiPullResult` 入口保留，但 Runner 生产链路禁止使用。

## 11. 验收标准

- Canvas Runner 的 HTTP API 输入不再调用 `HttpApiPullConnector.pull()`。
- Generic Connector 的每个批次不超过 10,000 行，单页超过该值时能拆分为多个批次。
- 多页结果按页逐批交出；消费者失败后不再请求后续页面。
- `HttpApiPullSummary` 的页数、批次数、行数和累计响应字节数准确。
- 每个非空批次在消费者返回前已使用 `DISK_ONLY` eager Local Checkpoint 完成物化。
- 多批次合并后的 Schema、字段顺序、Null 和数据值与原完整拉取语义一致；空结果仍保留显式 Schema。
- 任一批次、分页或转换失败时不执行下游节点，并清理此前暂存的 Dataset。
- Local、YARN、Kubernetes 模式不依赖 Driver 本地文件或新增共享文件系统。
- 原有异步、鉴权、Token、签名、重试、四类分页、执行限制和日志安全测试不得回退。
- `./mvnw -pl data-scalpel-task-engine -am test`、前端 `pnpm check` 和根目录 `./mvnw verify` 通过。
