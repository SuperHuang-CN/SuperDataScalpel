# DataScalpel Task Engine 开发约定

适用于本目录，继承 [根约定](../AGENTS.md)。详细约束统一维护在 [编译与执行规范](../docs/development/task-engine.md)；修改下列能力前必须阅读对应章节。专题中的验证清单是否执行，统一遵循根文件的测试政策和本次用户要求。

| 任务 | 必读内容 |
| --- | --- |
| 新增/修改 Canvas 节点 | [Operator](../docs/development/task-engine.md#node-operators)、[编译上下文](../docs/development/task-engine.md#compilation-context)、[新增节点](../docs/development/task-engine.md#new-nodes) |
| 批流模式、流式算子或 Sink | [模式](../docs/development/task-engine.md#execution-modes)、[有界性](../docs/development/task-engine.md#streaming-schema) |
| 定义保存、导入或版本升级 | [协议版本](../docs/development/task-engine.md#protocol-versions) |
| SDK、TestKit、用户 JAR、在线开发或试运行 | [Spark JAR](../docs/development/task-engine.md#spark-jar)及 [SDK 设计](../docs/design/spark-jar-task-sdk-v1.md) |
| Runner、日志、异常、结果和终态事件 | [节点日志](../docs/development/task-engine.md#node-logging)、[错误分类](../docs/development/task-engine.md#failure-diagnostics)、[结果一致性](../docs/development/task-engine.md#execution-results) |

## 模块与契约

- 本模块负责 Canvas 编译和 Spark/JDBC 运行，不放 JPA Entity、管理端业务 Service 或管理 REST API，不依赖 Business/Admin。稳定定义和消息使用 Contracts，Spark 类型不进入共享契约。
- 批/流模式固定为 `BATCH` / `STREAMING`，创建后不可切换；`LOCAL_SQL` 不进入 Canvas 编译体系。
- Canvas 的协议大/小版本与任务 `definitionVersion` 分开。大版本不兼容、未来小版本拒绝；同大版本旧小版本可读并规范化为当前小版本。破坏性变化升大版本并将小版本归零，不隐式迁移或覆盖不兼容定义。
- 协议升级同步所有读写端、前端及文档；当前版本取 Contracts/Manifest/Result 常量，历史设计中的数字不作为现行值。

## 编译与运行共用节点

- 每个稳定 `CanvasNodeType` 恰好对应一个无状态 `CanvasNodeOperator`；Compiler/Runner 使用同一个显式内置 Registry。不增加第二套 NodeCompiler、节点执行/字段映射副本或动态扫描机制。
- Operator 统一配置校验、元数据定位、逻辑表 Map、Dataset 变换、字段映射和 Cast；环境差异通过 `CanvasNodeDataAccess` 等 I/O 端口注入。
- 预检只使用元数据快照和显式 Schema 的零行 Dataset，Output 只分析计划；不读外部 JDBC/HTTP/文件/对象存储，不创建 Writer、TRUNCATE、试写或额外扫描。
- 逻辑 Schema 用于规划与解析，不是运行时物理相等门禁。Runner 不因字段数量、顺序、类型参数、可空性或 Geometry 元数据不完全相等而提前拒绝；以实际 Reader、Analyzer、Cast、解析器或目标系统结果为准。
- 文件 Reader 使用 Manifest 的目标 Schema 和 FAILFAST，不从运行文件重定义 Schema；`schemaFingerprint` 不作相等门禁。Kafka Value Schema 属于节点内联配置，不通过模型引用获取；运行连接与凭据只来自受保护 Manifest。
- Processor 的类型可执行性由实际 Spark 表达式和 Analyzer 判断，不维护第二套平台类型矩阵。Output 共享 Operator 显式 Cast：可证明安全则通过，实际值相关风险为 WARNING，配置/业务结构错误或 Analyzer 不支持才是 ERROR。
- `ERROR` 只表示定义、逻辑快照或 Analyzer 已能确定的不可执行问题。预检不保证真实数据、权限、连接或数据库约束一定成功，不为提高预检覆盖率增加外部试读/试写。
- 不因不同任务或多个 Output 指向同一目标、或模型同时读写而增加平台占用检查、跨任务扫描或分布式锁；并发结果由 Sink 与实施配置决定。

## 模式、传播与诊断上下文

- 节点声明分类、非空 `supportedModes` 和有界性规则，能力不进入用户 JSON。Compiler/Runner 调用 Operator 前校验模式，不支持时返回 `NODE_EXECUTION_MODE_NOT_SUPPORTED`。
- 按配置与状态语义决定是否拆分批流节点：无状态同义变换共用 Operator；Watermark、窗口、状态等流式专属语义独立定义。保留批 `JOIN` 与流 `STREAM_JOIN`，不机械复制或重命名节点。
- 表 Map 继续以逻辑表名为 Key，逐表传播 `BOUNDED` / `UNBOUNDED`，按需传播事件时间和 Watermark。有状态算子缺少必要状态边界时编译 ERROR；流式 Sink 不沿用批 OVERWRITE 或笼统宣称 Exactly Once。
- 先合并安全上游并填写当前节点 `inputTables`，再决定是否执行 Operator。当前节点 ERROR 阻止其输出和后继传播，但保留可推导输入；WARNING 不阻断分析或传播。上游确实无效时返回明确问题，不只返回空列表。
- 缺失字段只产生必填/结构错误，依赖该字段的业务规则待其配置后检查。草稿资源 ID 在校验边界解析，空值和非法 UUID 分别返回稳定问题。
- 新节点同时定义图规则、Schema/有界性、安全摘要、执行阶段、失败回退码和结果消息，并接入统一包装；格式支持变化也须贯通 Compiler、Reader、Manifest 和来源节点错误归属。

## 日志、错误与执行结果

- 所有节点统一记录 `NODE_START` / `NODE_SUCCESS` / `NODE_FAILED`，任务记录对应 TASK 事件；身份、阶段、耗时、错误码和诊断 ID 使用同一上下文。
- 日志、异常、Result 和事件不含数据值、SQL 参数、凭据、签名 URL、完整 Manifest 或敏感物理路径；摘要字段白名单及脱敏细节见专题。观测不额外触发 Spark Action，行数从实际写入计划采集。
- 统一分类器遍历包装异常、cause 及 SQLException nextException。一次失败的顶层、节点、事件、管理记录共用诊断 ID；最接近失败来源处只记录一次脱敏完整堆栈，任务终态只记录摘要。堆栈不进入结果或事件。
- 结果按拓扑顺序记录已执行节点；成功节点无错误、失败节点有安全错误。顶层 `SUCCESS` 与正常 `STOPPED` 无错误，其他终态须有安全错误；先保存结果再报告相应终态，具体交付要求见专题。
- 变更错误码、类别、阶段、retryable 或结果协议时同步 Runner、Dispatcher、Admin、前端与设计文档，不自行增加兼容分支。实现与设计不一致时先确认目标语义，再同步修正。

## Spark JAR 边界

- 用户仅依赖公开 SDK，实现 `SparkBatchJob` / `SparkStreamingJob`；SDK/Spark 等平台依赖为 provided，用户其他依赖由用户 JAR 携带。平台管理根 SparkSession 和一次性父优先类加载器，不建设 JVM 沙箱。
- 所有实时 `StreamingQuery` 通过 SDK 注册，由平台分配名称/Checkpoint、停止和监控；Trigger、Output Mode、状态 Schema 由用户负责。正式运行与 Trial 的 Checkpoint 和恢复来源隔离。
- SDK 绑定逐次校验资源种类与读写权限；观测仅保存当前 Attempt 最新快照，不为指标额外触发 Dataset Action。具体资源映射、试运行拦截范围、停止流程和制品校验必须遵循 [Spark JAR 规范](../docs/development/task-engine.md#spark-jar)。
