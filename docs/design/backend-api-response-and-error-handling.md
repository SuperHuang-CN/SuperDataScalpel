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
| 413 | `PAYLOAD_TOO_LARGE` | 上传或请求内容过大 |
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

Admin 的认证失败和无权限访问、Service Engine 的管理 Token 过滤和安全拒绝都必须使用公共 Writer。Engine 对 `/runtime/v1/services/**` 的 IP 策略拒绝使用 `403 ACCESS_DENIED`，`detail` 必须指出实际 TCP 来源 IP，并说明它命中黑名单或未命中白名单；不得信任代理转发头。Task Engine 回执 Filter 仅处理 `/api/v1/internal/task-runs/**`；作为 Servlet Filter 注册时，必须显式跳过所有其他路径，不能影响登录或普通业务接口。禁止手工拼装错误 JSON，禁止对 API 使用 `HttpServletResponse.sendError`。

## 前端消费规则

统一 HTTP 客户端位于 `data-scalpel-ui/src/shared/api`。`ApiError` 保留完整 `ApiProblem`：

- 页面默认向用户展示 `problem.detail`；
- 字段表单使用 `problem.violations` 定位校验项；
- 需要专门分支时依据稳定 `problem.code`，不根据中文文案或 HTTP 文本匹配；
- 业务模块不得各自解析响应体或定义第二套错误协议。

## 验收与演进

修改异常映射、认证/鉴权失败、错误码或 Problem 字段时，至少覆盖受影响的状态码和字段。公共基线覆盖：字段校验、非法 JSON、`ResponseStatusException`、参数类型错误、405、数据完整性、未预期 500，以及 Admin 和 Engine 的 401。

现有成功接口保持直接 DTO 和既有 HTTP 语义。若后续确实需要改变某个对外契约，应先讨论兼容性，并同步更新本文件、相关模块文档、前端类型和自动化测试。
