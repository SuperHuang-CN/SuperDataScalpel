# 第三阶段：Admin 接入 DSH

适用：DSH `0.1.5-rc.1`、工程插件 `0.3.0`。实现及运行验证状态见 [验证记录](dsh-plugin-phase-three-verification.md)。本阶段没有新助手页面。

## 架构与归属

Admin 托管 `/api/v1/dsh`，实现位于 Business 的 `dsh` 包。插件源码维护在 `integrations/dsh-plugin`，由 Docker 构建打包，不修改 DSH 核心。原生模型配置、原生会话和前两阶段验证入口继续保留。

新登录 JWT 的 `userId` 声明是系统用户 UUID。DSH 每次请求校验 UUID、JWT 到期时间及数据库中的当前启用状态。缺少 UUID 的旧 JWT 返回 `DSH_RELOGIN_REQUIRED`；原业务接口兼容旧 JWT。客户端不得指定用户、路径、模型、MCP 凭据或服务地址。

每用户一个 `/workspace/datascalpel-users/{userUuid}` 工作区，工作区下多个会话。删除后同名重建得到新的 UUID，不能接管旧文件或历史。跨用户访问统一 404。此能力采用现有轻量 RBAC，不增加多租户或数据行权限。

## 初始化与凭据

`ds_dsh_user_binding` 使用唯一用户 UUID、工作区 ID、托管令牌 ID、AES-GCM 密文及 PENDING/READY 状态。初始化在短事务内锁定当前用户行并生成或复用绑定；事务外向插件交付身份并创建或复用工作区，再以短事务记录 READY。失败保留可协调状态。

系统 MCP 令牌沿用随机 `dssmcp_` 秘密和摘要存储；`managed` 为空的旧记录按手动令牌处理。托管签发只由内部服务调用，管理 DTO 无该字段。管理页面显示“DSH 系统托管”并隐藏操作；服务端拒绝修改、启停、轮换和删除。

完整秘密仅加密保存在绑定表。AES-GCM 使用独立 32 字节 Base64 密钥、随机 12 字节 IV 和用户 UUID 作为认证附加数据。密钥丢失、变化或解密失败时拒绝执行，不静默重建令牌。秘密只经内部认证请求交付插件，插件在内存保存，不写入 DSH 控制存储、模型上下文或日志。默认不过期、不定期轮换。

停用和删除通过系统 MCP 现有认证立即拒绝后续请求；重新启用复用原绑定。Admin 每 5 秒协调插件用户授权，插件授权有效 10 秒、每秒检查过期并取消活跃任务、关闭事件连接。Admin 不可达也会失效；恢复后由新的已认证请求重新初始化。文件和历史保留，不重新分配。

## 接口

以下接口均要求 `Authorization: Bearer <登录 JWT>`。输入只接受明确 DTO；参数错误及连接错误遵循 RFC 9457。

| 方法和路径（`/api/v1/dsh`） | 输入 / 行为 |
| --- | --- |
| GET `/capabilities` | 启用状态和依赖就绪，不创建工作区或令牌 |
| POST `/workspace/actions/ensure` | 无身份参数，幂等初始化当前用户 |
| GET `/sessions` | `offset=0&limit=20` |
| POST `/sessions` | `{ "clientSessionId": "UUID" }`；重复创建复用原记录 |
| GET `/sessions/{id}` | 当前状态、待回答问题、历史边界 |
| GET `/sessions/{id}/messages` | `offset=0&limit=50`；只读原生历史 |
| POST `/sessions/{id}/messages` | `{ "clientMessageId": "UUID", "text": "文本" }`；202 接收，自动加载未活跃会话 |
| POST `/sessions/{id}/actions/resume` | 显式恢复，不重放中断轮次 |
| POST `/sessions/{id}/actions/cancel` | 当前执行取消请求；不影响其他会话 |
| POST `/sessions/{id}/interactions/{interactionId}/actions/respond` | `{ "answers": [{ "id": "问题ID", "selected": ["选项"] }] }`；自由文本用 `selected: []`、`custom: "回答"` |
| GET `/sessions/{id}/events` | SSE；HTTP 流客户端携带 Authorization，禁止 URL 令牌 |

发送中的会话拒绝新普通消息，允许追问和取消。消息 ID 重复且文本一致不会再执行，文本变化返回冲突。历史由 DSH 原生持久化保存，Admin 不复制聊天数据库。

## 插件与受控工具

新增 `/bridge/v3`，独立 Admin Bridge 密钥与 `/bridge/v1`、`/bridge/v2` 隔离。全局能力和授权协调接口由可信 Admin 使用；用户范围 `/bridge/v3/users/{uuid}` 的命令、查询、WebSocket 复用现有 Sessions、Events、Interactions。每个真实用户有独立控制存储域。

Preset `datascalpel-admin` 仅允许原生追问、`workspace_read/write` 和会话作用域的 `api_search/api_describe/api_invoke`。MCP 工具携带会话专属认证头；禁止修改全局头。沿用原有文件工具对路径逃逸、符号链接、硬链接、特殊文件及文本大小的限制。没有 Shell、全局文件工具或自动加载 Skills；这是可信用户共享实例下的工具访问边界，不承诺对抗宿主机 root。

系统 MCP 仍要求总开关、接口开放和当前用户权限，接入过程不自动开放接口。`/api/v1/dsh` 整体排除在系统 MCP 目录之外，避免递归控制助手。

默认每用户最多 4 个活跃 Agent，全局最多 8 个。槽位在异步创建前预留，失败和释放后归还；空闲 15 分钟释放，持久历史保留。

## 事件与失败语义

插件 WebSocket → Admin SSE。事件保留 sessionId、messageId、runId、toolCallId 和原生历史序号（适用时）。临时增量不跨重启重放，重连使用状态和持久历史恢复。断开订阅不取消模型任务。

每连接缓冲默认 1 MiB；慢客户端需重新同步。SSE 默认 15 秒心跳，检查 JWT 到期和当前用户状态；流开始后的错误通过 `connection.failed` 事件结束。超限连接或无法建立上游连接在流开始前返回 ProblemDetail。

控制请求不自动重试；超时、连接中断、无法读取完整响应返回 `DSH_RESULT_UNCERTAIN`，应查询状态和历史，不能直接重复提交可能已执行的命令。HTTP 客户端固定源地址、不跟随重定向，使用 HTTP/1.1 与原生 Node 服务通信。响应边读取边限制预算。

## 部署配置

Admin `data-scalpel.dsh` 默认关闭：

```yaml
data-scalpel:
  dsh:
    enabled: false
    base-url: http://127.0.0.1:13080
    bridge-key: ${DATASCALPEL_DSH_BRIDGE_KEY:}
    credential-key: ${DATASCALPEL_DSH_CREDENTIAL_KEY:}
    connect-timeout: 3s
    request-timeout: 45s
    max-request-bytes: 1048576
    max-response-bytes: 8388608
    event-buffer-bytes: 1048576
```

DSH Docker 通过不提交的 `deploy/dsh/.env.admin` 设置 `DSH_ADMIN_ENABLED`、`DATASCALPEL_DSH_ADMIN_BRIDGE_TOKEN` 和 `DSH_ADMIN_MCP_URL`。Bridge 密钥必须与 Admin 一致且至少 32 字符。连接方向单独配置：宿主机 Admin → DSH 用 `127.0.0.1:13080`，DSH → 脚本 Admin 用 `host.docker.internal:18080/system-mcp`；18887 是开发前端代理端口。

开发配置密钥写入不提交的 `config/application-local.yml`。生产部署通过部署密钥管理方式提供；备份数据库同时必须保留独立加密密钥。

## 旧助手移除与回退材料

移除旧 assistant Business 包、前端模块、旧模型页面、入口、专属状态、Canvas 提案及数据源草稿注入。共享配置权限和正常业务编辑功能保留。旧模型由 DSH 原生配置替代。

六表：`ai_assistant_session`、`ai_assistant_message`、`ai_assistant_run`、`ai_assistant_tool_invocation`、`ai_assistant_change_set`、`ai_llm_model_configuration`。维护脚本 `integrations/dsh-plugin/scripts/legacy-database.py` 在核对目标后备份结构与数据、校验清单和校验和，再在新应用旧路由下线后单事务 `DROP ... RESTRICT`。清单外依赖导致整次回滚，禁止 CASCADE。

备份保存在不提交的 `.local/dsh-phase-three/legacy-backup`，包含原始配置密钥的受保护副本；不能把备份、凭据或正文提交到仓库。具体完成状态见验证记录。

第四阶段的业务助手页面和上下文联动另行设计。

托管令牌启用状态按当前用户状态在后台同步，用户停用或删除后标记为停用；重新启用时复用并恢复原令牌，内部状态变化记录 `DSH_TOKEN_STATE_CHANGED`。认证始终逐次检查用户，不依赖后台同步延迟。
