# DataScalpel Task Engine 开发约定

## 适用范围

- 本规范适用于 `data-scalpel-task-engine` 下的全部代码，并继承根目录 `AGENTS.md` 的工程约定；发生冲突时，以更具体且不违背用户要求的本规范为准。
- Task Engine 负责 Canvas 编译和 Spark/JDBC 运行时执行。不得把 JPA 实体、管理端业务 Service 或管理端 REST API 放入本模块。
- Canvas Definition、执行命令、Runner 事件等跨模块稳定契约应与 `data-scalpel-contracts` 和现有设计文档保持一致；不得把 Spark 类型扩散到共享契约。

## 批流执行模式与节点能力

- Canvas 执行模式统一使用明确枚举表达，当前固定为 `BATCH`、`STREAMING`。`SPARK_CANVAS` 映射为 `BATCH`，`SPARK_STREAMING_CANVAS` 映射为 `STREAMING`；`LOCAL_SQL` 不进入 Canvas 节点编译体系。任务执行模式创建后不可切换。
- 每个内置节点描述必须声明 `category: INPUT | PROCESSOR | OUTPUT` 和非空的 `supportedModes: Set<CanvasExecutionMode>`。不得使用彼此独立的 `supportsBatch`、`supportsStreaming` Boolean 扩散无效组合，也不得让节点 Operator 自行隐式判断是否支持某种模式。
- `supportedModes` 属于 Task Engine 内置节点注册表能力，不属于用户配置，不得写入 Canvas Definition、Manifest 节点配置或持久化 JSON。Compiler 和 Runner 必须以同一个内置 Registry 为准，不能信任前端 Palette 已经完成过滤。
- Compiler 和 Runner 都必须在调用 Operator 前校验任务执行模式；节点不支持当前模式时使用稳定错误码 `NODE_EXECUTION_MODE_NOT_SUPPORTED`，不得依赖 Spark 在更晚阶段偶然报错。
- 现有 `JOIN` 节点和定义协议保持不变并仅支持 `BATCH`，不得为了命名对称将其重命名为 `BATCH_JOIN`。流式 Join 使用独立节点类型 `STREAM_JOIN`、独立配置类型和独立 Operator，不得在现有 Join 配置中堆叠 Watermark、时间范围、状态超时等流式可选字段。
- 只有当批流之间的用户配置和业务语义一致时，节点才可以同时支持 `BATCH`、`STREAMING`，例如 Filter、Select、Rename、Cast 和普通派生列。涉及 Watermark、事件时间、状态存储、时间窗口、流式去重、输出模式或 Checkpoint 语义时必须使用独立流式节点，例如 `STREAM_JOIN`、`WINDOW_AGGREGATE`、`STREAM_DEDUPLICATE`。
- 不得仅因为底层 Spark API 分为 `Dataset` 和 `DataStreamWriter` 就机械复制所有节点。是否拆分节点以配置契约、Schema 传播、状态语义和用户可理解性是否存在实质差异为准。
- 节点能力基线为：`JDBC_INPUT` 可支持批流，但在流任务中产生静态有界维表；`KAFKA_INPUT` 仅支持流并产生无界表；`JOIN` 仅支持批；`STREAM_JOIN` 仅支持流；无状态通用 Processor 可同时支持批流；`JDBC_OUTPUT` 只有在 Operator 明确校验流式写入限制时才可同时支持批流；`KAFKA_OUTPUT` 仅支持流。
- 同一节点类型同时支持批流时仍只能有一个 `CanvasNodeOperator`。模式差异通过明确的执行上下文和小范围策略表达；当配置契约已经明显分叉时，应新增节点类型，而不是在一个 Operator 中持续增加模式条件分支。

## 数据有界性与流式 Schema 传播

- 节点支持 `STREAMING` 只表示它可以出现在流任务中，不表示它一定产生无界数据。Task Engine 必须在表 Schema 中传播 `BOUNDED | UNBOUNDED`，并按需传播事件时间列和 Watermark；不得只依赖任务模式判断输入是否为流。
- `Map<tableName, CanvasTableSchema>` 的表名 Key 语义保持不变。数据有界性、事件时间和 Watermark 是 `CanvasTableSchema` 的运行时/编译期属性，不得改变表名冲突、字段冲突和 Rename 的既有规则。
- `JDBC_INPUT` 在批任务和流任务中均产生 `BOUNDED`；`KAFKA_INPUT` 产生 `UNBOUNDED`。无状态 Processor 继承主要输入的有界性；多输入和有状态 Processor 必须由自身 Operator 显式推导，禁止使用模糊的默认值。
- `FILE_DATASET_INPUT` 仅支持 `BATCH` 并产生 `BOUNDED`。Compiler 必须只根据元数据快照创建显式 Schema 的零行 Dataset，不得连接对象存储、读取文件或重新推断 Schema；Runner 必须调用同一个 Operator，并仅通过 `CanvasNodeDataAccess` 委托 Batch Reader。
- `STREAM_JOIN` 必须根据直接上游有界性区分语义：一个 `UNBOUNDED` 与一个 `BOUNDED` 表示 Stream-Static Join；两个 `UNBOUNDED` 表示 Stream-Stream Join；两个 `BOUNDED` 必须拒绝并提示使用批 `JOIN`。
- 在 Stream-Stream Join 尚未实现事件时间、Watermark、受限时间条件和有界状态清理之前，两个 `UNBOUNDED` 输入必须编译失败。实现后也必须由 `STREAM_JOIN` Operator 同时验证两侧 Watermark、时间范围和 Spark Analyzer 计划，不得允许无限状态增长的普通等值 Join。
- Window Aggregate、流式去重、Stream-Stream Join 等有状态 Processor 必须明确声明和验证事件时间、Watermark、状态保留边界及输出模式。缺失任何保证有界状态的必要配置时属于编译 `ERROR`，不得降级为 WARNING。
- 节点模式兼容和图数据语义是两层校验：节点全部支持 `STREAMING` 不代表整张图能够形成合法流式查询。Compiler 必须继续验证有界性组合、流式 Sink 能力、输出模式、Watermark 和状态算子约束。
- 流式 `JDBC_OUTPUT` 不得沿用批处理 `OVERWRITE` 语义。允许的写入模式、至少一次或幂等保证以及必要业务键必须由 Output Operator 显式校验和记录；不得笼统宣称任意流式 Sink 为 Exactly Once。
- Checkpoint、Trigger、查询名称和恢复策略属于任务/部署级运行配置，不得重复塞入每个节点配置。节点只声明形成执行计划所需的局部语义。

## 节点生命周期日志

- 所有 Input、Processor 和 Output 节点必须通过统一的节点执行包装记录生命周期，不得由各节点自行形成不一致的日志格式。
- 节点事件固定为 `NODE_START`、`NODE_SUCCESS`、`NODE_FAILED`；任务事件固定为 `TASK_START`、`TASK_SUCCESS`、`TASK_FAILED`。
- 节点日志必须包含 `executionId`、`runId`、`attempt`、`nodeId`、`nodeType`、`nodeName`、`phase`；成功和失败日志还必须包含耗时，失败日志必须包含错误码和诊断 ID。
- `JDBC_INPUT`、`KAFKA_INPUT`、`MODEL_INPUT` 默认归属 `READ`，包括 `JOIN`、`STREAM_JOIN` 在内的 Processor 默认归属 `PROCESS`，`JDBC_OUTPUT`、`KAFKA_OUTPUT`、`MODEL_OUTPUT` 默认归属 `WRITE`。任务准备、制品投递和分发分别使用当前协议定义的阶段，不得用节点名称代替阶段。
- 节点摘要只能记录诊断所需的安全元数据，例如数据源 UUID、模型 UUID、模型 code、模型 schemaVersion、表名、Join 类型、条件数量、写入模式和目标表；不得记录数据行、字段实际值或 SQL 参数值。
- Input 成功只表示读取计划和运行时 Schema 校验准备完成；不得为了日志或统计额外触发 Spark Action。
- 文件 Input 的安全摘要只允许记录节点 ID、文件表 UUID、稳定 table code、格式和字段数量。对象 Key、物化前缀、来源 Key、S3 凭据和执行器临时路径禁止出现在节点日志、异常消息、Result 或 Kafka 事件中。
- Output 行数应从实际写入计划的指标中采集；不得为日志单独调用 `count()` 或重复扫描数据源。

## 统一节点实现

- 每个正式 Input、Processor、Output 节点类型只能有一个无状态 `CanvasNodeOperator` 实现。预检 Compiler 与 Runner 必须通过同一个内置 `CanvasNodeOperatorRegistry` 调用同一个 Operator，不得分别增加 `*NodeCompiler`、`execute*` 或字段映射副本。
- Operator 统一负责配置规则、元数据定位、表 Map 语义、Spark Dataset 变换、字段映射和显式 Cast。预检与运行时的差异只能通过 `CanvasNodeDataAccess` 等外部 I/O 端口注入。
- 预检 I/O 必须使用元数据 Schema 创建零行 Dataset，Output 只分析计划，不得读取 JDBC/HTTP、创建 Writer、TRUNCATE 或写入。Runner I/O 才允许真实读取、运行时 Schema 漂移检查和生成延迟写入计划。
- 文件 Reader 必须以 Manifest 中的快照 Schema 为目标 Schema 并采用 FAILFAST 语义，不得根据运行文件重定义 Canvas Schema。Manifest 的文件存储配置、对象位置和解析参数属于受保护运行字段，不得回写 Canvas Definition、编译响应或前端状态。
- Kafka Value Schema 归 `KAFKA_INPUT/KAFKA_OUTPUT` 节点自身所有，Compiler 与 Runner 必须直接使用节点内联字段调用同一个 Operator，不得通过模型 ID、模型元数据快照或运行时模型查询间接取得 Schema。前端从模型导入只能是一次性字段复制，模型引用不得进入 Kafka 节点定义、编译契约或 Manifest。
- Kafka 节点内联 Schema 只允许平台稳定标量类型和明确的 STRING/DECIMAL 参数；Broker 地址、认证信息、序列化器私有配置和其他运行连接字段仍只能来自受保护 Manifest，不得混入 Value Schema 或 Canvas Definition。
- Compiler 只保证 Canvas 定义能够生成合法的 Spark 执行计划，不保证任务针对真实数据和外部系统一定执行成功。真实数据值、数据库约束、权限、连接状态和驱动差异由 Runner 在运行时判断，不得为了提高预检覆盖率在 Compiler 中增加试读、试写或外部连接测试。
- DataScalpel 是配置驱动的技术平台，不负责仲裁 Sink 目标的业务占用关系。Compiler、发布流程和 Runner 不得因为不同任务或同一任务内多个 Output 指向相同模型、数据源或物理表而拒绝定义，也不得因为同一模型同时被读取和写入而增加平台级冲突错误、分布式锁或任务间扫描；并发结果由 Sink 和实施配置决定。
- `ERROR` 只用于能够根据 Canvas 定义、元数据快照和 Spark Analyzer 确定无法执行的问题；`WARNING` 只用于 Spark 计划合法但可能受真实数据影响的问题。存在 WARNING 时编译结果仍必须有效，节点 Schema 和下游传播不得中断；不得因为预检无法证明数据值安全就将问题升级为 ERROR。
- Processor 的 Spark 可执行性以实际表达式和 Spark Analyzer 为唯一类型依据；不得按平台字段类型另写兼容矩阵，也不得据此产生类型风险警告。表名冲突、字段存在性、重复字段等配置和平台业务规则仍由 Operator 明确检查。
- Rename 必须根据原始 Schema 使用单次 `select + alias` 原子生成最终字段名，并在不修改输入 Map、上游表或 Dataset 的前提下替换逻辑表 Key；不得顺序调用 `withColumnRenamed`，也不得丢失物理 Origin 或字段元数据。
- Output 必须在共享 Operator 中对目标类型执行显式 Spark Cast。Output 类型策略只负责区分安全转换和运行时风险并生成 WARNING，转换是否受支持仍以共享 Operator 构造的 Spark Cast 和 Analyzer 结果为准；不得通过平台类型矩阵提前拒绝 Spark 能够建立的 Cast。
- 可证明安全的 Output 转换直接通过；Spark 支持但依赖实际值的转换产生 WARNING；只有配置错误、业务结构错误或 Analyzer 不支持的 Cast 才产生 ERROR。如果新增 Output 预检需要连接目标数据库、执行试写、模拟数据库约束或维护方言专属转换矩阵，应将该检查留到 Runner，不得加入 Compiler。
- 新增节点时必须新增一个 Operator 并注册到内置 Registry；Registry 完整性测试要求当前暂时禁用。若必须增加新的环境能力，应扩展 I/O 端口，不得复制节点行为。

## 编译诊断上下文保留

- Compiler 必须将“为编辑器提供诊断上下文”和“判定节点能否继续执行或向下游传播”分开处理。节点存在错误不代表其已经可以安全推导的输入上下文应被清空。
- 对拓扑序中的每个节点，Compiler 必须先合并直接上游已经成功传播的表并填写该节点的 `inputTables`，再根据当前节点已有错误决定是否调用 Operator。不得因为当前节点配置缺失、执行模式不兼容、节点度数错误或其他预置错误，在合并上游表之前直接跳过节点。
- 当前节点存在 `ERROR` 时可以跳过 Operator，不生成当前节点 `outputTables`，也不得继续向后继节点传播当前节点输出；但必须在编译响应中保留已经安全推导出的 `inputTables`，供 Inspector 展示来源表、来源字段并帮助用户修正配置。
- 只有上游节点本身无法产生有效 Schema、上游表名冲突、边端点不可靠、环路导致无法建立拓扑序等确实无法安全确定输入的情况，才允许输入上下文不完整；此时必须同时返回明确的稳定错误，不能只返回空列表。
- WARNING 不得中断 Operator 分析、`outputTables` 生成或下游 Schema 传播。ERROR 只阻止当前节点执行和当前节点输出，不得追溯清除此前节点已经形成的编译结果。
- 依赖其他配置字段的条件规则必须在前置字段已经配置后执行。空值、缺失值只产生对应的必填或结构错误；例如只有 `writeMode != null` 时，才能判断实时 `JDBC_OUTPUT` 是否违反仅支持 `APPEND` 的限制，不得用条件约束错误替代必填错误。
- 新增节点的编译测试必须至少覆盖：当前节点未配置完成仍返回上游 `inputTables`；当前节点存在预置模式错误仍返回上游 `inputTables`；依赖字段为空时不产生衍生业务错误；上游真正无效时停止传播并返回 `UPSTREAM_INVALID` 或更精确错误；WARNING 不影响 Schema 传播。

## 异常分类与日志安全

- 节点和 Runner 失败必须使用统一错误分类器。分类器应遍历 Spark 包装异常、`cause` 链以及 `SQLException#getNextException()`，不得只根据最外层异常消息判断。
- 稳定错误码、错误类别、执行阶段和 `retryable` 语义是对外诊断契约。调整映射时必须同步修改 Runner、Dispatcher、Admin、前端和设计文档；测试要求当前暂时禁用。
- 每次失败生成一个诊断 ID；顶层错误、失败节点结果、终态事件和管理端运行记录必须保持同一诊断 ID。
- 同一异常只允许在最接近失败来源的位置打印一次经过脱敏的完整异常链和调用栈；任务级终态日志只打印结构化摘要，不重复堆栈。
- 异常链和调用栈必须经过统一脱敏并限制最大长度。当前上限为 64 KiB，截断时必须保留明确标记。
- 日志和执行结果禁止包含密码、Secret、Token、Credential、Access Key、签名参数、预签名 URL、完整 JDBC Properties、Kafka 认证信息、数据行、SQL 参数值、Manifest 全文和本地敏感路径。
- 不得吞掉异常或仅打印自由文本。可预期失败必须形成结构化错误；未知失败必须安全回退为稳定错误码，并保留诊断 ID。

## 执行结果一致性

- Runner 必须生成当前唯一受支持版本的严格 `result.json`。协议字段和约束以 [Task Runner、Kafka 与制品](../docs/design/task-execution-platform/05-task-runner-kafka-and-artifacts.md) 为准，不在本规范复制完整 JSON Schema。
- 节点结果按稳定拓扑执行顺序保存。失败时保留已经完成的节点并追加失败节点；未开始节点不得写入结果。
- 成功节点不得包含错误对象；失败节点必须包含完整的安全错误对象。顶层成功结果不得包含错误，非成功终态必须包含错误。
- 日志和结果必须复用同一个节点执行上下文，避免节点身份、阶段、耗时或诊断 ID不一致。
- 结果和终态事件不得携带 Java 调用栈。调用栈只允许存在于经过脱敏的受控控制台日志中。
- 升级结果协议时必须在同一改动中更新 Runner 写入、Dispatcher 严格解析、Kafka 事件、Admin 持久化/API、前端类型与展示和设计文档；协议测试要求当前暂时禁用。除非用户明确要求，不得私自增加兼容分支。

## 新增节点类型

- 新增 Rename、Filter 等节点时，必须同时定义节点执行阶段、安全日志摘要、失败回退错误码、节点结果消息和 Schema 传播行为。
- 新节点必须声明非空 `supportedModes`、输入和输出有界性规则；有状态流式节点还必须声明事件时间、Watermark、状态边界和输出模式约束。
- 新节点是否需要拆分批流类型应遵循“配置与状态语义是否实质不同”的规则，不得为了减少节点数量把流式专属字段堆入批处理配置，也不得为了代码形式对称复制语义完全相同的无状态节点。
- 新节点必须接入统一生命周期包装和错误分类器，不得复制一套节点日志或异常处理逻辑。
- Processor 不得记录参与计算的实际字段值；只允许记录表名、字段名、条件数量和操作类型等安全元数据。
- 新节点的编译校验与运行时失败必须可区分。配置或编译错误不得伪装成 JDBC 或 Runner 内部错误。
- 文件 Input 新增或修改格式支持时，必须同时覆盖 Registry 能力校验、零行 Compiler、真实 Reader、Schema 指纹、Manifest v6 有序来源快照、来源节点错误归属和敏感路径脱敏；不得为每种格式拆分重复的 Canvas 节点。

## 测试与验证（暂时禁用）

- 当前阶段遵循根目录 `AGENTS.md` 的暂时禁用规则：除非用户在具体任务中明确要求，否则不强制执行 Registry、批流、Kafka、有状态流式节点、错误分类、日志、结果协议、JDBC 集成或其他针对性测试，不强制运行模块级测试或根目录 `./mvnw verify`。
- 本节仅暂时禁用测试和验证交付要求，不改变本文件其他章节规定的运行时行为、契约和安全约束。

## 设计依据

- Canvas Runner 语义、日志边界和管理端观察以 [Canvas 任务执行设计](../docs/design/canvas-task-execution.md) 为准。
- `result.json`、错误分类、Kafka 事件和制品安全边界以 [Task Runner、Kafka 与制品](../docs/design/task-execution-platform/05-task-runner-kafka-and-artifacts.md) 为准。
- 当实现与设计文档不一致时，应先确认目标语义，再在同一改动中同步代码和文档，不得让规范、协议和实现长期分叉；测试要求当前暂时禁用。
