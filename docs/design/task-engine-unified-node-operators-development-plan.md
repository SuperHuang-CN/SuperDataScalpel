# Task Engine 统一节点 Operator 分阶段开发计划

## 1. 背景与目标

当前 Task Engine 会在 Runner 执行前调用 `CanvasTaskCompiler`，但预检和运行仍分别实现节点行为：

- Compiler 使用 `JdbcInputNodeCompiler`、`JoinNodeCompiler`、`OutputColumnMappingCompiler` 等类。
- Runner 在 `CanvasTaskExecutor` 中通过 `executeInput`、`executeJoin`、`prepareJdbcOutput` 等方法重新解释同一份 Definition。
- 两侧虽然共享 Canvas 契约、元数据模型和部分 Spark 类型工具，但没有共享节点实现。

这种结构已经造成实际规则漂移：Output 预检按照“平台类型必须完全一致”阻止任务，而 Runner 的字段映射只做 `alias`，没有执行预检所假设的转换。

本计划的目标是：

1. Input、Processor、Output 都由同一个无状态 Node Operator 实现。
2. 预检与 Runner 只注入不同的外部 I/O 端口，不再分别实现节点业务逻辑。
3. Processor 的静态可执行性以 Spark Analyzer 为最终依据。
4. Output 使用同一套字段映射、显式 Spark Cast 和风险分级。
5. 预检不访问外部数据源、不产生写入副作用，也不为验证触发数据扫描。
6. 保持 Canvas Definition、Task Compilation HTTP 契约和 Runner Result v2 不变。

## 2. 老系统参考结论

老系统的 `CanvasNode#execute(CanvasData)` 同时被正式执行和 Processor 预运行调用，这是值得保留的核心思想。

但老系统没有统一全部节点：

- Input 预运行使用配置中的 Schema 创建空 Dataset，没有调用 Input 的真实执行实现。
- Output 预运行被直接跳过，没有执行字段映射与类型分析。
- 节点对象持有 Context、CanvasData、executed 等可变运行状态，并通过 Java 类名反序列化，不适合当前稳定协议。

新系统只借鉴“一个节点只有一份行为实现”，不恢复可变节点对象、Java 类名协议或 Admin 对 Spark 实现的直接依赖。

## 3. 目标结构

```text
Canvas Definition
        |
CanvasGraphPlan
        |
CanvasNodeOperatorRegistry
        |
CanvasNodeOperator
        |
  +-----+-------------------+
  |                         |
Preflight Context       Runtime Context
  |                         |
Schema-only Input       JDBC/HTTP Input
No-op Output Sink       JDBC Output Sink
Issue Collector         Runtime Exception
```

统一 Operator 负责：

- 节点配置和业务约束。
- 元数据定位。
- 表 Map 输入输出语义。
- Spark Dataset 变换。
- 输出字段映射和显式 Cast。
- 输出 Schema 传播。

环境端口只负责：

- Input Dataset 从空 Schema 还是外部系统产生。
- 运行时物理 Schema 漂移检查。
- Output 最终是无副作用预检还是实际 JDBC 写入。
- HTTP API 暂存资源生命周期。

## 4. 核心内部接口

### 4.1 Node Operator

```java
interface CanvasNodeOperator<N extends CanvasNodeDefinition> {
    CanvasNodeType nodeType();

    CanvasNodeOperationResult apply(
            N node,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    );
}
```

每个正式节点类型只有一个 Operator：

- `JdbcInputOperator`
- `HttpApiInputOperator`
- `ModelInputOperator`
- `JoinOperator`
- `JdbcOutputOperator`
- `ModelOutputOperator`

### 4.2 Operation Context

Context 提供 Spark Session、MetadataIndex、问题收集器和 I/O 端口。Operator 不直接判断自己运行在预检还是 Runner，不在节点内部散布 `if (preflight)`。

### 4.3 I/O 端口

Input 端口对 Operator 暴露相同返回类型：

- 预检实现依据元数据创建空 Dataset。
- Runner 实现读取 JDBC、HTTP API 或模型物理表，并检查运行时 Schema。

Output 端口接收已经完成映射和 Cast 的 Dataset：

- 预检实现不写入，只确认计划可分析。
- Runner 实现生成延迟写入对象，由现有稳定拓扑顺序统一提交。

### 4.4 Issue Sink

- 预检 Issue Sink 收集 `ERROR/WARNING`，允许继续检查其他节点。
- Runner 在正式执行前仍运行预检；进入节点执行后若共享 Operator 再发现配置错误，转换为稳定运行时错误。
- 警告不阻止发布或执行。

## 5. 类型转换规则

Output 不再使用“类型必须完全一致”的布尔判断。Output 专用转换策略分为：

- `EXACT`：类型和约束一致。
- `SAFE`：可证明无损，自动 Cast，不产生 Issue。
- `RISKY`：Spark 支持，但可能因实际值、长度、精度、时区或 null 失败，自动 Cast 并产生 WARNING。
- `UNSUPPORTED`：Spark Analyzer 无法建立转换计划，产生 ERROR。

首批规则：

- 整数扩大 `BYTE -> SHORT -> INTEGER -> LONG` 为 `SAFE`。
- `FLOAT -> DOUBLE` 为 `SAFE`。
- 数值缩窄、整数与浮点互转为 `RISKY`。
- Decimal 目标可完整容纳来源时为 `SAFE`，否则为 `RISKY`。
- 字符串目标长度已知且足够为 `SAFE`；目标更短或来源长度未知为 `RISKY`。
- nullable 来源写入 non-null 目标为 `RISKY`，使用独立警告码。
- 不同日期时间语义和字符串解析型转换为 `RISKY`。
- 最终可转换性由实际 Spark `cast` 后的 Analyzer 结果决定，不复制 Spark 的完整 Cast 矩阵。

Runner 保持 `spark.sql.ansi.enabled=true`。风险转换在真实数据溢出或解析失败时必须明确失败，不静默截断或转为 null。

Join 不再因为左右平台类型不完全相同而直接失败，也不维护类型风险分类。Operator 先建立真实 Spark Join 表达式：

- Analyzer 接受：直接通过，不产生平台类型警告。
- Analyzer 拒绝：ERROR。

## 6. 分阶段实施

### 阶段 0：基线与计划

交付：

- 当前 Compiler/Runner 重复实现清单。
- 老系统可复用经验和不可照搬部分。
- 本开发计划。

完成标准：

- 计划覆盖所有现有正式节点及预检、运行、日志和测试边界。

### 阶段 1：共享基础设施

变更：

- 新增 Node Operator、Registry、Operation Context、Issue Sink、Operation Result。
- 新增预检和运行 I/O 端口契约。
- 保留现有 Compiler/Runner 行为，通过适配器逐步迁移。

测试：

- Registry 对全部正式节点有且只有一个 Operator。
- 未知类型安全拒绝。
- 预检 Issue Sink 和 Runner Issue Sink 的 ERROR/WARNING 行为正确。

完成标准：

- 新基础设施可独立编译，尚未迁移的节点仍走旧路径。

### 阶段 2：Processor 统一

变更：

- 将 Join 的 Dataset 变换迁移到唯一 `JoinOperator`。
- Compiler 和 Runner 都调用 Registry 中的同一个实例。
- 删除 `JoinNodeCompiler` 和 `CanvasTaskExecutor#executeJoin` 的重复变换。
- 移除平台类型必须完全一致的前置拒绝，由 Spark Analyzer 判断可比较性。

测试：

- 空 Dataset 与真实 Dataset 使用同一个 Operator。
- INTEGER/LONG Join 通过。
- Spark 不支持的比较返回预检错误。
- Join 表名、字段、重复列和条件业务规则保持不变。

完成标准：

- 工程中只有一处 Join 表达式和输出 Schema 构建实现。

### 阶段 3：Input 统一

变更：

- 迁移 JDBC、HTTP API、Model Input 到统一 Operator。
- 预检 Input Port 依据 MetadataSnapshot 创建空 Dataset。
- Runner Input Port 执行真实读取和运行时 Schema 漂移检查。
- HTTP API 暂存资源继续在一次运行结束时可靠关闭。

测试：

- 同一 Operator 在预检和运行上下文产生相同表名、Origin 和预期 Schema。
- 预检不访问 JDBC/HTTP。
- Runner 仍能识别物理 Schema 漂移、禁用资源和运行连接错误。

完成标准：

- 删除三个 Input Compiler 和 Runner 中对应节点语义重复代码。

### 阶段 4：Output 与转换统一

变更：

- 迁移 JDBC、Model Output 到统一 Operator。
- 抽取唯一字段映射和 Cast 实现。
- 增加 `EXACT/SAFE/RISKY/UNSUPPORTED` 转换分类。
- 预检使用 No-op Sink；Runner 使用延迟 JDBC Sink。
- 保留 Output 写入顺序、TRUNCATE、指标采集和生命周期日志。

测试：

- INTEGER -> LONG 自动 Cast 且无警告。
- INTEGER -> SHORT 自动 Cast、预检有效且产生 WARNING。
- nullable -> non-null、字符串长度和 Decimal 风险产生独立警告。
- Spark 不支持的 Cast 产生 ERROR。
- BY_NAME、EXPLICIT、必填目标字段、重复目标映射规则回归。
- 预检绝不调用 truncate/write。
- Runner 实际写入 Dataset 的字段类型与目标 Spark Schema 一致。

完成标准：

- 工程中只有一处字段选择、alias、Cast 和目标 Schema 分析实现。

### 阶段 5：统一接入与清理

变更：

- `CanvasTaskCompiler` 和 `CanvasTaskExecutor` 都通过统一 Registry 分派节点。
- 删除已迁移的旧 NodeCompiler 和 Runner 私有节点方法。
- 保持 `CanvasGraphPlan`、拓扑传播、预检收集全部问题与 Runner fail-fast 的不同编排语义。
- 更新 Task Engine 开发约定和 Canvas 执行设计。

测试：

- Registry 完整性测试。
- 搜索确认不存在第二份 Join、Input、Output 映射实现。
- 编译响应和 Result v2 契约回归。

完成标准：

- 新增节点只需增加一个 Operator 和注册项，不再分别修改 Compiler 与 Runner 节点实现。

### 阶段 6：验证与手工验收

自动验证：

- `./mvnw -pl data-scalpel-task-engine -am test`
- `./mvnw verify`
- `pnpm check`
- `git diff --check`

手工验收：

- 本次 `sys_user` 场景预检返回有效，INTEGER -> SHORT 仅显示警告。
- 执行时实际进行显式 Cast。
- 合法值任务成功；SHORT 溢出值在 ANSI 模式下形成结构化节点失败。
- 一次 JDBC/Model Input、Join、JDBC/Model Output 成功执行。
- 预检期间没有外部读取、TRUNCATE 或写入。

## 7. 不在本次范围

- 不修改 Canvas Definition 版本。
- 不增加新的节点类型。
- 不增加自动重试或警告忽略开关。
- 不在预检阶段扫描真实数据判断最大值、null 或非法字符串。
- 不将 Spark 类型加入管理端 REST 或持久化协议。
- 不恢复老系统通过 Java 类名反序列化节点的方式。

## 8. 数据库和公开契约影响

- 不新增、删除或修改数据库表。
- 不修改 Task Compilation HTTP 字段。
- 不修改 Canvas Definition。
- 不修改 Runner Result v2。
- 新增内容均为 Task Engine 内部实现与稳定警告码。

## 9. 实施状态与验收映射

阶段 0 至阶段 6 已按本计划完成。实现后的权威结构如下：

- `CanvasNodeOperators` 只创建一份内置 Registry，并为六种正式节点各注册一个无状态 Operator。
- `CanvasTaskCompiler` 与 `CanvasTaskExecutor` 都从该 Registry 取得同一个 Operator 实例。
- `SchemaOnlyCanvasNodeDataAccess` 只从元数据创建零行 Dataset，Output 只触发 Schema 分析。
- `RuntimeCanvasNodeDataAccess` 承担真实 JDBC/HTTP/Model Input、运行时 Schema 检查和延迟 Output 计划。
- `JoinNodeOperator` 是唯一 Join 表达式和 Join 输出 Schema 实现。
- `OutputColumnMappingOperator` 是唯一 BY_NAME、EXPLICIT、alias、Cast 和目标 Schema 分析实现。
- 旧的 `*NodeCompiler`、Runner 节点私有执行副本及 Output 映射副本已经删除。

自动化验收覆盖：

- Registry 完整性、重复注册、未知类型以及共享实例。
- 预检 Issue 收集与 Runner fail-fast Issue 语义。
- 预检不启动 Spark Job，也不访问 HTTP/JDBC 或执行 Output 副作用。
- Spark 接受的 Join 类型转换不产生平台类型警告，Analyzer 拒绝的不支持比较产生错误。
- JDBC、HTTP API、Model Input 的真实运行路径、禁用 API 资源和运行时 Schema 漂移。
- BY_NAME/EXPLICIT 映射、必填目标字段、重复目标映射和额外来源字段。
- INTEGER 到 LONG/FLOAT 到 DOUBLE 的安全转换，以及 INTEGER 到 SHORT、nullable、字符串长度和 Decimal 的风险警告。
- INTEGER 到 SHORT 合法值的显式 Cast 写入，以及溢出值在 ANSI 模式下形成结构化 Output 节点失败。
- PostgreSQL/MySQL 跨库 Join、JDBC/Model Output、多个 Output 顺序、TRUNCATE、写入指标和权限失败分类。

该改动不涉及数据库迁移，也不改变 Canvas Definition、编译 HTTP 契约或 Result v2。
