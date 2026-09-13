# DSH 插件第一阶段：基础对话与生命周期验证

状态：第一阶段已实现并完成单操作者验证。本文维护本阶段契约；具体执行证据及限制见[验证记录](dsh-plugin-phase-one-verification.md)。

源码、配置和脚本维护在 [integrations/dsh-plugin](../../integrations/dsh-plugin/README.md)，已安装到现有 DSH Docker 服务。第一阶段不表示已支持多用户。

关联：[四阶段接入路线图](dsh-integration-roadmap.md)。本文只定义第一阶段；第二至第四阶段完成前序工作后再设计。

## 1. 目标、范围与基线

开发一个安装在共享 DSH 实例中的插件，通过内部 HTTP 接口和 WebSocket 事件，用脚本完成工作区初始化、创建会话、真实模型对话、原生追问、取消、断线重连及重启后恢复。保留 DSH 原生 Agent 循环、上下文压缩和会话持久化，不实现另一套智能体。

第一阶段仅供一个可信的技术验证操作者使用。固定验证工作区，不接入 DataScalpel 登录或用户目录；不实现多用户身份、MCP 身份适配、文件隔离、系统业务操作、助手页面、文件传输或工具授权审批桥接。Bridge 访问凭据只保护技术验证接口，不代表已具备用户级授权。

用户已选择本阶段**只验证基础对话，不复用或验证 MCP 和 Skills**。已有原生模型、MCP、Skills、工作 Preset、会话和工作文件保留。

| 项目 | 基线 |
| --- | --- |
| 运行包 | `@deepseek-ai/dsh@0.1.5-rc.1` |
| 插件包 | `@datascalpel/dsh-plugin`，初始版本 `0.1.0` |
| 语言与运行时 | TypeScript、Node.js 24、ES modules |
| 源码位置 | 工程根目录下 `integrations/dsh-plugin/`，在开发任务中维护 |
| 部署 | 现有 DSH Docker 服务；镜像构建时安装插件，启动时不下载安装 |
| 验收入口 | 内部接口和 Node.js 验证脚本；不增加测试页面 |

旧版源码 ZIP 只作分析参考。开发以安装包的公开导出及类型声明为准，不依赖其私有文件路径、复制核心实现或 monkey patch。涉及不兼容升级时先更新基线及验证记录。

## 2. 源码结构与职责

插件作为仓库内的独立 Node.js 包维护，不加入 Maven Reactor，不导入 DataScalpel Java 或前端运行时代码。不引入新的服务框架、数据库或消息系统。

```text
integrations/dsh-plugin/
├── package.json
├── package-lock.json
├── tsconfig.json
├── README.md
├── src/
│   ├── index.ts          # Cordis 入口、依赖和生命周期
│   ├── config.ts         # 配置及启动校验
│   ├── contracts.ts      # Bridge 输入、输出和事件类型
│   ├── routes.ts         # HTTP、WebSocket、认证和参数校验
│   ├── workspaces.ts     # 固定目录初始化、Workspace 注册
│   ├── sessions.ts       # Agent handle、会话归属、消息接收和历史
│   ├── projection.ts     # 原生追加消息与执行结果投影
│   ├── events.ts         # 原生事件适配、订阅和有界缓冲
│   ├── interactions.ts   # 原生追问及回复生命周期
│   └── storage.ts        # 现有 DSH 存储中的插件控制元数据
├── presets/              # 独立验证 Preset
├── scripts/              # 接口、真实对话及恢复验证脚本
└── tests/                # 有针对性的插件测试
```

不预建用户授权、MCP、Skills 管理或自定义沙箱实现。编译产物、依赖目录和打包文件不作为源码提交。脚本和配置样例与源码一同维护，禁止仅在容器里修改后作为最终交付。

DSH/Cordis 运行时包使用兼容基线的 peer dependencies，本地开发以对齐版本的 dev dependencies 提供类型和验证环境；构建不把第二份 DSH/Cordis 打进插件。直接使用的其他包应显式声明并锁定，不能依赖未声明的传递依赖。优先使用 Node.js 内置能力和 DSH 现有服务；WebSocket 复用其已有依赖栈。

### 2.1 已核对的公开扩展点

以下能力使用当前安装包的公开接口；具体运行验证范围见验证记录。

| 扩展点 | 本阶段用途 |
| --- | --- |
| `ctx.webServer.register` / `registerUpgrade` | 在现有监听服务注册独立路由及 WebSocket，不占用原生 `/api` |
| `ctx.workspaceRegistry.create` / Workspace 会话关联 | 注册已存在的规范化目录，重复创建复用记录 |
| `ctx.agents.create` / `resume` 及 `setup` | 在发布 Agent 前完成 Preset 和交互组装 |
| `ctx.agentPresets.mount` | 在 `setup` 阶段挂载验证 Preset |
| Agent 消息、取消及 handle disposal | 驱动会话并有序释放插件自己创建的 Agent |
| `agent/assistant-stream` 与 `session/event` | 分别读取临时输出流和持久执行事实 |
| `ctx.sessionPersistence` 的只读访问 | 不启动模型请求地读取历史 |
| `user-questions/request` 与原生追问工具 | 把追问交给验证脚本，并将回复返回原生等待方 |
| DSH 现有领域存储能力 | 保存插件会话归属、创建请求及消息接收控制信息 |

原生 Agent 状态仅有 `idle` / `running`，不能直接拿它表达是否已卸载、是否等待追问和上一次执行结果。Bridge 需分别投影这些信息。

## 3. 配置、Preset 与安装

### 3.1 插件配置

| 配置 | 默认值或要求 | 行为 |
| --- | --- | --- |
| `enabled` | `false` | 未启用不注册接口、不创建工作区或 Agent |
| `workspaceRoot` | `/workspace/bridge-phase-one` | 唯一验证目录；只由部署配置提供 |
| `presetId` | `datascalpel-bridge-phase-one` | 随插件交付的验证 Preset |
| `provider` / `model` | 启用时必填 | 引用现有 DSH 模型提供方和模型标识，不接收模型密钥 |
| `accessTokenEnv` | `DATASCALPEL_DSH_BRIDGE_TOKEN` | 从进程环境读取独立高熵随机访问秘密；启用时不允许为空 |
| `maxRequestBytes` | `1048576` | HTTP 请求大小上限，读取过程中限制 |
| `maxActiveSessions` | `4` | 本阶段可同时保持的 Agent handle 上限，超出返回繁忙，不排长队 |
| `eventBufferBytes` | `1048576` | 单连接待发送事件的字节上限 |

请求参数不能覆盖目录、Preset、模型、环境变量或访问凭据。配置校验失败应明确拒绝激活，不静默切换模型、创建替代环境或回退到现有工作 Preset。外部模型服务的网络可用性由真实调用证明，不能仅凭存在配置就宣称健康。

文档及样例只写环境变量名，不填写真实密钥。访问秘密只在插件内使用，不向模型暴露，不进入工具参数、会话事件或普通日志。Bridge 不直接读取或复制原生模型密钥。

### 3.2 验证 Preset

验证 Preset 使用独立标识和目录，通过部署配置将其加入原生发现范围，不替换现有默认 Preset。内容由插件仓库维护，安装为只读部署资产。

只组装基础对话、必要的原生上下文处理和原生追问工具。模型提供方和会话持久化仍复用 Host 服务；不加载 MCP、Skills、终端、文件编辑、子 Agent 或需要交互授权的执行工具。

在 Agent 创建阶段核对有效工具集合，不能只检查 Preset 文件中没有写 MCP。若部署的全局工具贡献造成额外能力继承，使用公开的 Agent 作用域限制机制约束该验证 Agent；无法通过公开扩展点实现时明确报告组装失败，不卸载全局插件、不修改原生工作 Preset。禁止以静默继承全部工具完成验收。

### 3.3 构建与 Docker 接入

插件提供 `build`、`check`、`test` 和 `verify` 命令。使用 npm 锁文件安装并编译，打包时只包含运行产物、Preset、包元信息和说明。

Compose 构建上下文已调整为工程根目录，并显式指定 `deploy/dsh/Dockerfile`；使用 Dockerfile 专属的 `Dockerfile.dockerignore` 排除本地配置、凭据、运行文件、依赖及无关制品，仅复制部署清单和插件源码所需文件。插件在构建阶段编译并安装到 `/opt/dsh` 的同一 Node.js 依赖树，避免重复运行时。启动阶段只加载构建产物。

通过额外部署配置行加载插件和验证 Preset。保留现有监听端口、数据卷、原生配置和模型设置；不直接修补 `node_modules`。部署操作及升级命令仍由 [Docker 运维说明](../../deploy/dsh/README.md)维护，开发交付时补充插件相关步骤并从插件 README 链接。

启用插件需要应用部署配置时，复用现有 DSH Compose 服务。撤销插件配置可停止 Bridge 并恢复原有运行方式，保留验证目录和会话记录；不删除卷，也不自动清理 Docker 缓存或镜像。执行部署前检查正在运行的原生任务，避免未经处理地中断用户工作。

## 4. 内部接口契约

以下是第一阶段已实现的接口契约，固定前缀为 `/bridge/v1`。HTTP 只用 GET / POST，成功直接返回数据对象，错误使用 RFC 9457 `application/problem+json`。这套 TypeScript 插件协议不修改现有 DataScalpel API 格式。

### 4.1 通用边界

- HTTP 与 WebSocket 握手都要求 `Authorization: Bearer <Bridge 访问秘密>`；原生页面 Cookie 不能代替认证，URL 不允许携带秘密。
- 不向普通浏览器开放跨域访问；第一阶段客户端是内部验证脚本。浏览器 WebSocket 的用户认证方式留到页面接入阶段设计。
- 全部会话相关接口先检查持久化的插件归属记录。非本插件会话统一返回 404，不通过请求一个原生会话 ID 自动接管它。
- 插件标识采用 UUID；会话路径先检查归属，因此非 UUID 的原生会话 ID 同样返回 404。创建请求、消息及交互 ID 校验 UUID，消息和回答只接受声明字段。没有请求体的动作接受空请求体或 `{}`。
- 请求过大返回 413，未知路由返回 404，不允许的方法返回 405；无效参数返回 400。
- 列表采用 `offset` / `limit`，默认 `0` / `20`、最大 `100`。历史默认 `50`、最大 `200`，按持久消息顺序读取。响应为 `{items, offset, limit, hasMore}`，不复用或扩展 DataScalpel Search DSL。
- 列表和只读历史不创建 Agent、不调用模型，也不修改原生历史。分页只包含已提交记录，实时增量走事件流。

### 4.2 能力及工作区

**`GET /capabilities`**

正常响应 200，返回版本和组件就绪状态，例如：

```json
{
  "protocolVersion": "1",
  "pluginVersion": "0.1.0",
  "dshVersion": "0.1.5-rc.1",
  "ready": true,
  "capabilities": ["workspace", "sessions", "text-chat", "events", "user-questions", "resume", "cancel"],
  "components": {"storage": "READY", "preset": "READY", "modelConfiguration": "READY"}
}
```

`ready` 表示执行所需配置与服务已准备好，不代表真实模型请求已经成功。已注册接口但执行依赖未就绪时返回 503 ProblemDetail，指出缺失组件；插件未成功加载时接口可能不存在，验证脚本需区分 HTTP 不可达、路由不存在与能力未就绪，不把它们归为模型失败。

**`POST /workspaces/actions/ensure`**

请求 `{}`。只初始化配置指定的目录，再调用原生 Workspace 注册；200 返回 `{workspaceId, path, title}`。重复请求和并发初始化得到同一规范目录和记录。目录已经存在则保留内容；路径是普通文件、无法解析或无法创建时返回明确错误，不另选目录。

### 4.3 会话创建、读取与恢复

**`POST /sessions`**

```json
{"clientSessionId":"10000000-0000-4000-8000-000000000001"}
```

`clientSessionId` 是客户端生成并在重试时复用的创建请求 UUID。插件先确保工作区存在，分配并持久记录自己的 DSH 会话标识，完成 Agent 组装、会话持久化和 Workspace 关联后返回 201。重复请求返回 200 及原会话，不再次创建。创建未完成时保留可辨识的控制状态，重试只补全该次创建，不生成另一份历史。

创建、读取和恢复的统一会话视图：

```json
{
  "sessionId": "20000000-0000-4000-8000-000000000001",
  "workspaceId": "30000000-0000-4000-8000-000000000001",
  "runtimeState": "IDLE",
  "lastOutcome": null,
  "pendingInteractions": [],
  "createdAt": "2026-09-11T06:00:00Z"
}
```

`runtimeState` 为 `UNLOADED`、`IDLE`、`RUNNING`、`WAITING_FOR_INPUT` 或 `CANCELLING`；`lastOutcome` 独立表达最近一轮的 `COMPLETED`、`FAILED`、`CANCELLED`、`INTERRUPTED`，没有执行过则为 null。结果从原生执行事实投影，不以 HTTP 连接状态推断。

**`GET /sessions`** 返回归属本插件且位于验证工作区的会话列表，按创建时间倒序、会话 ID 作为稳定次序补充。**`GET /sessions/{id}`** 返回上述视图，含当前尚有效的追问。

**`POST /sessions/{id}/actions/resume`** 请求 `{}`，200 返回会话视图。已有插件持有的活跃 handle 则复用；否则按原生持久化恢复并重新执行 setup。并发恢复只创建一个 handle。若会话已由其他原生入口占有写入权，返回 409 `BRIDGE_SESSION_OWNED`，不抢占或驱逐现有会话。

恢复不主动发送提示词，不重新发出中断消息，也不自动执行崩溃时遗留的收件箱工作。调用公开恢复/收件箱能力在 Agent 接收新工作前处理遗留待执行项；若当前版本无法阻止自动执行，作为技术验证阻塞项报告，不能直接恢复到会自动重放的状态。

### 4.4 消息与历史

**`POST /sessions/{id}/messages`**

```json
{"clientMessageId":"40000000-0000-4000-8000-000000000001","text":"请记住本轮的标记是青石。"}
```

`text` 必须非空。本阶段不接受附件、工具结果、任意角色消息或系统提示词覆盖。成功接收返回 202：

```json
{"sessionId":"20000000-0000-4000-8000-000000000001","messageId":"40000000-0000-4000-8000-000000000001","accepted":true,"duplicate":false}
```

接收前要求会话已加载且处于空闲状态；卸载状态返回 409 `BRIDGE_SESSION_NOT_LOADED`，执行中返回 409 `BRIDGE_SESSION_BUSY`。重复消息判定先于繁忙检查：同一会话相同 ID、相同文本返回原接收结果并置 `duplicate: true`；同一 ID 不同文本返回 409 `BRIDGE_MESSAGE_CONFLICT`。

用每会话串行控制避免同时接收两条普通消息。稳定 ID 进入原生消息记录，回复 202 前确认接收事实已通过持久化屏障。插件控制元数据与原生日志不是跨存储原子事务，重启时需对照稳定 ID 恢复接收记录；若持久化失败或中断导致是否接收无法确认，返回 `BRIDGE_MESSAGE_OUTCOME_UNKNOWN`，禁止自动再次入队。它是消息接收错误，不代表工具或业务操作已回滚。

**`GET /sessions/{id}/messages?offset=0&limit=50`** 返回持久化历史投影。每项包含 `id`、`role`、`content` 和 `sourceSeq`；`content` 使用带 `type` 的文本、工具调用或工具结果块，保留关联的工具调用 ID。按原生已提交的对话消息投影，不把上下文压缩后的模型 surface 当作完整的人类历史；不暴露内部请求头、密钥或未提交文本片段。

**`POST /sessions/{id}/actions/cancel`** 请求 `{}`。运行中返回 202 及 `CANCELLING` 视图，调用原生取消并等待原生结束事件决定结果；已空闲或已卸载则 200 返回当前视图。不得仅因发出取消请求就立即发布 `run.cancelled`。明确取消同时撤销本轮追问和未开始工作；取消不是撤销已经发生的外部操作。

### 4.5 原生追问

**`POST /sessions/{id}/interactions/{interactionId}/actions/respond`**

```json
{"answers":[{"id":"color","selected":["蓝色"],"custom":""}]}
```

沿用 DSH 原生答案结构：每项以问题 ID 关联，`selected` 为选项标签数组，`custom` 为自由文本。校验问题集合、选项、多选限制和自由文本约束后，一次性结算对应原生等待方。200 返回 `{interactionId, accepted: true}`。

追问由 Agent 作用域的 `user-questions/request` 处理器接管，通过公开 `prepend` 选项优先于原生页面回答器处理自己的 Agent；其他 Agent 继续交给原生回答器。插件分配独立交互 UUID，保留原生问题结构并发出 `interaction.requested`。同一会话内只处理当前有效交互；无效或不匹配的回答返回 400。交互重复回复、取消、重启后失效统一返回 410 `BRIDGE_INTERACTION_EXPIRED`。回答成功发布 `interaction.resolved`，Agent 继续执行。

等待追问的 Promise 和 AbortSignal 只在进程内存活。页面断线不取消追问；会话取消、卸载或进程退出会使其失效。重启后不得凭历史里的问题记录重建一个已不存在的授权或等待对象。工具授权审批不是追问，本阶段不提供审批接口或自动批准降级。

### 4.6 错误格式与分类

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "会话正在执行，请先等待完成或取消当前运行。",
  "code": "BRIDGE_SESSION_BUSY",
  "timestamp": "2026-09-11T06:00:00Z"
}
```

| 分类 | HTTP 状态 / 稳定 code |
| --- | --- |
| 缺失或错误的 Bridge 凭据 | 401 / `BRIDGE_UNAUTHORIZED` |
| 参数不合法、超限 | 400 / `BRIDGE_ARGUMENT_INVALID`；413 / `BRIDGE_REQUEST_TOO_LARGE` |
| 未知或非本插件会话 | 404 / `BRIDGE_SESSION_NOT_FOUND` |
| 会话忙、未加载、被其他入口占有 | 409 / `BRIDGE_SESSION_BUSY`、`BRIDGE_SESSION_NOT_LOADED`、`BRIDGE_SESSION_OWNED` |
| 消息 ID 与内容冲突 | 409 / `BRIDGE_MESSAGE_CONFLICT` |
| 追问已失效 | 410 / `BRIDGE_INTERACTION_EXPIRED` |
| Agent 容量已满 | 503 / `BRIDGE_CAPACITY_EXCEEDED` |
| 配置或执行组件未就绪 | 503 / `BRIDGE_NOT_READY` |
| 未适配的 DSH 版本 | 503 / `BRIDGE_VERSION_UNSUPPORTED`（启动拒绝注册） |
| 会话配置变化、工作区不可用 | 409 / `BRIDGE_SESSION_CONFIGURATION_CHANGED`；500 / `BRIDGE_WORKSPACE_UNAVAILABLE` |
| 路由、方法、格式不支持 | 404 / `BRIDGE_ROUTE_NOT_FOUND`；405 / `BRIDGE_METHOD_NOT_ALLOWED`；415 / `BRIDGE_CONTENT_TYPE_UNSUPPORTED` |
| 浏览器 Origin 请求 | 403 / `BRIDGE_ORIGIN_REJECTED` |
| 创建失败、持久化失败 | 500 / `BRIDGE_SESSION_CREATE_FAILED`、`BRIDGE_PERSISTENCE_FAILED` |
| 消息接收结果无法确认 | 503 / `BRIDGE_MESSAGE_OUTCOME_UNKNOWN` |
| 未知异常 | 500 / `BRIDGE_INTERNAL_ERROR` |

模型在消息接收之后失败，通过执行事件和会话状态表达，不伪造一个新的消息接收 HTTP 响应。对外使用安全的失败摘要，完整诊断仅进入服务日志，并过滤认证头、密钥和提供方请求中的敏感字段。控制日志不记录完整聊天正文。

## 5. WebSocket 事件与重连

验证脚本连接 `ws://127.0.0.1:13080/bridge/v1/events?sessionId=<验证会话UUID>`，在握手请求头中携带 Bridge Bearer。URL 中只携带会话标识。每个连接订阅一个已校验归属的会话，不提供全局广播。鉴权和会话校验在升级前完成；数据只下行，回复追问及其他命令使用 HTTP。

事件信封：

```json
{
  "version": "1",
  "streamId": "50000000-0000-4000-8000-000000000001",
  "eventSeq": 12,
  "type": "assistant.delta",
  "sessionId": "20000000-0000-4000-8000-000000000001",
  "messageId": "40000000-0000-4000-8000-000000000001",
  "runId": "60000000-0000-4000-8000-000000000001",
  "data": {"attemptId":"模型请求尝试标识","text":"我已记住"}
}
```

`streamId` 每次订阅生成，`eventSeq` 只在该流内递增，不充当原生日志位置或跨重启游标。持久事实附带 `sourceSeq`，工具事件附带 `toolCallId`；暂时没有相关消息或执行轮次的事件省略对应字段。

| 事件 | 内容与依据 |
| --- | --- |
| `session.snapshot` | 订阅建立后的状态、有效追问及已提交历史边界 |
| `message.accepted` | 消息持久接收成功的标识 |
| `assistant.started` / `assistant.delta` | 原生模型流尝试及文本增量，带 attemptId |
| `assistant.settled` | 本次模型流尝试结束及原生 outcome，不代表整轮已经完成 |
| `assistant.completed` | 已提交的完整回答及消息 ID |
| `tool.started` / `tool.completed` | 原生工具调用事实；本阶段只涉及追问工具 |
| `interaction.requested` / `interaction.resolved` | 追问结构及回答/取消后的状态 |
| `run.completed` / `run.failed` / `run.cancelled` | 以原生轮次结束事实确定的结果 |
| `session.persistence-failed` | 原生事实无法确认持久化，不冒充为已提交完成事件 |
| 关闭码 1013 / `resync-required` | 当前连接不能继续完整投递，需要重新读取状态和历史 |

不要从收到第一段文本推断新轮次，也不要以最后一段文本或 socket 关闭推断完成。同一轮可能存在多个模型请求尝试；按 attemptId 替换或丢弃失败尝试的临时文本，最终以已提交回答为准，不把重试前后的文本盲目拼接。

订阅先建立事件收集，再形成带历史边界的快照并顺序发送其后的事件，防止获取快照期间丢失完成事件。重连读取新的快照，按边界分页取持久历史，使用原生消息 ID 去重，再应用后续事件；旧连接临时文本不作为权威记录。此阶段不承诺按旧 eventSeq 重放。

单连接待发送数据超过配置上限时，不静默丢弃后假装完整；以 1013 和 `resync-required` 原因关闭连接。使用 WebSocket ping/pong 检测断开；连接清理不取消 Agent。事件适配、序列化和慢客户端失败应被隔离，不能阻塞模型循环或覆盖真实执行结果。

## 6. 生命周期、控制元数据与恢复

DSH 原生持久化是对话事件的唯一来源。插件只通过现有领域存储中的 `datascalpel_bridge` 域保存工作区绑定、创建请求到会话的映射、插件会话归属和消息接收控制状态；不复制整份历史，不另建数据库，不往会话头强塞未经声明的用户字段。

创建顺序为：验证配置与容量 → 确保目录和 Workspace → 记录创建意图及稳定会话 ID → 创建 Agent 并在 setup 挂载 Preset、监听器和追问处理器 → 持久化会话 → 关联 Workspace → 标记就绪 → 返回结果。部分失败只补全本次已记录意图；不能吞掉持久化异常或创建重名替代会话。

恢复时根据插件持久记录确认归属和配置目录，读取原生头部核对 cwd，再调用公开恢复服务。目录缺失、头部不匹配、日志版本不兼容均明确失败，不创建空历史掩盖问题，也不通过扫描目录把既有原生会话全部登记为插件会话。

第一阶段不引入空闲定时回收：活跃 Agent 保持到插件卸载或进程退出，数量由 `maxActiveSessions` 限制，超限直接报告。恢复同一会话复用已有 handle，不能在读取历史时消耗额外 Agent 容量。

卸载时先拒绝新命令并终止自己的订阅，取消自己的未完成追问和执行，再等待并释放持有的 handle，撤销监听器与路由。遵循 DSH 原生持久化刷新和 disposer 顺序，不能清空共享注册表或停止无关原生 Agent。

正常重启保留会话及工作文件。崩溃或进程被强制终止后的日志修复交给原生持久化；Bridge 显示中断事实并要求显式恢复。恢复后由用户发送新的消息继续，不对不确定的接收或执行自动重试。

当前 DSH 恢复未完成工具调用时可能补写 `interrupted-tool-result-*` 记录，使原生日志闭合；这是持久化修复，不是重新执行工具。验收同时核对没有新增模型流、用户/助手消息和有效追问。正常重启导致的取消结果沿用原生事实，强制崩溃后的修复不承诺为同一种结果。

## 7. 验证脚本与接口调用顺序

验证脚本从环境读取 `DSH_BRIDGE_URL`（默认 `http://127.0.0.1:13080/bridge/v1`）和 `DATASCALPEL_DSH_BRIDGE_TOKEN`。HTTP 使用 Node.js fetch，WebSocket 使用支持认证请求头的 Node.js 客户端；命令行参数、报告和调试输出不得显示令牌。脚本为每次创建/消息保留稳定 UUID，不通过自动重发掩盖失败。

脚本按如下请求样例链路执行：

1. `GET /capabilities` 确认版本与就绪状态；另以无凭据和错误凭据验证 401。
2. 两次 `POST /workspaces/actions/ensure {}`，核对工作区一致。
3. `POST /sessions {clientSessionId}`，重复调用验证创建复用。
4. 建立带 Authorization 的 `/events?sessionId=...` 订阅，接收 snapshot。
5. `POST /sessions/{id}/messages {clientMessageId,text}`，等待原生完整回答与轮次完成，再读取 `/messages` 核对记录。
6. 新消息要求模型用原生追问工具询问“蓝色还是绿色”。收到 `interaction.requested` 后，从真实问题结构取 ID 和选项，向 respond 接口提交答案；验证模型继续作答。若模型没有发起工具，记录该场景未通过，不把普通文字问句等同于完成原生追问验证。
7. 发出适当长度的回复请求，观测运行中状态后调用 cancel，验证最终结果；若模型已提前完成则本轮没有覆盖取消分支，需单独报告。
8. 在一轮执行中断开订阅，保持任务运行，重新连接并通过 snapshot、历史和后续事件恢复。
9. 保存会话 ID 与已完成对话中的随机标记，进入“等待部署操作者重启”步骤；脚本不自行调用 Docker。按现有 Compose 流程重启后执行 resume，再发消息询问标记，验证真实上下文恢复。

验证脚本只发送基础对话和追问，不发起 MCP、Skills、业务 API、终端或文件操作。报告记录验证使用的会话 ID、版本、结果和安全摘要，不记录完整私密对话或凭据。

### 7.1 调用示例

以下代码用于说明第 4 节接口的调用方式，完整可执行验收脚本见插件目录的 `scripts/verify.mjs`。独立 Bridge 密钥由运行环境注入；示例不加载或输出模型密钥。`clientSessionId` 和 `clientMessageId` 应由正式验证脚本写入本地检查点，重试时读取原值。

```js
import { randomUUID } from 'node:crypto';
import WebSocket from 'ws';

const base = process.env.DSH_BRIDGE_URL
  ?? 'http://127.0.0.1:13080/bridge/v1';
const token = process.env.DATASCALPEL_DSH_BRIDGE_TOKEN;
if (!token) throw new Error('缺少 Bridge 访问凭据');
const headers = { Authorization: `Bearer ${token}` };

async function request(method, path, body) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { ...headers, ...(body === undefined ? {} : {
      'Content-Type': 'application/json',
    }) },
    body: body === undefined ? undefined : JSON.stringify(body),
    redirect: 'error',
  });
  const result = await response.json();
  if (!response.ok) {
    // 只输出状态和稳定错误码，不打印请求配置、认证头或响应正文。
    throw new Error(`Bridge ${response.status}: ${result.code ?? 'UNKNOWN'}`);
  }
  return result;
}

await request('GET', '/capabilities');
await request('POST', '/workspaces/actions/ensure', {});
const session = await request('POST', '/sessions', {
  clientSessionId: randomUUID(),
});
const path = `/sessions/${session.sessionId}`;
const eventsUrl = new URL(`${base}/events`);
eventsUrl.protocol = eventsUrl.protocol === 'https:' ? 'wss:' : 'ws:';
eventsUrl.searchParams.set('sessionId', session.sessionId);
const socket = new WebSocket(eventsUrl, { headers });
// 正式脚本须先注册事件监听并等到 session.snapshot，再发送消息。
// 监听器处理断线、慢消费和事件顺序，不能只等待 WebSocket open。

// 收到快照后：
// await request('POST', `${path}/messages`, {
//   clientMessageId: randomUUID(), text: '请记住标记：青石',
// });
// 收到 run.completed 后：
// await request('GET', `${path}/messages?offset=0&limit=50`);
// await request('GET', path);
// await request('GET', '/sessions?offset=0&limit=20');
// 收到真实 interaction.requested 后，使用它的 ID、问题和选项构造 answers：
// await request('POST', `${path}/interactions/${interactionId}/actions/respond`, { answers });
// 需要取消时：await request('POST', `${path}/actions/cancel`, {});
// DSH 重启后：await request('POST', `${path}/actions/resume`, {});
// 断开订阅只调用 socket.close()，不自动发送取消。
```

断线和请求超时不等于命令未被接收。正式脚本先读取会话状态及持久历史，再使用已保存的客户端 ID 核对接收情况，不自动生成新 ID 重发。重启检查点只保存非秘密的会话 ID、客户端 ID 和验证标记。

## 8. 开发顺序与验收清单

### 8.1 第一阶段内部实施顺序

1. 建立仓库内插件包、构建和锁文件；接入公开运行时依赖与独立验证 Preset。
2. 实现配置、Bearer 边界、能力探测、固定工作区及插件归属存储。
3. 实现 Agent 创建/恢复、消息接收、历史读取及取消，确认原生持久化和稳定消息 ID。
4. 实现模型流和持久事实适配、WebSocket、追问回复及断线处理。
5. 接入现有 Docker 构建，补充配置样例、脚本和安装说明，按约定记录验证结果。

### 8.2 验收场景

| 场景 | 预期 |
| --- | --- |
| 加载与边界 | 不修改核心即可加载；版本准确；HTTP/WS 无凭据和错误凭据拒绝；密钥不在 URL 或日志 |
| 工作区初始化 | 重复和并发 ensure 得到同一目录及记录；既有文件保留；无效配置不另选路径 |
| 会话创建与归属 | 重复创建复用；只能读取和操作本插件会话；原生会话 ID 返回 404 |
| 基础对话 | 真实模型完成文本回复，增量、最终回答与持久历史一致 |
| 原生追问 | 收到结构化追问，选项或自由文本回复后继续；重复/取消后回复拒绝 |
| 消息去重 | 相同 ID 和文本不重复执行；不同文本冲突；忙时不新增普通消息 |
| 执行取消 | 真实结束状态准确；追问随取消失效；取消不误报已撤销外部动作 |
| 断线与慢连接 | 断线不终止任务；重连不漏持久结果；超出缓冲要求重新同步 |
| 重启与恢复 | 工作区、归属和对话保留；显式恢复后记得上下文；中断任务不自动重放 |
| 失败语义 | 缺模型配置、创建/持久化失败、容量已满有明确错误；不确定接收不自动重发 |
| 原生环境保留 | 原生页面、默认工作 Preset、模型配置、MCP、Skills 和历史工作文件仍可使用 |

对消息竞态、归属检查、持久化失败、重连顺序和过期回复可使用针对性自动化测试，不靠真实模型碰运气覆盖边界。测试与真实验收是否执行遵循[根测试政策](../../AGENTS.md#测试与验证暂时禁用)及实施任务的明确要求；本规格不恢复全工程强制测试。模拟通过与真实模型通过分别记录。

实际运行验收复用现有 DSH Docker 环境，不另起 DataScalpel 前后端或数据库。第一阶段通常不需要启动 DataScalpel；如确有需要，只能使用根目录 `./start-local-dev.sh`。重启 DSH 验证按现有 Compose 运维流程执行，保留数据卷和用户配置，结束后保持服务可用。

### 8.3 开发交付物与后续设计输入

开发完成应交付仓库内源码、构建安装说明、配置与 Preset 样例、验证脚本、实际验证记录和已知限制。版本信息及引用路径需与交付源码一致，不填写假定通过的验收结果。

验证记录至少区分“通过”“失败”“未执行”，附失败原因与影响。应列出实际使用的公开扩展点，以及遇到的运行时共享状态、恢复和事件限制，供下一阶段设计使用；不得把这些观察直接升级为多用户方案或宣称用户隔离已经完成。

第一阶段完成后返回[路线图](dsh-integration-roadmap.md)，基于实际结果再设计第二阶段。
