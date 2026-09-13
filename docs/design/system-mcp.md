# 系统 MCP

状态：首版实现。由 Admin 托管，业务实现位于 `business.systemmcp`，不增加进程或 Maven 模块。与在线 MCP 平台的 Groovy Tool、发布版本、服务和令牌完全独立。配套操作流程由仓库中的统一 DataScalpel Skill 提供；不集成 DHS 运行时、不改造助手交互。

## 使用入口

“系统管理 → 系统 MCP”提供接口开放、访问令牌和操作记录三个页签。接口开放页左侧按 OpenAPI Tag 展示接口分类，选择分类后只查询该分类的接口；“全部接口”恢复完整目录。首次启动后总开关关闭，全部接口未开放。

1. 确认目录状态为“已就绪”，查看接口详情及不支持原因。
2. 勾选需要开放的接口，修改总开关并保存。翻页、筛选和同步目录保留尚未保存的变更；保存只提交实际变更的接口 ID，整批成功或失败。
3. 超级管理员创建绑定系统用户的专用令牌。完整值仅在创建、轮换成功时显示一次，请及时保存。可选过期时间，留空表示不过期。
4. 标准 MCP 客户端连接 `POST {公开应用地址}/system-mcp`，使用 `Authorization: Bearer dssmcp_...`。客户端需使用 Streamable HTTP 和 JSON 请求/响应。

支持项目 MCP SDK 的协议版本：`2024-11-05`、`2025-03-26`、`2025-06-18`。服务无状态，不分配会话；通知返回 202，不提供 SSE、持续流、MCP Tasks 或文件传输。

## 配套 Skill

[DataScalpel Skill](../../skills/datascalpel/SKILL.md) 是面向第三方 Agent 的统一操作入口，通用规则放在 `SKILL.md`，专项流程按需读取 `references/`。将整个 `skills/datascalpel/` 目录复制到客户端支持的 Skill 位置，并为 Agent 连接上述系统 MCP、提供本地文件读写能力即可使用；不依赖工程源码、固定客户端工具前缀或其他 Skill。本仓库不提供安装器，也不自动安装到本机 Agent。

当前提供以下完整场景：

- [源数据分析与建模](../../skills/datascalpel/references/modeling.md)：从 JDBC 数据源或指定分层、目录、模型集合获取元数据，保存在 Agent 当前工作目录的 `modeling/` 下，结合业务目标形成目录和完整字段方案。用户确认具体版本后，仅创建必要的 `MODEL` 目录和 `MANAGED` 模型草稿，再读取结果核对；不执行物理建表、发布或加工任务。
- [查询服务开发](../../skills/datascalpel/references/service-development.md)：根据需求检索模型并保存字段元数据，在 `service-development/` 下维护 SQL 或 Groovy 设计、接口契约和测试记录。使用现有接口测试未保存定义，用户确认具体版本后创建必要的 `DATA_SERVICE` 目录和完整服务草稿；首版仅支持查询逻辑，Engine 启用和网关发布由用户完成。
- [批处理](../../skills/datascalpel/references/task-batch.md)与[实时任务](../../skills/datascalpel/references/task-streaming.md)：选择一份模式指南和 [Canvas](../../skills/datascalpel/references/task-canvas.md)/[在线 JAR](../../skills/datascalpel/references/task-jar.md)开发指南，在 `task-development/` 下保存设计、结构快照和诊断。Canvas 先对完整定义与独立元数据快照做 Engine 预校验，确认后创建任务并保存；JAR 先确认设计与草稿创建范围，再创建任务、配置资源绑定、取得模板并在线编译。只交付 `DRAFT`，不试运行、预览样本、测试结果、配置调度或上线。Canvas 的 [55 类节点手册](../../skills/datascalpel/references/canvas-nodes/index.md)按类型索引，按需读取。

SQL 测试在 Admin 侧执行，脚本草稿在所选 Engine 实际执行；两类测试都不能等同于上线验证。脚本测试不会自动提供只读沙箱，Agent 必须遵守该场景的只读逻辑边界；SQL 固定分页响应和脚本 Examples 等契约差异见服务开发参考文档。

任务检查与服务测试的边界独立：Canvas 预校验只分析定义和传入快照，JAR 在线编译会先保存源码、成功后替换当前制品，均不证明真实作业运行或结果正确。JAR 通过 JSON 源码接口开发；文件输入/输出节点仅引用现有资源或声明未来运行配置，不改变系统 MCP 不支持文件传输的范围。

管理员需按所用场景的参考文档开放实际需要的接口，绑定用户也需具有相应业务权限；服务测试接口为 `EXECUTE`，不应仅按 `READ` 筛选开放。Skill 不能增加 MCP 可见范围或绕过授权；接口不可见时 Agent 应说明能力缺口。运行时契约始终通过 `api_describe` 获取，Skill 中的接口指南不替代实时契约。其他专项场景后续在同一 Skill 内补充参考文档，当前不承诺已提供完整流程。

## 三个固定工具

| 工具 | 参数 | 作用 |
| --- | --- | --- |
| `api_search` | `query`，可选 `module`、`effect`、`offset`、`limit` | 在已开放且当前用户有权访问的接口中进行确定性关键词检索；默认 10 项，最多 50 项 |
| `api_describe` | `operationIds`，1–5 项 | 返回参数、响应、局部组件、操作性质、前置条件、关联接口、最新契约指纹 |
| `api_invoke` | `operationId`，可选 `pathParams`、`queryParams`、`body` | 经原业务 API 执行，保留 MVC 参数绑定、Bean Validation、方法权限和 ProblemDetail |

`operationId` 固定为 HTTP 方法、空格和 MVC 路由模板，例如 `GET /api/v1/data-sources/{id}`，仅作为目录标识；服务端不会将客户端标识直接作为请求地址。`effect` 为 `READ`（查询）、`WRITE`（变更）、`EXECUTE`（执行）。搜索匹配名称、模块、关键词、说明及路径，支持中文片段和英文标识符。

示例工具参数：

```json
{"operationId":"GET /api/v1/data-sources/{id}","pathParams":{"id":"实际的数据源 UUID"}}
```

接口详情是当前契约的唯一依据。路径、查询参数使用 Schema 中的类型，JSON 请求体可以是对象、数组、标量或契约允许的 null。查询支持 form 样式标量/标量数组，保留 Search DSL、分页、排序。其他参数编码形式在目录中标记需要适配。

## 接口目录维护

应用就绪后从真实 Spring MVC 路由及本机 Springdoc `/v3/api-docs` 构建目录。只扫描 `/api/v1/**` 的 GET / POST，排除认证、内部路径及系统 MCP 管理接口。接口目录的业务模块直接取 Resource 类的 OpenAPI `@Tag(name)`；管理页面和 Swagger 文档共享该分类来源，不另建分类配置。候选接口采用真实 Handler 的授权表达式，使用 Spring Security 求值，不拆解权限字符串。

每个业务方法通过 `@SystemMcpOperation` 声明操作性质、中文用途以及必要的关键词、前置条件和关联接口。声明通过 OpenAPI `x-system-mcp` 扩展输出。参数和 Schema 继续来源于业务 DTO / Validation，禁止在系统 MCP 手工复制业务请求契约。未来缺少声明的接口为“待完善”，不能开放。

新增或修改接口还必须遵循 [OpenAPI 契约](backend-api-response-and-error-handling.md#openapi-契约)。目录以最终生成的接口、参数和可达 Schema 说明为准；单有源码注解但在多态或引用转换中丢失的说明仍视为缺失，该接口不得开放。

同方法/路径有多个 Handler，或路由依赖额外 params/headers 条件时，标记“不支持，需要适配”。依赖参数的权限表达式也需适配。文件、Multipart、二进制、图片、纯文本和持续流接口第一版不能开放；JSON 形式的文件数据集元数据查询仍可使用。

Schema 只保留可达的本地 components 引用，保留递归、多态与可空定义；禁止远程引用和远程获取。请求头及 Cookie 参数不开放给调用者。

Schema 规范化及循环检查只遍历 Schema 节点，不把 `properties` 等字段映射当成 Schema，也不改写示例或默认值。业务字段可命名为 `nullable`、`format`、`example` 等关键字；例如表元数据中的 `nullable` 是列是否允许 NULL 的布尔字段，不应因此被标记为不支持。

多态 DTO 的子类型来源是 Jackson `JsonSubTypes`；父类型不重复声明指向自身子类型的 `Schema.oneOf`，避免与 Springdoc 生成的子类型 `allOf` 形成循环。联合类型由 Springdoc 在引用位置生成，具体分支的判别字段枚举依据 Jackson 注解补齐，使标准 JSON Schema 校验可区分分支。目录编译拒绝通过 `$ref`/组合关键字在同一输入值上循环的契约；进入子字段或数组元素的正常递归仍保留。

目录同步先完整解析，再在一个事务中更新投影：新增关闭；仍支持的契约变更更新指纹并保留开放；删除或不再支持的接口关闭，重新出现不自动恢复开放。同步失败显示 ERROR，不以部分目录覆盖旧记录。当前部署未就绪时 MCP 不可调用，业务应用继续运行。

## 身份与执行边界

独立表为 `ds_system_mcp_setting`、`ds_system_mcp_api`、`ds_system_mcp_access_token`、`ds_system_mcp_audit`，遵循 UUID、BaseEntity、标量引用和 PostgreSQL text 规范，通过现有 `ddl-auto=update` 建表。

数据库只保存令牌 SHA-256 摘要；随机秘密为 32 字节。令牌绑定用户 UUID，每次请求重新读取用户启用状态和当前角色权限，不复用 JWT 权限快照。停用、删除、轮换和过期立即影响后续认证。最近使用时间单独更新，避免并发认证覆盖令牌停用或轮换。

系统令牌不能访问系统 MCP 管理接口。它直接访问业务 URL 时，也会在真实 Handler 执行前检查总开关、目录状态和接口开放状态。普通 JWT 与在线 MCP 平台认证链保持独立。

系统 MCP 认证成功后，将 SecurityContext 保存在当前 Servlet 请求属性中，供 DeferredResult 的异步再分派恢复身份。此存储只覆盖同一次 HTTP 请求，不创建 Session、不跨请求缓存认证；后续 HTTP 请求仍重新校验令牌与用户状态。不能仅设置线程内的 SecurityContext，否则异步响应可能被认证链覆盖为 401。

执行目标只取当前监听端口、应用上下文和固定 `127.0.0.1`；公开连接地址、Host、代理头均不能改变执行目标。禁止任意 URL、自定义认证头、Cookie、自动重定向和自动重试。路径参数拒绝目录跳转及分隔符，避免编码后的路径改变目标 Handler。

外层请求使用 Servlet 异步响应及有界执行器，无等待队列；实际业务调用包含完整响应读取期限及大小上限。业务接口的返回格式不变，以下内容仅是工具元信息：

工作线程发生 `Error` 时先完成异步错误响应，再将错误继续抛出，避免线程终止后 HTTP 请求一直等到超时。此类基础设施错误使用通用安全错误响应，不表示业务操作一定未执行；调用方仍需核对业务状态。

| executionStatus | 含义 |
| --- | --- |
| `NOT_DISPATCHED` | 参数或访问检查拒绝，未发出业务请求 |
| `RESPONDED` | 收到完整业务响应，保留真实 HTTP 状态、JSON 或 ProblemDetail |
| `RESPONDED_INCOMPLETE` | 已收到 HTTP 状态，但响应超大、不支持或未完整读取；操作可能已经完成 |
| `UNKNOWN` | 请求发出后未能确认结果，或工具处理结果不确定 |

业务失败通过 `CallToolResult.isError=true` 表达。HTTP 边界的认证、服务关闭、大小限制和繁忙使用工程统一 ProblemDetail。JSON-RPC 解析与方法错误留在协议层。不确定或未完整读取时，应查询业务状态，不能直接重复提交写操作。

## 权限与管理 API

查看权限为 `system.mcp.view`，修改权限为 `system.mcp.update`；令牌管理额外要求 `super_admin`。普通列表使用 SearchRequest / SearchEngine。

管理根路径 `/api/v1/system-mcp`：

- GET `/configuration`；POST `/actions/update-configuration`；POST `/actions/refresh-catalog`。
- GET `/apis`、`/apis/modules`、`/apis/{id}`；`/apis/modules` 按 OpenAPI Tag 返回接口总数、可开放数和已开放数。
- GET / POST `/access-tokens`；POST `/access-tokens/{id}/actions/update`、`enable`、`disable`、`rotate`、`delete`。
- GET `/audits`。

页面不能修改路由或 Schema，只能修改开放状态。审计记录配置变更、令牌事件及工具调用的身份、接口、状态、耗时和错误分类，不记录完整令牌、请求体或响应正文。审计失败不覆盖已经完成的业务响应。

## 运行配置

统一前缀 `data-scalpel.system-mcp`：

| 配置项 | 默认值 | 用途 |
| --- | --- | --- |
| `public-base-url` | 空 | 外部可访问的应用基地址（含 context-path）；空时页面使用当前站点地址 |
| `connect-timeout` | `3s` | 本机连接超时 |
| `invoke-timeout` | `60s` | 业务调用及响应读取期限 |
| `concurrency` | `8` | 最大并发工具请求；无长队列 |
| `max-request-bytes` | `2097152` | MCP 与业务请求上限 |
| `max-response-bytes` | `1048576` | 单个业务响应及批量详情预算 |
| `audit-retention` | `30d` | 审计保留期，每小时清理 |

公开基地址支持 `DATASCALPEL_SYSTEM_MCP_PUBLIC_BASE_URL` 环境变量。总开关与开放清单保存在数据库，默认关闭，不由上述参数自动开放。代理部署需放行 `/system-mcp` 的 POST 和 Authorization，并给调用留出足够的超时。

完整系统联调遵循根 `./start-local-dev.sh` 启动约定，不另起临时应用或数据库。

## DSH 系统托管令牌

第三阶段新增 `managed` 标识；旧记录空值按手动令牌处理。系统内部为每个 DSH 用户绑定签发一次托管令牌，管理页面显示“DSH 系统托管”并隐藏操作，所有手工修改、启停、轮换和删除接口返回 409 `SYSTEM_MCP_TOKEN_MANAGED`。

令牌表继续只保存摘要。为使 DSH 重启后恢复当前用户身份，独立 DSH 用户绑定表使用部署专用 AES-GCM 密钥保存可恢复的秘密；这是 DSH 接入专属保管逻辑，普通令牌没有可恢复副本。完整值不通过用户接口返回，原生模型不接触凭据。用户停用/删除、角色调整、总开关和开放清单检查沿用现有逐次鉴权。

新增审计事件 `DSH_TOKEN_CREATED` 仅记录操作人、令牌 ID 与托管标识，不含秘密。系统 MCP 目录整体排除 `/api/v1/dsh`，防止递归。详见 [DSH 第三阶段](dsh-plugin-phase-three.md)。

托管令牌启用状态按当前用户状态在后台同步，用户停用或删除后标记为停用；重新启用时复用并恢复原令牌，内部状态变化记录 `DSH_TOKEN_STATE_CHANGED`。认证始终逐次检查用户，不依赖后台同步延迟。
