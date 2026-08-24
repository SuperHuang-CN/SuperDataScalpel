# DataScalpel AI 助手 V1

## 1. 定位与边界

AI 助手是 `data-scalpel-business` 内独立的 `assistant` 业务包，前端对应
`data-scalpel-ui/src/modules/assistant`。它仍属于现有模块化单体，不增加 Maven 模块、Spring AI、
LangChain、向量数据库、事件总线或前端全局 Store。

当前 V1 提供四类能力：

- 全局界面协助：系统问答、白名单页面导航、主侧栏展开或收起、目录导出下载。
- 目录协助：查询六类目录，以及生成可审阅的新增、修改、移动、排序和空叶子删除计划。
- 数据源协助：查询安全基本信息、定位数据源，以及准备新建、修改和已保存连接测试的客户端动作。
- 任务 Canvas 协助：查询任务和安全资源 Schema，生成批任务完整 Canvas 提案，并交付到原编辑器作为未保存草稿。

任务工具只支持 `SPARK_CANVAS`，不创建、保存、发布、调度或执行任务。模型只作为 Canvas 资源
查询，不提供模型业务写入。LLM 不能直接访问数据库、调用 DataScalpel REST 接口、执行目录写入、
保存数据源或保存 Canvas；数据源连接参数、凭据和任务计算引擎始终由用户在原页面维护。

## 2. 依赖与执行方向

```text
AppShell / AssistantDrawer
        │
        ▼
AssistantResource ──► AssistantConversationService ──► LlmGateway
        │                         │
        │                         ├──► AssistantToolCatalog（显式白名单）
        │                         ├──► DirectoryService（只读查询）
        │                         ├──► DataSourceService（安全字段查询）
        │                         ├──► DirectoryChangePlanService（仅保存计划）
        │                         └──► TaskCanvasProposalService（仅保存提案）
        │
        └── 用户确认 ──► DirectoryPlanExecutor ──► 现有 DirectoryService CRUD
```

依赖固定由 `assistant` 指向现有目录、数据源、模型、文件数据集和任务能力。原业务实体和 REST API 不增加 Agent
字段、事件或反向依赖。前端只给现有数据源 Drawer 增加可选安全草稿入口，最终测试、校验和保存
仍复用原页面。未来接入模型或任务工具时，也在 Assistant 内增加显式处理器。

## 3. 模型注册与薄网关

管理员在“系统管理 / AI 模型”维护全局模型。`LlmModelConfiguration` 保存显示名称、协议、
Base URL、模型标识、加密 API Key、额外请求参数、启用/默认状态和测试状态。协议第一版固定为
`OPENAI_COMPATIBLE`。

API Key 使用独立配置 `data-scalpel.assistant.credential-key` 进行 AES-GCM 加密，不能复用数据源
或计算引擎密钥。无鉴权内网模型可以不填写 Key。列表和详情只返回 `apiKeyConfigured`，不会
返回密文或明文。

额外请求参数使用 JSON 对象配置，并合并到 OpenAI Compatible Chat Completions 请求顶层。它用于
承载 `enable_thinking`、`top_p` 等模型或厂商扩展参数，不建立厂商类型或独立网关。例如千问关闭
思考模式：

```json
{
  "enable_thinking": false
}
```

`model`、`messages`、`tools`、`tool_choice`、`stream`、`temperature`、`max_tokens` 和 `n` 等核心
字段由系统维护，不允许通过额外参数覆盖；额外参数也不得保存 API Key、Token 或授权信息。修改
额外参数与修改连接信息相同，会自动停用模型、重置测试状态，并要求重新执行 Tool Calling 测试。

新建模型固定为 `UNTESTED + disabled`。测试会要求远端返回指定函数调用：

- 正确返回指定 Tool Call：`AVAILABLE`；
- 服务不可达或超时：`UNAVAILABLE`；
- 可以对话但没有按要求调用工具：`INCOMPATIBLE`。

只有 `AVAILABLE` 模型可以启用。第一个启用模型成为默认模型；停用默认模型后不自动选择
替代项。修改 Base URL、模型标识或 API Key 会自动停用并重置测试状态。模型被任何
`AssistantRun` 使用后不再物理删除，只能停用。

`LlmGateway` 只接收运行时模型、消息和工具定义，同步调用 `/chat/completions`，返回文本、
usage 和 Tool Calls。它不处理权限、业务事务、计划和工具执行。

## 4. 会话、运行与审计

持久化对象均使用标量 UUID 引用，不建立 JPA Entity 关联：

| 对象 | 用途 |
| --- | --- |
| `AssistantSession` | 用户私有会话、标题、选中模型和归档状态 |
| `AssistantMessage` | 用户与最终助手消息 |
| `AssistantRun` | 单轮实际模型、状态、开始结束时间和安全失败摘要 |
| `AssistantToolInvocation` | 工具名、风险、裁剪后的参数和结果、执行状态 |
| `AssistantChangeSet` | 强类型目录计划或任务 Canvas 提案、确认人与结果 |

所有大文本使用 `LONG32VARCHAR`。系统不保存 System Prompt、完整远端请求、API Key、SQL、
凭据、样例数据或页面表单草稿。模型上下文只组装最近 20 条用户/助手消息。

会话按当前 JWT 用户名隔离，列表查询通过固定 `ownerUsername` 条件与客户端 Search DSL 做
`AND`。发送消息前会悲观锁定会话；一个会话同时只能存在一个 `RUNNING`。超过两分钟的旧
运行会在下一次请求前标记失败，避免永久锁定。

单轮流程使用短事务：先保存用户消息和 Run；在事务外调用 LLM；每次工具审计和计划保存各自
使用短事务；最后保存助手消息和完成状态。模型调用超时返回 504，其他远端失败返回 502，失败
Run 仍保留。

## 5. 工具白名单与权限

工具由 `AssistantToolCatalog` 以代码显式注册，不使用反射、Spring 扫描或运行时插件：

- `ui_navigate`
- `ui_set_app_sidebar`
- `directory_list_scopes`
- `directory_list_roots`
- `directory_list_children`
- `directory_search`
- `directory_get`
- `directory_prepare_export`
- `directory_propose_changes`
- `datasource_list_types`
- `datasource_search`
- `datasource_get`
- `datasource_prepare_create`
- `datasource_prepare_update`
- `datasource_prepare_test`
- `task_search`
- `task_get`
- `task_get_canvas_summary`
- `task_canvas_model_search`
- `task_canvas_model_schema`
- `task_canvas_file_dataset_search`
- `task_canvas_file_table_search`
- `task_canvas_file_table_schema`
- `task_canvas_jdbc_tables`
- `task_canvas_jdbc_table_schema`
- `task_propose_canvas`

页面导航只接受稳定 `pageKey`，前后端分别维护白名单并再次检查页面权限。前端只执行明确枚举
动作，不接受任意 URL、Selector、JavaScript 或 DOM 指令。除原三种界面动作外，数据源仅增加
`OPEN_DATA_SOURCE_CREATE`、`OPEN_DATA_SOURCE_EDIT` 和 `CONFIRM_DATA_SOURCE_TEST`。

目录查询要求 `directory.view`，计划生成与确认执行要求 `directory.manage`。工具只会按当前
JWT authorities 提供给模型，执行时仍会二次校验。目录名称、说明、路径和工具结果都在 System
Prompt 中明确标记为不可信业务数据，不能覆盖系统指令或扩大权限。

数据源类型、搜索和详情要求 `datasource.view`；创建草稿、修改草稿和测试准备分别额外要求
`datasource.create`、`datasource.update` 和 `datasource.test`。安全查询结果只包含 ID、编码、名称、
类型、连接类别、用途、启用状态、说明、目录归属和时间，不返回主机、端口、数据库、Schema、
用户名、Kafka 地址、S3 配置、HTTP URL/Header/Auth、连接选项、凭据、SQL、元数据、样例数据和
连接测试诊断。按目录搜索和返回目录路径还要求 `directory.view`。

任务查询要求 `task.view`；修改已有 Canvas 提案额外要求 `task.update`，新任务草稿还要求
`task.create`。模型、文件和 JDBC 资源工具分别要求 `model.view`、`filedataset.view` 以及
`datasource.view + datasource.metadata`。Schema 工具最多向模型返回 200 个逻辑字段，工具审计只保存
资源 ID、字段数量、Schema 指纹和截断状态，不重复保存完整 Schema。JDBC 只披露已选数据源名称、
表名和平台逻辑字段；连接地址、数据库连接配置、账号、凭据、原生类型、索引细节、SQL、样例行和
物理文件位置不会进入模型上下文、工具审计或助手历史。

每轮最多 4 次模型交互和 8 次工具调用；目录搜索最多返回 50 项，直属子目录最多返回 100 项，
计划最多 100 个操作且只能处理一个 `DirectoryScope`。

## 6. 目录计划与确认

`directory_propose_changes` 没有写目录能力，只把模型参数交给服务端规范化：

- `CREATE` 使用本地引用连接同一计划中的新父目录；
- `UPDATE` 必须提供完整目标父目录、名称、排序和说明；
- `DELETE` 只接受真实目录 ID。

服务端从 `DirectoryService` 读取真实目录，补齐路径、资源数量和 `expectedUpdatedAt`，校验作用域、
父子关系、同级重名、循环移动、重复操作和创建拓扑。LLM 提供的路径、资源数量或时间戳不作为
可信依据。同一会话的新待确认计划会把旧计划标记为 `SUPERSEDED`。

确认接口不再调用 LLM。执行前重新检查目录时间戳、最终目录图和业务引用；变化后的计划标记
`STALE`。整个计划在一个短事务中通过现有 `DirectoryService.create/update/delete` 执行，失败
整体回滚。只允许删除空叶子目录，不支持递归删除、跨作用域移动、合并或自动迁移资源。已经
`APPLIED` 的计划重复确认时直接返回原执行结果。

## 7. 接口与前端

模型管理接口位于 `/api/v1/system/llm-models/**`，查看复用
`system.configuration.view`，维护复用 `system.configuration.update`。

会话与计划接口位于 `/api/v1/assistant/**`，全部要求登录。会话只允许创建者访问，目录确认还
要求 `directory.manage`。所有更新和命令继续使用 `POST .../actions/*`，错误使用统一 RFC 9457
Problem Details。

`AppShell` 顶部提供 AI 助手按钮，右侧 460px Drawer 在路由切换后保持打开状态和当前会话。
Drawer 显示模型选择、会话历史、消息、目录计划和执行结果。目录执行成功后只通过目录模块公开
的 `invalidateDirectoryTree` 刷新对应作用域，不改造任何现有目录页面。

数据源准备工具不新增 REST 接口或持久化计划，只返回安全客户端动作。新建动作通过一次性路由
状态打开 `/datasource` 的现有 Drawer；修改动作先进入 `/datasource/{id}`，加载最新完整配置后仅
覆盖名称、目录、用途、启用状态和说明，编码、类型、连接参数与凭据保持不变。路由状态消费后
立即清除，刷新不会重复打开。连接测试动作先由用户二次确认，再调用现有已保存数据源测试接口；
测试结果只在前端 Modal 展示，不进入助手消息、模型上下文或工具审计。

### 7.1 任务 Canvas 提案

`task_propose_canvas` 接收 Assistant 内部强类型 `TaskCanvasPlan`，不接受 Canvas JSON、节点 UUID、边、
坐标或协议版本。第一版允许 1～2 个输入、最多 1 个 Join、1 个输出和最多 20 个最终节点，节点范围为：

- 输入：`MODEL_INPUT`、`JDBC_INPUT`、`FILE_DATASET_INPUT`；
- 处理：`FILTER`、`SELECT_COLUMNS`、`RENAME`、`TYPE_CAST`、`NULL_HANDLING`、`DEDUPLICATE`、`JOIN`、`AGGREGATE`；
- 输出：`MODEL_OUTPUT`、`JDBC_OUTPUT`。

服务端使用真实资源解析 UUID 和逻辑 Schema，校验资源状态、字段引用和拓扑，再由确定性 Builder 生成
Canvas 3.0 节点、边、逻辑表名和分层布局。输入逻辑表重名时 Builder 插入只调整表名的 `RENAME`。
最终定义仍通过现有 `CanvasDefinitionValidator`；Builder 不调用 Task Engine，也不复制 Spark 类型兼容
或 Schema 传播规则。缺少写入模式、字段映射等决策时必须写入 `needsUserInput`，由用户在 Inspector 中补齐。

提案保存为 `TASK_CANVAS` ChangeSet。接受接口为
`POST /api/v1/assistant/change-sets/{id}/actions/accept-task-canvas`，只把状态改为 `APPLIED` 并返回
`CLIENT_DRAFT` 定义，不调用任务 Canvas 保存接口。接受前重新检查任务类型、状态、定义版本和全部资源
Schema 指纹；变化后标记 `STALE`，JDBC 临时读取失败时保持 `PENDING`。

已有任务直接进入 `/task/{id}/definition`。新任务先通过一次性路由状态打开原 `TaskDrawer`，类型固定
`SPARK_CANVAS`，计算引擎必须由用户选择；创建后再进入定义编辑器。编辑器存在 Inspector 草稿或未保存
修改时禁止应用，非空 Canvas 需要确认完整替换。应用只调用 `CanvasDesigner.replaceDefinition` 替换内存
画布并产生 dirty，Task Engine 按原流程自动编译；只有用户点击“保存定义”才会落库。

## 8. 部署配置

| 配置 | 环境变量 | 默认值 |
| --- | --- | --- |
| `credential-key` | `DATASCALPEL_ASSISTANT_CREDENTIAL_KEY` | 空；只在保存带 Key 模型时要求有效 |
| `connect-timeout` | `DATASCALPEL_ASSISTANT_CONNECT_TIMEOUT` | `5s` |
| `request-timeout` | `DATASCALPEL_ASSISTANT_REQUEST_TIMEOUT` | `90s` |
| `stale-run-timeout` | `DATASCALPEL_ASSISTANT_STALE_RUN_TIMEOUT` | `2m` |
| `max-output-tokens` | `DATASCALPEL_ASSISTANT_MAX_OUTPUT_TOKENS` | `4096` |
| `temperature` | `DATASCALPEL_ASSISTANT_TEMPERATURE` | `0.1` |

凭据密钥必须是包含 16、24 或 32 字节内容的 Base64 字符串，并在部署生命周期内稳定保存。
丢失或更换密钥后，已登记模型的 API Key 无法解密，需要恢复原密钥或重新填写 Key 并测试。

## 9. 第一版明确不做

不提供流式输出、纯 JSON 降级、多模型路由、个人模型、RAG、知识库、多 Agent、长期记忆摘要、
费用配额、定时自主任务，也不接入模型、任务、数据源或服务的直接业务写工具。数据源第一版不
支持删除、修改类型或连接配置，也不提供通用表结构查询、数据预览、SQL和依赖影响；JDBC Schema
仅在用户明确选择 Canvas 资源后以安全逻辑字段投影提供。任务第一版不支持实时 Canvas、任意 SQL、
多输出、节点 Patch、编译错误自动修复、发布、调度或运行。
