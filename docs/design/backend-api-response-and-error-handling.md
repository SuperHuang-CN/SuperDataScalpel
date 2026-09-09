# 后端 API 响应与异常处理

## 目标与范围

DataScalpel 是模块化单体。接口响应应当直接、可预测，不引入无实际价值的通用成功包裹层；错误响应则使用一个全系统一致、可由前端可靠处理的标准。

本约定适用于 Admin 的 `/api/**` 业务接口，以及 Service Engine 的 `/internal/**` 和 `/runtime/**` 接口。Actuator、Swagger/OpenAPI 等第三方或框架端点不强制改写为该格式。

## 成功响应

成功响应直接表达资源或操作结果：

| 场景 | 响应 |
| --- | --- |
| 查询详情、普通命令结果 | 明确的 Response DTO，通常为 `200 OK` |
| 分页列表 | `PageResponse<T>`，通常为 `200 OK` |
| 创建资源 | 明确的 Response DTO，使用 `201 Created`；可提供 `Location` |
| 删除或无返回命令 | `204 No Content` |
| 文件下载 | 原始文件流及正确的媒体类型 |

不得新增 `Result<T>`、`ApiResponse<T>`、`success/data/message` 等成功响应外壳。HTTP 状态码和 DTO 已足以表达成功语义。

## 错误响应

所有由应用控制的 HTTP 错误使用 RFC 9457 `ProblemDetail`，媒体类型为 `application/problem+json`。响应结构示例：

```json
{
  "type": "urn:datascalpel:problem:validation-failed",
  "title": "请求参数校验失败",
  "status": 400,
  "detail": "请求参数校验失败",
  "instance": "/api/v1/data-sources",
  "code": "VALIDATION_FAILED",
  "timestamp": "2026-07-14T12:00:00Z",
  "violations": [
    { "field": "name", "message": "不能为空" }
  ]
}
```

字段含义：

| 字段 | 约定 |
| --- | --- |
| `type` | 稳定的 `urn:datascalpel:problem:<kebab-code>`，用于文档和程序识别。 |
| `title` | 稳定的错误类别标题。 |
| `status` | HTTP 状态码。 |
| `detail` | 面向调用方的具体说明，不能含密码、连接串、堆栈、SQL 或内部实现细节。 |
| `instance` | 本次请求的 URI 路径。 |
| `code` | 稳定的大写错误码，前端差异化行为应优先依赖它。 |
| `timestamp` | 服务端生成的 ISO-8601 时间戳。 |
| `violations` | 仅请求校验失败时出现；每项为 `field` 和 `message`，不回显被拒绝的原始值。 |

不定义 `path`、`success`、`message`、`error` 等并行错误字段。

运行日志查询中的 `WAITING`、`ARCHIVING` 和 YARN 日志尚未聚合属于可预期读取状态，使用成功响应中的
状态字段表达。Dispatcher 不可达、后端日志命令异常或对象存储故障仍返回标准 `ProblemDetail`，前端应
保留上次成功取得的日志窗口并提供重试，不得把基础设施错误显示成空日志。

## 错误码与 HTTP 状态

| HTTP | `code` | 典型场景 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | Bean Validation 或方法参数校验失败 |
| 400 | `MALFORMED_REQUEST` | JSON 或请求体无法解析 |
| 400 | `INVALID_SEARCH_REQUEST` | 统一 Search DSL 不合法 |
| 400 | `INVALID_QUERY` / `BAD_REQUEST` | 查询协议或普通请求参数无效 |
| 401 | `AUTHENTICATION_REQUIRED` | 未登录、Token 缺失或无效 |
| 403 | `ACCESS_DENIED` | 已认证但没有所需权限，或 Engine 业务服务未通过来源 IP 策略 |
| 404 | `RESOURCE_NOT_FOUND` | 资源或路由不存在 |
| 405 | `METHOD_NOT_ALLOWED` | 请求方法不受支持 |
| 406 | `NOT_ACCEPTABLE` | 请求的响应格式不可接受 |
| 409 | `BUSINESS_CONFLICT` | 资源状态、唯一性或并发业务冲突 |
| 409 | `MODEL_REFERENCED` | 模型仍被任务、数据服务或当前有效血缘引用，删除被阻止 |
| 400 | `PANORAMA_INVALID_FILE` | JPEG 格式、尺寸、投影或完整性不合规 |
| 413 | `PANORAMA_FILE_TOO_LARGE` | 全景原图超过 100 MiB |
| 409 | `PANORAMA_PROCESSING_CONFLICT` | 候选忙、请求接收中或操作不符合当前状态 |
| 409 | `PANORAMA_CONTENT_CHANGED` | 媒体或变更请求的预期内容版本已过期 |
| 409 | `PANORAMA_PREVIEW_UNAVAILABLE` | 尚无可用当前成品 |
| 503 | `PANORAMA_STORAGE_UNAVAILABLE` | 全景文件存储未配置或暂不可用 |
| 413 | `PAYLOAD_TOO_LARGE` | 上传或请求内容过大 |
| 429 | `TOO_MANY_REQUESTS` | 执行队列饱和，例如 MCP Tool 执行繁忙 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 请求媒体类型不支持 |
| 501 | `NOT_IMPLEMENTED` | 功能尚未实现 |
| 502 | `UPSTREAM_UNAVAILABLE` | 上游服务不可用或返回异常 |
| 503 | `SERVICE_UNAVAILABLE` | 当前服务不可用 |
| 504 | `UPSTREAM_TIMEOUT` | 上游服务超时 |
| 500 | `INTERNAL_ERROR` | 未预期异常；对外只返回安全的通用说明 |

同一 HTTP 状态允许有不同的明确错误码；新增错误码前应确认其稳定性、补充本表并添加测试。

## 后端实现规则

公共实现位于 `data-scalpel-web-core`：

- `ProblemType` 维护稳定错误码、标题、默认状态和 `type` URI。
- `ProblemDetailFactory` 负责构造公共字段。
- `ProblemDetailsExceptionHandler` 统一处理验证、请求解析、参数绑定、`ResponseStatusException`、数据完整性和未预期异常。
- `ProblemDetailWriter` 供 Spring Security 或 Servlet Filter 等 MVC 异常处理器无法覆盖的位置输出同一格式。

业务代码可继续使用 `ResponseStatusException` 表达直接、预期的业务失败。新代码必须选择准确状态码；不要为了复用错误结构创建无业务价值的异常层级。不可恢复的内部异常不应直接携带敏感上下文给调用方。

Admin 的认证失败和无权限访问、Service Engine 的管理 Token 过滤和安全拒绝都必须使用公共 Writer。Engine 对 `/open-api/v1/**` 动态服务路由的 IP 策略拒绝使用 `403 ACCESS_DENIED`，`detail` 必须指出实际 TCP 来源 IP，并说明它命中黑名单或未命中白名单；不得信任代理转发头。Task Engine 回执 Filter 仅处理 `/api/v1/internal/task-runs/**`；作为 Servlet Filter 注册时，必须显式跳过所有其他路径，不能影响登录或普通业务接口。禁止手工拼装错误 JSON，禁止对 API 使用 `HttpServletResponse.sendError`。

## Service Engine JDBC 监控

Admin 的数据源连接池摘要和监控详情接口沿用 `service.engine.view`，Engine 内部接口继续使用 Management Token。监控不可用不修改数据源注册或健康状态：运行时连接池不存在返回成功 DTO 中的 `NOT_LOADED`，池存在但监控未启用或不支持返回 `UNSUPPORTED`，均不伪造零值指标。

Admin 引擎/注册记录不存在返回 `404 RESOURCE_NOT_FOUND`；对 GeoServer 请求 API Studio 监控返回 `400 BAD_REQUEST`。远程监控接口缺失、Engine Token 错误、连接/读取失败以及响应 Engine Code 或数据源 ID 不匹配返回 `502 UPSTREAM_UNAVAILABLE`，使用安全、可操作的 `detail`，不直接透传上游响应正文。老版 Engine 的 404 提示升级并重启；引擎的 401/403 提示检查 Management Token，而不是触发 Admin 用户重新登录。

## MCP 协议边界

MCP 管理接口继续使用上述 ProblemDetail 契约。公开 `POST /mcp/{serverCode}` 内的 JSON-RPC 错误遵循 MCP：非法 JSON 为 `-32700`，非法消息为 `-32600`，未知方法为 `-32601`，非法协议参数为 `-32602`；有效 Tool 调用的输入校验失败、脚本异常、超时、输出不符合 Schema 或超限，通过 `CallToolResult.isError=true` 返回安全说明。通知返回 202 空响应，不执行无 ID 的 Tool 调用。

认证失败、Server 未授权、已授权但 Server 未发布启用、HTTP 请求超限、不支持的协议版本头及执行队列饱和属于 HTTP 边界，分别使用公共 401、403、409、413、400、429 ProblemDetail，不得将 429 改写成 HTTP 200 的参数错误。调用审计只记录稳定分类，如 `INPUT_INVALID`、`SCRIPT_FAILED`、`OUTPUT_SCHEMA_MISMATCH`、`EXECUTION_TIMEOUT`、`OUTPUT_TOO_LARGE` 和 `HTTP_429`，不记录脚本异常原文或校验器实例值。审计写入失败输出不含请求正文和数据库绑定值的告警。

经过 `mcp.execute` 权限校验的草稿调试响应可返回临时调试错误和有界脚本日志；这些内容不进入调用审计或平台持久化日志。Groovy 仍是受信任代码执行环境，此约定不限制脚本自行使用 Java 日志或文件能力。

## 前端消费规则

统一 HTTP 客户端位于 `data-scalpel-ui/src/shared/api`。`ApiError` 保留完整 `ApiProblem`：

- 页面默认向用户展示 `problem.detail`；
- 字段表单使用 `problem.violations` 定位校验项；
- 需要专门分支时依据稳定 `problem.code`，不根据中文文案或 HTTP 文本匹配；
- 业务模块不得各自解析响应体或定义第二套错误协议。

## 验收与演进

修改异常映射、认证/鉴权失败、错误码或 Problem 字段时，至少覆盖受影响的状态码和字段。公共基线覆盖：字段校验、非法 JSON、`ResponseStatusException`、参数类型错误、405、数据完整性、未预期 500，以及 Admin 和 Engine 的 401。

现有成功接口保持直接 DTO 和既有 HTTP 语义。若后续确实需要改变某个对外契约，应先讨论兼容性，并同步更新本文件、相关模块文档、前端类型和自动化测试。

全景图片和候选处理错误的业务边界见 [全景影像管理 V1](../development/panorama-management-v1.md)。异步处理失败通过资源状态和安全摘要表示，原图读取/命令拒绝继续返回 ProblemDetail。


### 指标结果绑定的引用保护

`MODEL_REFERENCED`（409）也覆盖当前已发布启用指标对结果模型及字段的引用。模型引用响应增加 `metrics` 摘要并保留 `tasks`、`services`；解除指标绑定保护需修订并发布替代绑定，或停用指标。草稿、停用和历史版本不单独阻止模型/字段删除。

### 系统 MCP 边界

系统 MCP 的 HTTP 认证、大小限制、服务关闭和繁忙响应沿用本规范的 ProblemDetail。JSON-RPC 错误由协议层表达；进入工具执行后的业务 HTTP 状态、原 ProblemDetail 和执行状态作为 MCP 工具元信息返回，不改变业务 API 的成功或错误格式。超时及响应不完整的执行语义见 [系统 MCP](system-mcp.md#身份与执行边界)。
