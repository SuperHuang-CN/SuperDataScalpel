# 本体 Agent 接入定案建议与能力映射

状态：设计评审稿，未实施。日期：2026-10-02。配套：[总体方案](ontology-agent-layer-datasource-model-v1.md)。

本轮完成固定版本包契约与当前源码的静态核对，给出接入路径、动作映射和改动清单。没有启动服务、执行业务接口、安装依赖或修改实现代码。任何 Java 新增或修改均须提前取得用户确认。

## 1 建议结论

1. 首批保留分层、数据源、模型的完整管理操作，复用已有业务 REST。
2. 运行时复用 DSH 原生会话和 userQuestions 服务，包括 plan-review；产品界面建议以现有 DataScalpel 聊天实现为基础，在新模块有限复制并适配，不采用裁剪整套原生 Web 的路线。两种复用不能混称；取舍见第 2.1 节。
3. 首选不新增 Java：新 DSH 插件提供专属 HTTP 接入，浏览器沿用现有登录 JWT；插件借助现有只读身份接口确认用户，再在服务端调用业务 REST。
4. 该路径需要少量前端代理/部署路由配置，而且依赖现有 DSH capabilities 路由的身份校验。它是有明确代价的复用，不是“什么都不用改”。
5. 若不接受第 4 项耦合，备选仅新增一个只读 Java 身份接口；不预建 Java 本体引擎、通用执行器或整套方案确认转发。未经用户单独确认不实施这个备选。

这里的“不依赖 MCP”指新 Agent 不使用 MCP 的搜索、描述、调用、目录及令牌执行业务。原系统仍保留原 MCP 功能和后台维护，本次不删除它们。

## 2 固定版本核对结果

项目固定 `@deepseek-ai/dsh@0.1.5-rc.1`。本轮读取同版本发布包的 README、类型声明及 workspace 实现；六个包的 tarball SHA-512 均与 `deploy/dsh/package-lock.json` 的 integrity 相同。只在系统临时目录展开静态材料，没有安装或执行包代码。结论不再依赖另一个工程的 rc.2 或官方 master。

| 包/现有代码 | 核对结果 | 使用方式与限制 |
| --- | --- | --- |
| dsh-user-questions | `ctx.userQuestions.ask()` 等待结构化回答；问题支持 detail、options、multiSelect、intent | 能等待用户；业务操作与修订绑定由新插件维护 |
| 同上 plan-review | `intent: {kind: 'plan-review', approve: '选项标签'}`；必须有 detail 且批准标签对应真实选项 | 可展示固定方案并确定批准标签；不以选项位置判断批准 |
| dsh-client-ui-user-questions | 原生 Web 有专门 PlanReviewPanel，使用相同回答格式 | 可以在原生 DSH Web 环境使用；不代表已经在 DataScalpel React 页直接接入 |
| dsh-client-ui-approval / dsh-user-approval | 支持一次 allowed-once；请求需位于活跃 turn，策略 never 会拒绝请求 | 用于工具权限，不作为持久业务审批库；本方案优先选 userQuestions 的业务方案交互 |
| dsh-client-ui-renderer | 本身使用 React，但依赖 Cordis 客户端、Remote、Session、Slot 注册体系 | 障碍不是 React 不兼容，而是宿主服务依赖；本期不为搬一个卡片引入整个客户端宿主 |
| dsh-workspace | create 对同一 realpath 返回原工作区；resolveByPath 可只读查找 | 保留 DSH_HOME、Registry 和用户根路径具备契约依据；部署时仍需核对实际卷和旧记录 |
| 当前 Interactions | 每会话一个待答问题，使用 interactionId；校验选项，处理取消 | 可借鉴协议和处理方式；内存 pending 不等于可恢复的业务待办 |
| 当前 Java respond DTO | answers 下有 id、selected、custom | 传递原生选择无需增加字段；不能把密码和任意业务 JSON塞入 custom 来规避表单设计 |

原生 userQuestions 明确只提供选择与自由文本词汇，不提供字段表格、文件选择及结构化差异确认。会话、等待和回答机制复用；需要编辑的业务数据仍使用 Ant Design Form、字段表格及现有 BusinessSecretInput。

### 2.1 原生 Web 裁剪评估结论

用户希望复用原生 Web 来节省前端开发，同时不显示完整工作区及其会话树。这个产品要求合理；技术上也不必显示完整左栏。但是，当前固定版本提供的是客户端插件体系，没有核实到一个可直接嵌入现有应用的独立聊天组件或 `hideSidebar` 配置。本期建议选择现有 DataScalpel 聊天基线，不选择原生 Web 裁剪。以下是静态代码与包契约结论，不是已完成的裁剪页面演示或工时测量。

| 核对项 | 固定版本依据 | 对本次接入的影响 |
| --- | --- | --- |
| 工作区与左栏 | `dsh-client-ui-layout` 的 README、客户端类型及实现：默认三列布局，收起后仍有 56px 导航栏；`sidebar` 插槽可以替换导航内容 | 可以扩展外观；替换内容不自动移除布局列。无左栏聊天需要额外外壳适配，不能把“收起”当成完成裁剪 |
| 会话组件 | `dsh-client-ui-conversation/package.json` 及 `/client` 导出：依赖 layout、renderer、session、workspace、settings 等客户端服务 | 不是传入接口地址就能工作的 React 聊天组件。隐藏工作区 UI 后仍需满足相关服务依赖，不能简单删除 workspace 插件 |
| 登录 | `dsh-client-connection/README.md`：启动 token 在根路径换取 Cookie；原生 HTTP 通道不接受 Authorization-header token；Host/Origin 检查不建立业务用户身份 | 现有 DataScalpel JWT 不能直接驱动原生通道；共享 DSH 启动登录也不能代替用户归属。需要另做身份与通道适配 |
| 通信 | 同包及 `dsh-api-remotes`：原生 Remote 单次 HTTP 调用与 `/api/remote.mux` WebSocket 流 | 第 3 节拟议的 REST/事件流接口不是其直接替代品。若选择原生 UI，必须承接其 Remote 契约，或改造客户端连接层 |
| 服务端范围 | `dsh-api-session-controller` 的 README、类型：包含会话、检索、文件引用及媒体；原生媒体接口接受文件路径。未提供现成的 DataScalpel 当前用户映射 | 不能只隐藏左栏；会话列表、历史、流、回答、上传及文件读取都要绑定实际业务用户。原生默认实例授权不能直接视为业务用户授权 |
| 本体专用交互 | userQuestions 提供选择和文本，不提供完整业务编辑表单 | 字段编辑、连接秘密、操作差异和持久回执两条路线都要补；原生消息 UI 不会免除这些工作 |

公开的插槽及与传输方式解耦的通道接口提供了定制可能，所以结论不是“原生 Web 做不到”，也不是“必须修改 DSH 核心”。结论是：在旧 Agent 不改、共享 DSH、沿用现有登录及单 React 产品入口的约束下，原生 Web 的接入和升级适配成本削弱了其省开发的优势。

三条路线的比较如下。身份检查、会话归属和业务确认是共同工作，不把它们全部算成原生 Web 独有成本。

| 路线 | 可以省掉 | 额外成本 | 本期建议 |
| --- | --- | --- | --- |
| 原生 Web 定制外壳与接入 | 原生消息渲染、输入、会话交互的较多实现 | 客户端宿主与布局适配、原生 Remote/登录接入、各原生入口的用户范围约束；仍需业务表单 | 不选为产品主入口；保留开发参考 |
| 在新模块采用现有 DataScalpel 聊天基线 | 已有抽屉布局、消息展示、附件交互、历史和问答处理的设计及部分代码 | 新 API/事件适配、本体业务卡；复制部分代码后需分别维护 | 推荐，符合旧实现不改的当前要求 |
| 抽共享聊天组件供新旧两套使用 | 可减少重复维护 | 必须改旧模块的导入、属性或传输注入，并验证旧行为 | 本期不采用；若将来允许调整旧实现，再讨论 |

当前 `modules/dsh/index.ts` 仅公开 `DshDrawer`；Drawer、会话 Hook、附件及 API 与旧链路有直接依赖。因此第二条路线是**有限复制与适配**，不是不改代码就能共享整个组件。新模块不得越过 index 导入旧模块私有文件，也不复制旧 MCP 接入。展示代码按实际依赖取用，事件 Hook 和 API 按新接入契约适配；公共 HTTP、反馈、格式化等继续引用 shared。后续展示缺陷可能需要同步修复两处，这是保留旧模块原样的代价。

产品形态建议：一个“AI 助手”抽屉，打开后进入当前对话；保留“新对话”和按需打开的“历史”入口，历史只列当前用户的本体会话。不展示 DSH 工作区树、全实例会话列表及原生管理设置。分层、数据源、模型仍是同一个助手的三个业务范围，不新增三个聊天入口。部署选择原有或本体实现，用户不选择。

本轮补充读取的 layout、conversation、workspace、connection、remotes 等包均为 `0.1.5-rc.1`；上述依据来自发布包，而不是 master 文档。没有运行原生 UI、修改依赖或实现页面。前述首选 Java 零新增路径仍需在实施接入阶段验证，不能由界面选型推导出所有接入问题已经解决。

## 3 首选接入：Java 零新增，TS 插件承担专属接入

### 3.1 连接方向

```text
DataScalpel 单一助手入口
  └─ 部署为 ontology 时加载新 React 模块
       └─ 同源 /api/ontology-agent/v1/* → 反向代理 → 新 DSH 插件
            ├─ 原生 sessions / userQuestions / workspace / persistence
            ├─ 本体、能力处理器、业务方案与确认记录
            └─ 固定 Admin 地址的 REST 客户端
                 ├─ GET /api/v1/auth/me
                 ├─ GET /api/v1/dsh/capabilities
                 └─ 分层、数据源、模型现有接口
```

新路径是方案名。前端继续调用 shared/api 的 requestJson、requestBlob 和流式基础设施，路径传 `/ontology-agent/v1/...`，不另建带令牌存储的浏览器客户端。反向代理先匹配该前缀，再处理原有 `/api`；开发代理同样显式匹配。业务 REST 始终指向固定 Admin 内网地址，不能被请求参数重定向回新插件或任意主机。

新的 HTTP 路由属于同一个 DSH 插件，不新增进程或微服务；旧 `/api/v1/dsh`、旧插件及 UI 源码不改。切回 existing 时移除新路由覆盖、加载原插件和原入口，保留两套数据。

### 3.2 如何确定真实用户

浏览器只在 Authorization 头提供既有登录 JWT。插件不持有 JWT 签名密钥，也不自行认定解码出的 UUID 已通过认证。

1. 用同一个 Bearer 请求 Admin `/api/v1/auth/me`。成功后取其返回的 userId、角色和权限快照；无合法 UUID 则要求重新登录。
2. 用同一个 Bearer 请求 `/api/v1/dsh/capabilities`。源码中 `DshResource.owner()` 会先调用 `DshIdentityService.require()`，检查 UUID、JWT 到期及数据库用户当前启用状态，再进入 capabilities 查询。
3. 两次均成功，且 DSH 返回 enabled/ready 与本体实现标识匹配，才建立内存身份上下文。JWT 的 exp 在 Admin 验证成功后才作为本地到期上界使用；不允许客户端另外提交 userId 覆盖身份。
4. 每次业务写入前重新经过该检查；活跃上下文按约 5 秒检查、10 秒失效的既有基线续期，JWT 到期优先失效。检查失败拒绝开始下一项操作，不撤销已发出的外部动作。

`/auth/me` 只返回 JWT 权限快照，不重新读取最新角色授权。普通业务 REST 也使用该快照；本方案保持这个现行语义，不能宣称角色权限变更后立刻得到最新权限。用户停用/删除则借助第 2 步与短租约阻止后续操作。

### 3.3 避免循环调用和旧凭据预配

新插件提供内部 `/bridge/v3/capabilities` 兼容响应，只做内部密钥认证和本地依赖就绪检查，不反向调用 Admin 身份接口。链路是“用户请求 → Admin capabilities → 插件内部 capabilities → 返回”，不会再次进入用户身份检查。

新会话不调用旧 `DshService.ensure()`，否则仍会执行 `bindings.prepare()`、签发/预配旧托管凭据。新插件在身份核验后自行使用公开 WorkspaceRegistry 和原生会话服务，采用相同 `/workspace/datascalpel-users/{UUID}` 根路径、独立控制存储域。既有 Java workspaceId 绑定保留不改，新工作区的相同目录复用由 DSH Registry 契约保证；冲突时停止接入并报告，不覆盖旧绑定。

现有 Admin 定时协调仍会调用 `/bridge/v3/active-users` 和 `/bridge/v3/actions/renew-leases`。新插件需兼容这些内部请求，明确它们只对应旧 v3 托管域；该域在本体部署中为空。新 JWT 上下文由自身有效期及第 3.2 节检查管理，不能让旧续租消息延长一个过期 JWT。原有托管令牌后台维护可能继续运行，本轮不修改它。

### 3.4 凭据和业务确认

登录 JWT 只驻留受信任的插件内存，不写入持久操作记录、聊天、工具参数、日志或 URL。以用户 UUID 区分业务归属，以登录上下文区分不同令牌，不能因同用户另一浏览器登录就替已确认操作静默换令牌。重启或过期后重新提供有效登录，旧 EXECUTING 保留 UNKNOWN，不自动恢复提交。

业务 API 最终继续校验权限、参数和业务规则。插件通过有限的固定处理器调用，模型不能指定 URL、header 或身份。浏览器确认来自已认证的专属交互路由，模型侧没有 respond/confirm 工具。

数据源密码通过专用业务表单提交到插件敏感输入接口，仅在内存保留并绑定操作修订；不复用原生 custom 字段，不写入原生问题及答案历史。表单修改会使已展示确认失效。执行器固定调用业务地址，不把登录凭据用于回调自身确认接口。

### 3.5 这条路径的代价及退出条件

这是一条可从源码和公开包契约推导出的候选路径，尚未进行运行联调。它依赖旧 capabilities 的“先验用户，再查健康”行为，还需保留现有 DSH 启用、内部 Bridge 密钥及相关就绪配置；不是专门设计的身份探测契约。

如果这些依赖不接受，或运行核对发现 capabilities/原生 Web 中间件不允许该组合，应停止该路径并评审第 4 节，不悄悄取消用户启用检查、放宽认证、发送凭据到聊天或改用 MCP。无需为验证该候选而改 Java。

## 4 Java 备选清单：仅在用户提前确认后

建议的唯一新增业务端能力是只读 `GET /api/v1/ontology-agent/identity`，接收已有登录 JWT，复用现有登录身份检查，返回稳定 userId、expiresAt 及明确标注为 JWT 快照的 authorities。它代替上述两个探测调用，不签发第二套令牌、不保存会话、不接收 API 地址、不执行业务操作。

| 候选文件 | 用途 | 当前授权状态 |
| --- | --- | --- |
| Business `ontologyagent/web/resource/OntologyAgentIdentityResource.java` | 调用现有 DshIdentityService.require，返回身份；明确拒绝非普通登录身份 | 未授权实施 |
| Business `ontologyagent/web/response/OntologyAgentIdentityResponse.java` | 输出 UUID、到期时间及权限快照说明，带中文 OpenAPI | 未授权实施 |

不需要提前建立 ontologyagent/service、通用代理、方案库、业务封装或新的 SecurityFilterChain。若实际落地暴露出别的 Java 缺口，须重新列出证据、文件和替代方案，再请求确认。当前首选方案不包含这两个文件。

## 5 操作方案如何复用原生确认

建议将能力区分为读能力和 prepare 能力。prepare 创建持久方案及结构化预览，不直接执行业务写入。插件可以在该工具的当前 turn 中调用 userQuestions.ask，或者在用户回到未完成方案时发起新的等待；不重放已结束的原生等待对象。

```text
准备方案并持久化 READY
  → 插件从该修订渲染说明和结构化业务卡
  → userQuestions.ask(plan-review)
  → 用户选择“确认执行”或“返回修改”
  → 固定处理器验证真实回答及 operationId/revision/hash
  → 重新核验身份、依赖和实际目标
  → 持久化 EXECUTING → 调用既有 REST → 回查 → 写回执
```

原生 questionId 与插件生成的 interactionId 都关联到服务端方案修订；答案只有精确的批准选项才允许进入执行，custom 中的“同意”、其他选项、取消和过期都不能授权。方案被修改则撤销旧等待，重新渲染和询问；旧答案返回失效。

业务卡通过专属方案 GET 读取类型化数据，不能从模型输出 Markdown 反解析执行参数。简单操作原生 detail 足够；字段编辑和差异表由新前端组件完成，但最终确认仍沿用同一原生问题/回答词汇。

建议 HTTP 接入只在新插件中提供：会话/消息/事件、pending interactions/respond、操作方案 get/update/review/cancel、敏感输入和附件。没有模型可调用的 commit 工具；review 只是重新发起针对当前修订的人工等待。HTTP 路径和输入固定，不提供任意后端转发。

原生 waiting Promise 只属于当前运行态；持久操作记录才是跨重启依据。READY 可重新展示并重新确认，EXECUTING 一律先核对结果。物理变更 PARTIAL 不重新发起同一执行；创建/发布等出现外部部分成功也保留具体副作用。

## 6 最小本体定义

实例实时取 REST，不建立另一份业务主库。定义采用版本化 YAML；执行逻辑使用显式 TypeScript 处理器，不引入规则解释器。

| 对象 | 标识 | 状态/关键属性 | 关系与权威来源 |
| --- | --- | --- | --- |
| WarehouseLayer | UUID | code、name、enabled、前缀、输入策略、允许输入 UUID 集合 | 模型可选所属分层；允许输入仅规划；分层 API |
| DataSource | UUID | code、type、purposes、enabled、目录、非敏感连接摘要 | 同一连接可多用途；与模型、任务、服务的引用来自对应查询 |
| PhysicalTable / PhysicalColumn | 数据源 UUID + 精确 Catalog/Schema/表及列标识 | 实时结构、可达性、观察时间 | 查询元数据；存在与否不能由模型定位字段推定 |
| DataModel / ModelField | UUID | MANAGED/EXTERNAL、DRAFT/PUBLISHED/DISABLED、字段及 schemaVersion | 目录、分层、连接、预期物理位置；模型详情及物理检查 |
| PhysicalChangePlan | 后端 planId | 后端原始状态、冻结目标、风险、执行方式、指纹 | 归属模型；是业务物理计划，不是 Agent 确认记录 |
| Directory / Dictionary 引用 | 既有 UUID | scope/可用状态及必要显示信息 | 只读选择与校验，不扩展其独立管理能力 |

AgentOperation 是交互控制记录，保存能力 ID、owner、修订、参数摘要、证据、确认及回执，不与 PhysicalChangePlan 混用。参考源表只作为方案证据，不自动成为数据血缘。

定义最小字段：对象 id/identity/properties；关系 from/to/cardinality/resolver；规则 id/说明/强制位置/依据；能力 id/input/handler/reads/writes/preconditions/confirmation/readback。REST method/path 只在固定客户端绑定，不允许 YAML 指定任意地址或动态执行代码。

示例（说明性结构，非已实现格式）：

```yaml
id: model.prepare_update_fields
subject: DataModel
input: ModelFieldReplacementInput
handler: prepareModelFieldReplacement
reads: [model.get, model.inspect_physical]
writes: [DataModel.fields]
rules:
  - model.editable_status
  - model.preserve_field_identity
  - model.external_physical_structure_unchanged
confirmation: current_revision
readback: model.get
```

规则分别标记 backend（现有 Service 最终执行）、advisory（分层规划）、agent_flow（消歧、人工确认、不确定结果不重试）。“后端校验完善”不等于它能判断本次用户具体同意修改哪个对象，所以插件仍需保存目标和确认修订。

## 7 能力与接口逐项映射

以下路径相对 `/api/v1`。L=`/model-warehouse-layers`，D=`/data-sources`，M=`/models`。点号能力 ID 是设计名；prepare 表示先生成方案，实际写入在用户确认后执行。

所有查询保留 scope、时间、分页/截断信息；403 不当作空列表；404 仅在准确对象范围内解释。所有修改从完整基线构造请求，回查不能把失败解释为“什么也没发生”。需要回查的业务动作会额外依赖相应读取权限，应明确报告而不是静默跳过。

### 7.1 分层

| 能力 | REST / 输入 | 前置及影响 | 确认与回查 |
| --- | --- | --- | --- |
| layer.query/get | GET L；SearchRequest，get 用 UUID 精确查询 | 原列表读取权限；返回动态实例及引用计数 | 只读；保留分页 |
| layer.models | GET M；Search 中 warehouseLayerId 条件 | model.view；不能从前缀猜归属 | 只读 |
| layer.prepare_create | POST L；CreateModelWarehouseLayerRequest | system.configuration.update；code、name、前缀、颜色、排序、输入策略与完整输入分层集合 | 确认创建；按返回 UUID 查 L |
| layer.prepare_update | POST L/{id}/actions/update；UpdateModelWarehouseLayerRequest | 同上；被模型引用时不可改 code；允许输入集合整体替换 | 显示前后差异；按 UUID 回查 |
| layer.prepare_enable | POST L/{id}/actions/enable | 同上；允许新模型分配到此层 | 确认；查 enabled |
| layer.prepare_disable | POST L/{id}/actions/disable | 同上；历史关联保留，不阻止任务运行 | 披露实际影响；查 enabled |
| layer.prepare_delete | POST L/{id}/actions/delete | 同上；保留后端引用保护 | 指定名称/UUID确认；精确查询不存在，不能把无权限当删除成功 |

### 7.2 数据源

| 能力 | REST / 输入 | 前置及影响 | 确认与回查 |
| --- | --- | --- | --- |
| datasource.query/get | GET D、D/{id}；SearchRequest、hasPublishedModels | datasource.view；hasPublishedModels=true 额外 model.view | 只读，不自动测连接 |
| datasource.types | GET /data-source-types | 以类型接口的原授权及 supportedPurposes/连接形态为准 | 只读；能力矩阵不复制一份 |
| datasource.references | GET D/{id}/related-models、related-tasks、related-services | datasource.view 加对应领域 view；部分无权限标记不完整 | 只读；最终删除保护仍在 Service |
| datasource.prepare_create | POST D；CreateDataSourceRequest | datasource.create；code/name/目录/purposes/type/enabled/connection | 秘密表单＋确认；查详情，不声称已测通 |
| datasource.prepare_update | POST D/{id}/actions/update；UpdateDataSourceRequest | datasource.update；code 不变，完整 connection；同类型/认证下秘密 null 保留原值 | 显示目标、用途和配置差异；查脱敏结果 |
| datasource.prepare_enable/disable | 同 update，固定 enabled 目标值 | datasource.update；完整保留其他字段；受外部注册和引用规则限制 | 单独展示启停影响；查 enabled |
| datasource.prepare_delete | POST D/{id}/actions/delete | datasource.delete；有引用可能拒绝；不删除外部数据 | 指定对象确认；精确查不存在 |
| datasource.prepare_test_saved | POST D/{id}/actions/test | datasource.test；实际访问外部系统 | 明确测试后执行；展示返回诊断及时间，不缓存成永久健康 |
| datasource.prepare_test_draft | POST D/actions/test；type + connection | datasource.test；不保存连接，秘密不进入模型上下文 | 确认目标；返回测试结果 |
| datasource.namespaces/tables/table_schema | GET D/{id}/namespaces、tables、table-metadata | datasource.metadata；JDBC；精确 catalog/schema/table；tables limit 1～500 | 只读；区分视图及不完整枚举 |
| datasource.preview | GET D/{id}/table-preview | datasource.metadata；catalog/schema/table，limit 1～100 | 用户要求数据预览时读取；不把样本作为全量 |
| datasource.kafka_topics | GET D/{id}/kafka-topics | datasource.view；keyword/includeInternal | 只读资源发现，不声称可以建 JDBC 模型 |
| datasource.tmq_topics/tmq_topic | GET D/{id}/tmq-topics、tmq-topic | datasource.metadata；keyword 或 topic | 只读，保留产品限制 |

`POST D/{id}/actions/inspect-query` 是既有 SQL 结构检查能力。本期默认不向模型开放任意 SQL 工具；如果用户明确提交 SQL 建模需求，再评审该专属能力，不用它兜底所有结构读取。S3/HTTP 的连接维护通过各自 connection.kind 覆盖，但不能虚构这两个类型的 JDBC 元数据接口。

### 7.3 模型管理及物理操作

| 能力 | REST / 输入 | 前置及影响 | 确认与回查 |
| --- | --- | --- | --- |
| model.query/get/statistics | GET M、M/{id}、M/statistics | model.view；详情含完整字段和生命周期 | 只读 |
| model.references | GET M/{id}/references | model.view；用于显示引用影响 | 只读，不保证预查后依赖不再变化 |
| model.platform_types | GET M/platform-types；storageDataSourceId | model.view；目标 STORAGE JDBC 的方言能力，不要求连接测试成功 | 只读 |
| model.preview_managed_import | POST M/managed-import-preview；源连接、精确源表、目标连接 | datasource.metadata；映射字段；LOSSY/UNSUPPORTED 阻断 | 只读；不是完整创建预检 |
| model.preview_external_import | GET M/external-table-import-preview；storageDataSourceId/physicalTableName | model.create 或 model.update；已有普通表、默认命名空间 | 只读，保留真实字段结构 |
| model.prepare_managed_draft | POST M/managed-drafts；CreateManagedDraftRequest | model.create；完整 1～500 字段、连接、表名、可选分层/目录及排序键；无 DDL | 展示完整目标；查新 UUID 的 DRAFT/字段/归属 |
| model.prepare_external_draft | POST M；CreateDataModelRequest，EXTERNAL | model.create；后端实时读表并导入字段；不复制数据 | 确认绑定；查字段和物理状态 |
| model.prepare_update | POST M/{id}/actions/update；UpdateDataModelRequest | model.update；非 DRAFT 不能改变物理配置；编码不可改 | 展示差异及依赖；查详情 |
| model.prepare_update_fields | POST M/{id}/actions/update-fields；完整 fields | model.update；DRAFT/DISABLED；保留 UUID，遗漏就是删除；EXTERNAL 不改物理结构，已有匹配 MANAGED 的结构变化走计划 | 对完整差异确认；查 fields/schemaVersion；预读不是原子 CAS |
| model.inspect_physical | GET M/{id}/physical-table | model.view；状态含 NOT_FOUND/MATCHED/DRIFTED/UNREACHABLE/UNSUPPORTED | 只读，不单凭 exists=false 判断缺表 |
| model.get_ddl | GET M/{id}/physical-table/ddl | model.view；展示后端方言结果 | 只读；模型不生成替代 SQL |
| model.prepare_create_table | POST M/{id}/actions/create-physical-table | model.update；DRAFT MANAGED、有字段、目标满足建表条件 | 确认物理目标与 DDL；查物理结构，不声称已发布 |
| model.prepare_publish | POST M/{id}/actions/publish | model.publish；DRAFT/DISABLED；MANAGED 缺表时可能建表 | 披露建表；查状态和物理表，识别建表成功但发布失败 |
| model.prepare_disable | POST M/{id}/actions/disable | model.publish；PUBLISHED；保留物理表 | 确认；查 DISABLED |
| model.prepare_delete | POST M/{id}/actions/delete | model.delete；DRAFT/DISABLED，无保护引用；仅删管理元数据 | 明确保留物理表；查模型不存在 |
| model.change_plan_query/get | GET M/{id}/physical-table-change-plans、.../{planId} | model.view；planId 必须属于 modelId | 只读，展示后端原始状态 |
| model.prepare_create_change_plan | POST M/{id}/physical-table-change-plans；完整目标 fields | model.update；DRAFT/DISABLED MANAGED、物理表匹配；冻结风险及方案，旧 PLANNED 可能被替代 | 先确认生成；回查 planId；不声称已执行 |
| model.prepare_cancel_change_plan | POST .../{planId}/actions/cancel | model.update；仍为 PLANNED；不回滚已执行 DDL | 确认；查计划状态 |
| model.prepare_execute_change_plan | POST .../{planId}/actions/execute；executionMode | model.update；执行方式必须来自冻结计划；后端检查 schemaVersion、指纹、前置数据条件 | 显示实际风险/方案后确认；查计划、模型、物理表，PARTIAL 不自动重试 |
| model.prepare_refresh_statistics | POST M/{id}/actions/refresh-physical-statistics | model.view；访问物理库并更新平台统计 | 明确刷新动作；返回统计及采集时间 |
| model.preview/query_data | GET M/{id}/data-preview；POST M/{id}/actions/query-data | model.view；columns/filters/orders/分页/returnCount | 只读；保留限制和样本语义，POST 不等于写入 |
| model.spatial_preview/map | GET M/{id}/spatial-preview、.../map | model.view；后者 geometryField/bbox/width/height | 只读；元信息及 PNG 通过附件/结果卡显示，不假造空间分析 |

### 7.4 既有辅助入口和跨域引用

这些入口同样列入映射，避免用“完整模型管理”遗漏已有批量入口；跨域部分仅读取已有引用，不扩展其管理能力。

| 能力 | REST / 输入 | 语义、确认及结果 |
| --- | --- | --- |
| directory.query | GET /directories；MODEL 或 DATA_SOURCE scope | 现有权限；只读已有目录 |
| model.lineage | GET M/{id}/lineage/table、lineage/fields/{fieldId}；POST .../lineage/actions/query-fields | model.view + task.view + service.view；只读真实血缘，方向/深度使用原契约，不从参考源表推断 |
| model.related_tasks | GET M/{id}/related-tasks；role/SearchRequest | model.view + task.view；只读引用，不编辑任务 |
| model.preview_file_dataset | POST M/file-dataset-import-preview | model.create + filedataset.view；只读已存在文件逻辑表结构，后续复用 managed-drafts；不创建或解析文件数据集 |
| model.metadata_template | GET M/metadata-import-template | model.create；下载模板，不创建模型 |
| model.export_metadata | POST M/actions/export-metadata；modelIds 最多 200 | model.view；只读导出 MANAGED 元数据，返回文件，不读取业务行 |
| model.preview_metadata_import | POST M/actions/preview-metadata-import；multipart file + targetStorageDataSourceId | model.create；系统 .xlsx 模板最大 10 MiB；返回校验结果，无业务写入 |
| model.prepare_import_metadata | POST M/actions/import-metadata；目标连接 + models 最多 200 | model.create；确认完整批次 JSON；后端原子创建全部 MANAGED 草稿与字段，无建表/发布；查每个返回 UUID |

批量导入是单个后端原子业务动作，可以单次确认。它不同于 Agent 循环调用多个独立创建接口；后者逐项记录，不能承诺一起回滚。文件来自已授权附件，返回文件使用现有下载机制；模型不能指定任意本地文件路径。

## 8 代码与配置改动清单

| 区域 | 具体工作 | 是否改旧实现 |
| --- | --- | --- |
| 新 integrations/ontology-agent | 本体定义、固定能力处理器、REST 客户端、JWT 身份接入、原生会话/追问适配、操作状态、附件与结果 | 新增独立插件；不导入旧插件私有代码 |
| 新 data-scalpel-ui/src/modules/ontology-agent | 以旧聊天展示代码为基线有限复制，适配新 API/事件；增加业务方案卡、字段编辑、秘密表单、回执 | 新增模块；不导入旧模块私有文件；复用 shared/api 和共享组件 |
| AppShell 助手装配点 | 按部署设置懒加载旧/新模块，入口仍叫 AI 助手 | 仅装配选择；旧 modules/dsh 保持原样 |
| 前端运行配置及 vite 代理 | 为新前缀指向 DSH；existing 默认；核对 implementationId | 新增明确配置，不让用户选择 |
| deploy/ontology-agent | 固定版本镜像/插件装配、内部 Bridge 配置、新前缀反向代理、卷保留与回退说明 | 独立覆盖，不直接替换旧部署文件 |
| Java | 首选方案无源代码变化；第 4 节作为单独待批备选 | 未获批准不得新增或修改 |

不能承诺“现有任何文件一行不动”：统一入口必然需要装配点及部署路由调整。这里保护的是原 Agent 的业务实现和旧部署可回退性，不是禁止所有共享入口变化。

部署选择可由同一部署变量生成前端启动配置和插件装配；不为一个开关新增 Java 配置服务。新前缀先匹配，普通 `/api/v1` 继续转发 Java；默认 existing。运行握手不匹配时显示不可用，不静默切换。原生 DSH Web 管理入口不通过该代理向普通用户一并开放。

前端沿用现有 `api/`、`hooks/`、`model/`、`components/`、`index.ts` 结构即可。`components/` 承担聊天、按需打开的历史视图和操作卡；`hooks/` 承担新会话事件与附件状态；本体规则和执行器继续留在服务端插件。新模块使用独立查询缓存键、浏览器布局键及 CSS 作用域，避免新旧模块共享会话状态。无需为此次选型增加通用聊天 SDK、第二个前端应用或完整 DSH Web 副本。

## 9 三个端到端例子

### 修改分层规则

用户“DWD 允许从 ODS 和 DIM 输入” → 解析当前 UUID 和完整分层定义 → 展示输入集合前后差异，说明只是规划规则 → 原生 plan-review → 固定处理器确认当前修订 → POST update → 按 UUID 回查。不能据此宣称任务执行策略已改变。

### 新建连接并建模发布

选择类型和用途 → 专用表单填写秘密 → 明确测试/保存为独立动作 → 保存数据源并回查 → 读取源表及目标平台类型 → 预览字段映射 → 编辑字段、目录、分层 → 确认创建完整草稿 → 回查 → 展示发布可能建表的影响 → 确认发布 → 核对物理表与 PUBLISHED 状态。每步失败停在具体结果，不自动删掉前面已创建的连接或模型。

### 给已有受管表改字段

读取完整模型与物理状态 → 若已发布，先明确停用需求 → 按字段 UUID 生成目标差异 → 确认生成后端物理计划 → 展示后端风险、执行模式和检查 → 再确认执行具体 planId → 核对计划、字段版本及物理结构。漂移/过期计划不继续；PARTIAL 显示已知变化和后续核对入口。

## 10 实施前仍需验证的具体事项

静态设计已经给出可执行的路线，不等同于已完成运行验证。接入实现阶段先验证新前缀能通过 DSH Web 服务公开路由接收既有 JWT，现有 capabilities 的往返不会递归，旧定时协调不会延长新 JWT，以及不同用户/登录上下文不能交叉确认。再验证工作区 ID 复用、原生等待取消和重启后 READY/UNKNOWN 恢复。

这些验证不需要预先修改 Java，也不要求启动第二套临时应用。若进行系统联调，遵循根 start-local-dev.sh 管理的开发环境。结果暴露新 Java 缺口时停止相关实现，先提交具体改动供用户确认。

## 证据入口

- [锁定包与 integrity](../../deploy/dsh/package-lock.json)、[原生 Web 部署](../../deploy/dsh/README.md)。固定包中的 lib/types 和 README 是本次能力依据，未采用 master 的新增交互特性。
- [JWT 当前身份接口](../../data-scalpel-admin/src/main/java/cn/superhuang/data/scalpel/admin/security/web/resource/AuthenticationResource.java)、[JWT 安全链及权限快照](../../data-scalpel-admin/src/main/java/cn/superhuang/data/scalpel/admin/security/SecurityConfiguration.java)。
- [DSH 登录身份检查](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/dsh/service/DshIdentityService.java)、[现有 capabilities 与追问入口](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/dsh/web/resource/DshResource.java)、[旧 ensure 预配](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/dsh/service/DshService.java)、[后台协调](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/dsh/service/DshEventService.java)。
- [已有原生追问适配](../../integrations/dsh-plugin/src/interactions.ts)、[会话监听](../../integrations/dsh-plugin/src/sessions.ts)、[前端问题卡](../../data-scalpel-ui/src/modules/dsh/components/Questions.tsx)。
- [现有聊天入口](../../data-scalpel-ui/src/modules/dsh/index.ts)、[聊天抽屉](../../data-scalpel-ui/src/modules/dsh/components/DshDrawer.tsx)、[会话事件 Hook](../../data-scalpel-ui/src/modules/dsh/hooks/useConversation.ts)、[旧 API 固定接入](../../data-scalpel-ui/src/modules/dsh/api/dsh.ts)；原生 Web 裁剪包依据见第 2.1 节。
- [分层 API](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/model/web/resource/ModelWarehouseLayerResource.java)、[数据源 API](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/datasource/web/resource/DataSourceResource.java)、[模型 API](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/model/web/resource/DataModelResource.java)。
