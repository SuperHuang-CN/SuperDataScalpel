# 查询服务开发

## 范围与执行边界

流程：**明确需求 → 检索模型并保存字段元数据 → 设计 SQL 或 Groovy 查询服务 → 测试未保存定义 → 用户确认具体版本 → 创建完整服务草稿并核对。**

首版只开发读取、聚合和组织结果的查询服务，默认新建，不修改已有服务。确认前可保存本地资料、读取元数据，并在需求范围内自主执行只读逻辑测试；不能创建目录或服务来试验方案。确认后仅创建批准的 `DATA_SERVICE` 目录和完整 `DRAFT` 服务。Engine 启用、网关发布留给用户，不通过上线再调用公开路由完成测试。

不创建或修改来源模型、物理表、业务数据、数据源、Engine 或注册配置。发现相近服务时说明复用可能，不能自动覆盖。需要跨数据源编排或写入服务时说明超出本场景范围，不通过改用脚本绕过边界。

## 1. 明确需求并保存工作资料

沿用对话已有信息，明确查询对象、业务口径、关联条件、过滤条件、数据粒度、分页排序、预期返回结构，以及服务位置和目标 Engine。仅澄清影响设计的歧义；从模型注释推断的含义标为推测，不把同名字段当作已确认关联。

在 Agent 当前工作目录的 `service-development/` 下创建独立任务目录，避免覆盖已有任务。按进展保存：

```text
<任务标识>/
├── requirements.md         # 需求、约束和补充口径
├── sources/                # 模型及字段、相关资源元数据快照
├── design.md               # 来源选择、选型、服务信息、请求与返回契约
├── definition.sql          # SQL 方案源码；脚本方案改用 definition.groovy
├── tests/                  # 用例、预期、实际结果摘要和覆盖边界
├── approved/               # 用户确认版本的设计、源码及测试记录副本
└── execution.json          # 确认依据、计划对象、调用状态与实际 UUID
```

快照记录系统标识、资源 UUID、采集时间、可获取的 `updatedAt`/`schemaVersion` 和完整物理位置（数据源 UUID、catalog、schema、table）。与 MCP 接口契约指纹分开保存，不能用契约未变证明来源结构未变。引用前次任务快照时先核对当前系统和资源，不能直接当作实时元数据。

筛选响应后落盘，只保存必要元数据、设计和测试结论；不默认保存真实结果行、连接配置、凭据或完整调试日志。请求示例使用非敏感值，诊断摘要也需去除敏感样本。缺少本地文件能力时可继续解释需求与能力缺口，但不声称已保存，也不进入依赖执行记录的创建步骤。

## 2. 发现接口并核对来源

按 Skill 入口的 `api_search → api_describe → api_invoke` 操作，使用运行时真实工具名。以下标识只帮助定位，必填项、类型、枚举和响应结构以当前部署契约为准，不依赖工程源码或固定客户端配置。

| 工作 | `operationId` |
| --- | --- |
| 检索模型、读取完整字段 | `GET /api/v1/models`；`GET /api/v1/models/{id}` |
| 核对模型数据源 | `GET /api/v1/data-sources/{id}` |
| 按需核对物理表元数据 | `GET /api/v1/data-sources/{id}/table-metadata` |
| 查询与读取 Engine | `GET /api/v1/service-engines`；`GET /api/v1/service-engines/{id}` |
| 查询 Engine 数据源注册 | `GET /api/v1/service-engine-data-sources` |
| 检索相近服务、核对完整定义 | `GET /api/v1/data-services`；`GET /api/v1/data-services/{id}` |
| 查询目录树与目录详情 | `GET /api/v1/directories`；`GET /api/v1/directories/{id}` |
| 获取脚本变量、方法和数据库补全 | `GET /api/v1/data-services/script-completion` |
| 测试未保存 SQL | `POST /api/v1/data-services/actions/test-sql` |
| 执行未保存脚本 | `POST /api/v1/data-services/actions/execute-script-draft` |
| 确认后创建必要目录 | `POST /api/v1/directories` |
| 确认后创建完整服务草稿 | `POST /api/v1/data-services` |

### 模型与服务检索

- 按业务关键词、用户指定模型、目录或分层检索，遵循实际分页。筛选只用标量字段，例如 `warehouseLayerId`、`directoryId`、`storageDataSourceId`，不使用嵌套属性。列表没命中、权限不足或接口未开放不能证明模型不存在。
- 模型列表只是概要。对选定模型逐项读取详情中的 `model` 和 `fields`，保存字段编码、类型、可空性、主键、说明以及模型真实物理位置。模型名、编码不必等于物理表名，SQL 使用已核对的表和列标识。
- 实际查询涉及的模型必须有足够字段与业务依据。缺字段、物理表未建立或口径冲突时报告问题，不自动创建或发布模型。按需核对物理元数据，最终以真实测试检查可查询性；少量样本不能证明全量唯一性或数据质量。
- 检索已有服务避免重复开发；列表不包含完整 SQL/脚本，需要读取详情。来源选择、关联和聚合依据写入设计文档，脚本服务没有模型引用字段，不能虚构 `modelIds` 或宣称已生成正式血缘。

### Engine、数据源与目录

选择已启用且类型兼容的 Engine（当前为 `DATASCALPEL`）和数据源，查询注册时使用 `engineId`、`dataSourceId` 标量条件并核对 `status=READY`。注册未就绪时说明需处理的条件，不自动创建、同步或修改注册。注册 READY 也不等于所有实际查询已通过。

SQL 与脚本均按单执行数据源设计。涉及多个数据源时先说明无法直接组合，不擅自更换用户业务范围。选择 SQL 所需模型后仍需核对存储数据源一致性；数据源 `purposes` 是集合，不能使用当前 Search DSL 对其做集合筛选。

目标目录查询使用 `scope=DATA_SERVICE`，来源模型目录使用 `scope=MODEL`。目录接口返回树，不套用实体分页。方案中分别列出拟复用 UUID、拟创建路径及父目录，不按叶子名称跨位置匹配。

## 3. 选型并设计请求与返回

优先选择能满足需求的 SQL 服务；需要多次查询组合、条件处理或嵌套结构时使用脚本。类型创建后不可切换，测试与确认针对最终选型。

### SQL_QUERY

- 当前支持具备 SQL 服务能力的 PostgreSQL、ClickHouse 数据源。`modelIds` 至少一个、不能重复，全部模型的 `storageDataSourceId` 必须等于执行 `dataSourceId`。草稿、已发布、已停用模型均可关联，不因模型状态直接排除，也不因状态可关联就假定物理表存在。关联模型不是数据库表访问白名单，本任务仍只使用需求范围内已核对的来源。
- 编写一条只读 `SELECT` 或 `WITH ... SELECT`，使用 `:name` 标量命名参数。声明与占位符完全对应；不接受调用方传 SQL、动态表名/列名、SQL 片段或列表展开。顶层不写 `LIMIT`、`OFFSET`、`FETCH` 或锁定查询。根据需要增加稳定排序，字段使用明确列和唯一别名。
- 参数定义说明名称、平台类型、必填、适用长度/精度、小数位及业务含义；可选参数缺失或 null 绑定数据库 NULL，需要在 SQL 中明确相应条件。推导类型不稳定时显式 cast。不要把本地记录的默认值、枚举范围误当作系统已提供对应参数校验。
- SQL 服务请求为固定分页参数加 `arguments`，业务参数放入 `arguments`。`pageNo` 从 1 开始，当前 `pageSize` 默认 20、最多 100，`returnCount` 默认 false；这与 Admin 列表从 0 开始的 `page` 不同。
- 输出字段由 SQL 结果列及别名决定，实际类型从测试 `resultFields` 核对。响应固定为 `pageNo`、`pageSize`、`totalCount`、`items`，不自定义外层模板；不查询总数时 `totalCount` 为 null。BigDecimal 为字符串、日期时间为 ISO 字符串，契约中如实说明。
- 当前参数不支持 BINARY/Geometry，输出不支持 BINARY、Geometry、JSON、ARRAY 等不可映射类型。`LOSSY`、`UNSUPPORTED` 或其他映射问题必须报告，不能静默删字段或改口径；需要转换时展示语义并重新测试。

### SCRIPT_API

- 固定 Groovy、POST、`/open-api/v1/` 下静态路径和一个默认 JDBC 数据源。当前不支持 TDengine 脚本服务。源码仅实现查询和结果组织，不包含 DML、DDL、外部写请求或其他改变状态的逻辑；“草稿执行”不会自动回滚这些操作。
- 先用 `script-completion` 获取所选 `engineId`、`dataSourceId` 的变量、方法签名及数据库补全。采用当前运行时支持的参数绑定和查询 API，不凭记忆编造 `db` 方法。所需方法无法确认时说明缺口，不能声称脚本可执行。
- 在脚本中落实请求解析、必填/类型/范围检查、空结果处理、稳定返回结构和错误行为。所有查询设置与需求相符的数量边界，分页要有明确排序；查询值使用绑定参数，不直接拼接用户输入。
- 系统目前没有独立请求/响应 Schema 保存字段。将参数定义、嵌套结构、字段含义、可空性和错误约定保存在本地设计，由脚本实现；不能向服务保存请求添加虚构的 Schema 或响应模板属性。
- 完整定义必须包含请求 `examples`，当前要求 1–50 项，包含标识、名称、合法 JSON `bodyText` 及 query/header 条目列表。按实时契约保存，示例标识与名称不得重复。Examples 用于重现请求，不等于参数 Schema，也不代表每项都测试通过。
- 调试输入与保存格式不同：`execute-script-draft` 接收解析后的 `body`、query/header 映射和 `routePath`；保存服务使用 `contextPath` 及 Examples 列表。按契约转换，不将 `bodyText` 字符串直接当成对象请求体，也不把 Examples 列表传给调试接口。

设计文档区分脚本返回值、API Studio 公开响应包装和调试报告。调试响应中的日志、SQL Trace、耗时等不是业务返回结构；公开包装以当前运行时可确认的行为为准，草稿调试不能证明公开 HTTP 路由已验证。

## 4. 测试未保存定义

SQL 测试与脚本调试接口当前均声明为 `EXECUTE`。能力发现不能只筛选 `READ` 而遗漏它们；允许调用这些测试接口也不意味着可以执行任意变更。

执行前检查完整 SQL 或脚本分支符合只读范围，测试使用小规模、受限查询。不给不可控调用、写入语句或有副作用的函数加上“测试”名称后执行；无法确认只读性质时暂停该测试并说明原因。

### SQL 测试

向 `test-sql` 提交当前 `dataSourceId`、有序 `modelIds`、SQL、参数定义、测试 `arguments` 和 `previewSize`（当前 1–50）。它不要求预先保存服务，也没有 `engineId` 入参，不添加虚构参数。

先按 MCP 状态判断是否拿到完整业务响应，再读取业务 `valid`、`problems`、`resultFields`、`preview`、`elapsedMs`。HTTP 200 可返回 `valid=false`，正常用例必须同时通过执行、输出契约和业务结果核对；预期错误用例应匹配相应拒绝原因。参数和模型选择错误也可能直接返回 ProblemDetail。

当前 SQL 测试由 Admin 连接数据源执行，只测试固定第一页且不查询总数；不能宣称已经验证 Engine 连接、后续分页、count、部署和网关访问。设计这些公开能力时注明依照现有协议，保留未进行运行验证的边界。

### 脚本调试

调用 `execute-script-draft`，使用计划 Engine、数据源、路径、当前源码及测试输入。Engine 实际执行该源码，不保存或部署该草稿，也不注册公开路由；因此可以在用户确认保存前测试，但这不是无副作用沙箱。

MCP `api_invoke.data` 是业务调试报告，报告自身的 `data` 才是执行结果。检查报告的 `status`、`error`、结果及诊断，不以 HTTP 200 或存在数据字段作为唯一成功依据。工具 `RESPONDED_INCOMPLETE` 与报告 `truncated` 都需记录；当前运行时不会主动设置报告的 `truncated`，false 不能证明结果或跟踪完整。关键结果未读全时不能标记完整通过。

### 通过日志定位问题

适用于 `SCRIPT_API`。先从当前 `script-completion` 确认 `log` 和数据库方法。当前运行时支持 `log.debug(format, args...)` 以及 info/warn/error/trace，使用 SLF4J 风格的 `{}` 占位符。格式字符串不能为 null；不假设存在 `log.isDebugEnabled()` 等未声明方法。

`log.debug` 会同时调用 Engine 的日志框架，并把格式化消息写入本次执行报告；报告采集不依赖控制台 DEBUG 级别开关。优先读 `logEvents` 中 `source=SCRIPT` 的结构化记录，每项包含 timestamp、level、source、message；`logs` 是兼容字符串列表，缺少级别与来源。由数据库 API 生成的记录可能是 `source=SQL`，不要把它当作脚本主动打印的内容，也不要把两份日志重复计数。`println`/`System.out` 不作为获取调试报告日志的约定。

围绕一个具体问题加少量阶段日志：请求校验后记录参数是否存在及类型，分支处记录选择，查询前后记录阶段和返回行数，转换后记录字段名或计数。不要逐行打印结果，也不要为打印计数额外执行一次查询。以下片段假设请求校验已得到非空 Long `customerId`，模型元数据已确认 PostgreSQL 上的 `public.customer.id`，且当前补全支持 `db.find(String, Map)`：

```groovy
log.debug("stage=query-start customerIdType={}", customerId.getClass().getSimpleName())
def rows = db.find(
    'SELECT id FROM public.customer WHERE id = #{customerId} ORDER BY id LIMIT 20',
    [customerId: customerId]
)
log.debug("stage=query-done rowCount={}", rows.size())

def result = [items: rows]
log.debug("stage=response itemCount={}", result.items.size())
return result
```

示例使用当前脚本数据库 API 的 `#{customerId}` 绑定，表和列由已核对的元数据固定，真实业务应替换为已设计的查询；不要把 Groovy `${...}` 字符串插值当作 SQL 参数绑定。示例只说明日志插入位置，本身不是已测试证据。

通过同一个 `execute-script-draft` 重现问题，按本次 `executionId` 和源码版本核对证据：

| 证据 | 如何判断与排查 |
| --- | --- |
| `status`、`error` | 当前成功为 `SUCCESS`，失败为 `FAILED`；先看 error.code/category/message/hint，再结合 source.line/column 和可用的 technicalMessage 定位。语法或请求阶段失败可能没有阶段日志，缺日志本身不能确定失败位置 |
| `logEvents` | 按阶段与发生顺序缩小范围。例如已有 query-start、没有 query-done，再结合 SQL Trace 区分数据库失败与后续结果处理错误；日志只辅助解释，不能单独证明结果正确 |
| `sqlTraces` | 核对 operation、sqlTemplate、parameters 的 type/valuePreview/masked、durationMs、rowCount、status/error。参数预览可能已掩码或裁剪，不能把掩码值当作真实绑定值；通常无需重复打印 SQL 或参数 |
| `transaction` | 结合 enabled/status 解释执行路径。COMMITTED 不表示业务结果正确，ROLLED_BACK 也不保证任意外部副作用被撤销；本场景仍只允许只读逻辑 |
| 完整性 | 当前 SQL Trace 最多保留 200 条，sqlCount 是已保留条数，不能据此断言实际只执行了这些语句；SQL 预览本身也可能裁剪。日志过多导致 MCP 响应无法完整读取时，先减少日志和查询范围，不直接重发相同大响应请求 |

`log.error(...)` 只写一条 ERROR 日志，不会自动让报告变成 FAILED；反过来，SUCCESS 也不能代替用例结果核对。不要捕获异常后仅打印错误并返回空集合或“成功”，这样会掩盖失败；没有业务恢复逻辑时让异常继续抛出，由运行时返回结构化错误。

当前脚本日志正文按格式化文本直接收集，**不保证自动脱敏，也没有日志条数上限**；它还可能进入 Engine 日志。不要打印 body/header 全量、令牌、凭据、原始业务记录或异常完整对象。SQL Trace 的安全预览机制不能套用到 `log.debug`。本地只保存排查结论和必要的非敏感日志摘要，不保存整份调试报告。

定位后删掉无用的临时日志，必要的少量阶段日志可保留。对最终源码重跑受影响用例，再提交用户确认；不要拿带日志的旧版本测试结果证明清理后的版本已通过。日志排查不扩展到服务启用、网关发布，也不改变 Canvas/JAR 任务只预校验或编译的边界。

### 用例与结果记录

按实际需求组织正常结果、无匹配结果、必填缺失、类型错误、可选条件、边界输入，以及关联去重/聚合口径用例。没有某类参数时不制造无关用例。空结果可验证结构和空值行为，但不能证明真实聚合结果正确；缺少可核对数据时记录覆盖缺口，不写入测试数据来填补。

每条记录包含定义版本或内容摘要、来源快照版本、测试输入、预期、实际状态和结论；结果摘要与源码同步更新。修复后只重跑受影响用例；设计、源码或参数变化后不能沿用旧版本测试通过结论。测试中断或结果不明不算通过，也不连续重复提交长查询。

必须实际调用测试接口并取得足够结果，才能报告该用例通过；静态检查、生成的示例或模拟结果不能替代真实测试。缺少模型字段、数据、权限、接口或 Engine 时保留设计并列出未验证项，停止受影响的保存流程，不通过启用服务补齐测试。

## 5. 展示成品并获取确认

测试完成后展示具体版本，提供完整资料位置和以下可审阅内容：

| 内容 | 确认依据 |
| --- | --- |
| 用途与设计 | 业务目标、SQL/脚本选型理由、来源模型、字段依据、关联及聚合口径 |
| 基础信息 | 名称、编码、类型、Engine、数据源、目录和计划 `contextPath`；路径是计划值，尚不可公开调用 |
| 接口契约 | 请求参数及约束、完整返回结构、源码和非敏感调用示例；明确系统契约与脚本自行实现的部分 |
| 测试证据 | 正常与异常用例结果、定义版本、实际覆盖范围、运行限制和未验证部分 |
| 保存清单 | 拟复用/创建目录、将创建的完整服务草稿，保持未启用、未发布 |

用户确认具体版本后保存确认依据及对应设计、源码、Examples 和测试记录副本。本地确认标记不能自行代表用户授权，也不能把初始“开发服务”的请求当作对未展示成品的确认。用户要求修改时更新版本，补测受影响内容后再确认；同一已确认版本的目录和服务创建无须逐项重复询问。

## 6. 确认后保存与核对

1. 重新读取必要契约和来源结构，检查 Engine/数据源启用状态、READY 注册、目录、全局服务编码和同 Engine 路径冲突。编码和路径按当前规范化规则比较；当前路径为 `/open-api/v1/` 下小写静态路径。可按 `code` 或 `engineId` 与 `contextPath` 标量条件查询服务；查询不可用不能当作不存在。契约格式调整不改变设计时按最新格式提交，来源或定义变化影响设计时补测并重新确认。
2. 创建前写入 `execution.json`：系统、确认版本及依据、计划对象、预期编码/路径、父对象和调用状态。按 `DATA_SERVICE + parentId + name` 复用或逐层创建批准的必要目录，每次立即记录 UUID。目录冲突先重新读取，不能跨位置复用同名目录。
3. 通过 `POST /api/v1/data-services` 一次提交基础信息与完整定义：SQL 仅带对应 `sqlDefinition`，脚本仅带对应 `scriptDefinition`，不同时提交其他类型定义。模型关联 UUID 仅进入 SQL 定义支持的 `modelIds`；完整脚本定义包含 Examples。测试值、测试报告和本地契约文档不作为额外保存属性。
4. 创建成功立即记录返回的服务 `id`，再读取服务详情核对代码、定义、参数或 Examples、目录、Engine、数据源、路径和 `definitionConfigured=true`。确认状态为 `DRAFT`，没有 Engine 部署和网关绑定。服务详情直接返回服务属性，不套用模型详情的 `model/fields` 结构。
5. 发现定义不符或服务已被其他人更改时如实记录并暂停，不自行更新修补、不停用别人的部署。执行记录保存失败时保留已返回 UUID，停止后续写操作，避免失去追踪。

不调用启用、发布、更新、删除或基础设施变更接口。保存完成只表示定义已落盘；测试通过也不保证之后上线时外部环境仍相同。

## 7. 恢复与交付

创建返回 `UNKNOWN`、`RESPONDED_INCOMPLETE` 或响应丢失时，记录待核实；优先按 UUID 查询，否则按服务编码和 Engine/路径或目录位置定位，再对照实际定义及可用证据判断。同名、查询无结果或旧本地标记都不能单独证明本次结果，无法确认时暂停受影响项，不直接重复提交。

中断恢复先读取确认依据和执行记录，再查询现状；复用本次已成功资源，保留部分成功，不自动删除回滚或覆盖冲突对象。定义或测试依据过期时重新核对，不能从恢复流程跳过测试与确认。

交付目录与服务的名称、编码、UUID、实际状态、本地设计/源码/测试报告/执行记录位置，分清已完成、失败、未执行和待核实项。仅在知道前端地址及真实路由时提供管理页面入口，不从 MCP 地址推导网关 URL，不将计划调用示例标为已上线接口。

告知用户可在系统中审阅草稿，后续由用户自行启用到 Engine、发布到网关。本场景至完整未上线服务交付结束。
