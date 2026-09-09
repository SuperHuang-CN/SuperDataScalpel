# MCP 在线开发平台

## 1. 定位与边界

MCP 平台是 `data-scalpel-business` 下独立的 `business.mcp` 业务域，由 `data-scalpel-admin` 直接托管管理接口和公开 MCP 请求。它不依赖数据服务、Service Engine、API Gateway、API Consumer、平台数据源或模型，也不新增 Maven 模块和独立进程。

MVP 允许一个用户创建多个 MCP Server，并在每个 Server 中在线开发 Groovy Tool。Server 通过持久化草稿与不可变发布快照隔离开发态和调用态。

## 2. 生命周期

Server 状态为 `DRAFT`、`ENABLED`、`DISABLED`：

1. 创建 Server 时状态为 `DRAFT`，不自动签发访问凭证；管理员通过独立凭证管理授权调用方。
2. Server 基础信息和 Tool 变更只增加草稿修订，不影响当前调用版本。
3. 发布时校验所有启用 Tool 的 Schema 和 Groovy 编译结果，计算整服摘要。
4. 摘要变化时创建下一个不可变 Server Release 和 Tool Release；摘要未变化时复用最新版本。

   摘要使用固定字段顺序和规范化 Schema（对象键排序，忽略 JSON 格式空白）。对旧版摘要不匹配的记录，会按不可变快照内容重算比较，避免仅因摘要算法修正而增加版本。
5. 发布完成后原子切换 `activeReleaseId` 并进入 `ENABLED`。
6. 停用保留最后发布版本；重新启用恢复该版本，不发布草稿。
7. 只有从未发布的草稿 Server 可以物理删除。MVP 不提供版本回滚。

## 3. 持久化模型

- `ds_mcp_server`：基础信息、状态、草稿修订、当前发布版本和活动 Release。
- `ds_mcp_tool`：Tool 草稿、Schema、Groovy 脚本、样例、启用状态和修订。
- `ds_mcp_server_release`：不可变 Server 快照、版本、摘要和发布时间。
- `ds_mcp_tool_release`：不可变 Tool 定义与脚本快照。
- `ds_mcp_access_token`：独立调用凭证的名称、状态、有效期、SHA-256 摘要、AES-GCM 密文、提示、轮换版本和最近调用时间。
- `ds_mcp_access_token_server_grant`：访问凭证与 Server 的多对多授权，只保存两个 UUID 标量引用。
- `ds_mcp_server_access_token`：停用的旧 Server 级 Token 表，仅保留历史数据，不再参与认证。
- `ds_mcp_invocation_log`：不含业务正文的调用元数据。

实体之间只保存 UUID 标量引用。Schema、脚本和快照大文本使用 `LONG32VARCHAR`，不使用 PostgreSQL Large Object。

## 4. 管理 API

所有管理接口使用 Admin JWT 和权限编码，成功响应直接返回 DTO，错误使用统一 RFC 9457 Problem Detail。

### Server

- `GET /api/v1/mcp-servers`
- `POST /api/v1/mcp-servers`
- `GET /api/v1/mcp-servers/{id}`
- `POST /api/v1/mcp-servers/{id}/actions/update`
- `POST /api/v1/mcp-servers/{id}/actions/publish`
- `POST /api/v1/mcp-servers/{id}/actions/disable`
- `POST /api/v1/mcp-servers/{id}/actions/enable`
- `POST /api/v1/mcp-servers/{id}/actions/delete`
- `GET /api/v1/mcp-servers/{id}/access-tokens`
- `POST /api/v1/mcp-servers/{id}/actions/grant-access-token`
- `POST /api/v1/mcp-servers/{id}/actions/revoke-access-token`

旧 `GET /api/v1/mcp-servers/{id}/token` 和 `POST /api/v1/mcp-servers/{id}/actions/rotate-token` 返回 410，提示改用独立访问凭证。

### 访问凭证

- `GET/POST /api/v1/mcp-access-tokens`
- `GET /api/v1/mcp-access-tokens/server-candidates`：凭证管理权限下的 Server 分页选择接口。
- `GET /api/v1/mcp-access-tokens/{id}`
- `GET /api/v1/mcp-access-tokens/{id}/secret`：按需解密完整 Token，响应禁止缓存。
- `POST /api/v1/mcp-access-tokens/{id}/actions/update`
- `POST /api/v1/mcp-access-tokens/{id}/actions/update-servers`
- `POST /api/v1/mcp-access-tokens/{id}/actions/enable`
- `POST /api/v1/mcp-access-tokens/{id}/actions/disable`
- `POST /api/v1/mcp-access-tokens/{id}/actions/rotate`
- `POST /api/v1/mcp-access-tokens/{id}/actions/delete`

### Tool 与版本

- `GET/POST /api/v1/mcp-servers/{serverId}/tools`
- `GET /api/v1/mcp-servers/{serverId}/tools/{toolId}`
- `GET /api/v1/mcp-servers/{serverId}/tools/summaries`：列表专用摘要，不读取或返回脚本、Schema 和样例大文本；原完整列表接口保留兼容。
- `POST /api/v1/mcp-servers/{serverId}/tools/{toolId}/actions/update`
- `POST /api/v1/mcp-servers/{serverId}/tools/{toolId}/actions/delete`
- `POST /api/v1/mcp-servers/{serverId}/tools/actions/execute-draft`
- `GET /api/v1/mcp-servers/{serverId}/releases`
- `GET /api/v1/mcp-servers/{serverId}/releases/{version}`

### 调用日志

- `GET /api/v1/mcp-invocations`
- `GET /api/v1/mcp-invocations/{id}`
- `GET /api/v1/mcp-invocations/statistics/overview`

权限为 `mcp.view`、`mcp.create`、`mcp.update`、`mcp.execute`、`mcp.publish`、`mcp.delete` 和 `mcp.token.manage`。

## 5. MCP 协议入口

公开入口固定为 `POST /mcp/{serverCode}`，使用 `Authorization: Bearer dsmcp_...`。缺失、未知、停用、过期或已删除 Token 返回 401；有效 Token 未获目标 Server 授权返回 403；已授权但 Server 未发布启用返回 409。授权、停用、删除和轮换均在后续请求中立即生效，不缓存认证或授权结果。

当前是无状态 Streamable HTTP，不创建会话，不提供 SSE。支持：

- `initialize`
- `notifications/initialized`
- `ping`
- `tools/list`
- `tools/call`

支持协议版本 `2024-11-05`、`2025-03-26` 和 `2025-06-18`。协议解析和错误遵循 JSON-RPC 2.0；Tool 业务异常返回 `CallToolResult.isError=true`，而不是把业务错误转换为 HTTP 500。

协议方法由现有 MCP SDK 的 Stateless Server Handler 分发，MVC 只负责动态 Server 路由、HTTP 边界与调用审计。SDK 0.17 的强类型 Input Schema 无法表示全部根级关键字，因此对外发现响应保留原始完整 Schema；脚本执行器始终校验同一份原始定义。

非初始化请求校验 `MCP-Protocol-Version`，缺省按 `2025-03-26` 处理；不支持的头返回 400。无 ID 通知返回 202，不执行 Tool。队列满返回 HTTP 429 ProblemDetail，输入与脚本业务失败分别分类，不把执行繁忙或输出校验错误伪装成协议参数错误。

运行时按 Release ID 缓存不可变 Tool Bundle，采用访问顺序淘汰，最多 32 项且定义文本合计最多 16MB；超过预算的单项只用于当前调用。淘汰或关闭时等待当前引用释放，再关闭对应 SDK Server。每次请求仍先从管理库解析 `serverCode -> activeReleaseId`，因此多 Admin 实例能在发布或停用后收敛到同一状态。

## 6. Groovy Tool

脚本提供四个绑定：

- `args`：通过 Input Schema 校验后的 Map。
- `context`：当前 Server、Release 和 Tool 的只读元数据。
- `log`：`info/warn/error` 日志助手。
- `json`：`parse/stringify` JSON 助手。

Map 结果直接作为 `structuredContent`；List、标量或 `null` 统一包装为 `{ "value": ... }`。工作线程内先执行有界 JSON 序列化，只有普通 JSON 值可以离开工作线程。配置 Output Schema 时，对标准化后的结构结果执行校验。Input/Output Schema 的根类型必须为 object；保存及发布阶段使用 SDK 已引入的校验库完成元 Schema 校验和引用解析。禁止远程 `$ref`、`$dynamicRef`、`$recursiveRef`，校验器不启用网络资源加载。Schema 缓存最多 128 项。

默认限制为：执行 10 秒、并发 8、队列 32、脚本 200KB、单个 Schema 64KB、请求/响应 1MB、每个 Server 100 个 Tool。队列饱和返回 429。脚本超时会中断执行线程，但任意 Java 能力意味着该中断是尽力而为。

时间预算包含排队、Schema 校验、Groovy 编译、脚本运行及结果转换/校验/序列化；保存/发布时的定义校验也使用同一有界执行器。请求体按上限读取，不先完整加载再检查；响应限制同时覆盖文本与结构化结果及协议封装。限制不能阻止受信任 Java 代码在内部预先分配任意大小对象，仍不是内存沙箱。

编译缓存最多 64 项，每项使用独立 GroovyClassLoader；淘汰后待在途执行结束再清理缓存与关闭加载器。保存 Tool 的编译发生在数据库事务外，短事务内重新确认修订。更新可附带 `expectedRevision`；旧页面或并发校验期间定义变化返回 409，前端保留本地内容。未携带该字段的旧调用继续兼容。

## 7. 安全声明

Groovy 不是沙箱。获得 Tool 编辑和执行权限的用户等价于获得 Admin 进程内的受信任代码执行能力，脚本可以访问类路径、文件、网络、系统环境，创建线程，甚至调用进程终止能力。生产部署必须把 MCP 开发权限限制给可信开发人员，并通过主机、容器和网络策略约束 Admin 进程本身。

Access Token 使用 `dsmcp_` 前缀和 32 字节随机秘密。数据库保存用于认证的 SHA-256 摘要，以及使用独立 `data-scalpel.mcp.credential-key` 进行 AES-GCM 加密的密文；普通响应不返回两者，只有具备 `mcp.token.manage` 权限的显式查看接口可以解密原文。一个凭证可授权多个 Server，一个 Server 可被多个凭证访问；授权只覆盖目标 Server 当前发布的全部 Tool。轮换后摘要和密文一起替换，旧 Token 立即失效且不保存历史明文。

加密密钥缺失或无效不阻止 Admin 启动，也不影响摘要认证；创建、轮换和查看完整 Token 返回 503。第一版上线不迁移旧 Server 级 Token，旧 Token 全部失效；既有 Server、Tool、Release 和调用日志保持不变。

## 8. 调用审计与保留

调用日志保存协议方法、Request ID、Server/Release/Tool 快照标识、状态、耗时、请求/响应字节数、来源地址、User-Agent、访问凭证 ID、调用时名称快照和 Token 修订。严禁保存完整 Token、Tool 参数、返回值和脚本日志正文。凭证最近调用时间独立保存在凭证主记录中，不随默认 30 天日志清理而丢失；历史日志允许凭证字段为空。

`errorSummary` 只保存稳定错误分类，不保存异常原文；来源地址使用实际连接地址，不直接信任 X-Forwarded-For。审计写失败会产生安全告警，不中断业务响应。脚本 `log` 助手仅在草稿调试中保留最多 100 行、每行 500 字符，随调试响应的 `logs` 返回，不写管理进程日志。公开调用不收集这些正文。受信任脚本自行调用 Java 日志不属于此助手的约束范围。

## 9. 配置

配置前缀为 `data-scalpel.mcp`：

- `public-base-url`
- `credential-key`：MCP Token 独立 AES-GCM 密钥，对应 `DATASCALPEL_MCP_CREDENTIAL_KEY`。
- `execution-timeout`
- `execution-concurrency`
- `execution-queue-capacity`
- `max-script-size`
- `max-schema-size`
- `max-payload-size`
- `max-tools-per-server`
- `invocation-retention`
- `invocation-cleanup-cron`

前端入口为“MCP 管理 / MCP Server”、“MCP 管理 / 访问凭证”和“MCP 管理 / 调用日志”。Server 使用 `MCP_SERVER` 独立目录范围。访问凭证列表默认仅展示脱敏提示；查看与复制完整 Token 时即时调用禁止缓存的接口，关闭弹窗后清除前端明文状态。Server 详情的访问凭证页签维护同一授权关系。

## 10. 编辑与列表行为

- Monaco 使用工程本地资源及懒加载，不访问公网 CDN。
- Tool 编辑页只在初次加载或资源身份切换时初始化本地草稿，后台刷新不覆盖编辑内容；保存期间禁用编辑，离开未保存页面需确认。
- 可视化参数仅编辑可以无损表示的标量属性，嵌套结构及复杂约束保留 JSON 模式，根级属性不被强制改写。
- 全部测试样例原样保留，最多 10 项；临时测试参数仅在明确“添加为样例”后参与保存。
- Server 列表批量统计 Tool 数量；普通筛选点击查询后提交并回到第一页。详情调用日志提供服务端分页，返回 Tool 列表保留页签；各列表提供加载失败和重试入口。
