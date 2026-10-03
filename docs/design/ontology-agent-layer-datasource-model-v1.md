# 分层 数据源与模型本体 Agent 落地方案

状态：待评审，未实施。创建日期：2026-10-01；修订日期：2026-10-02。

本文提出基于 DSH 的独立本体 Agent 实现，范围为数仓分层、数据源和模型。用户始终使用一个“AI 助手”入口，由部署方选择原有实现或本体实现。新增主体位于前端模块和 TypeScript 插件中；现有 Java 业务接口继续承担认证授权、业务校验、事务和持久化，不预建 Java 本体引擎或通用能力执行平台。

本次只交付设计文档。现状依据当前工作区源码与现行专题文档静态核对，不代表已核验运行环境的 REST 契约或完成联调。范围、能力 ID、配置及新接口均为提案，评审通过前不作为现行契约。

本轮细化结果见[接入定案建议与能力映射](ontology-agent-integration-and-capabilities.md)：已核对锁定版本包，首选“新插件专属同源接入 + 现有只读身份校验 + 原生追问确认”，Java 零新增作为当前候选路线。该文给出完整动作映射、代码改动清单和端到端流程；本文保留总体设计，Java 接入目录及转发方案均为待单独确认的备选。

原生 Web 裁剪评估已补充完成，结论见配套文档第 2.1 节：本期建议在新模块有限复制并适配现有 DataScalpel 聊天界面，运行时继续复用 DSH；不采用整套原生 Web 的产品接入路线。这个建议满足单入口和隐藏完整工作区的目标，但有复制代码的维护代价，尚未作为用户已批准的实施选择。

2026-10-02 修订：新本体 Agent 的业务能力直接调用现有 REST API，不依赖系统 MCP 的接口发现、描述或执行。删除此前因 MCP 目录限制而提出的数据源列表适配；身份接入与本体能力分别设计，不能默认复用旧 MCP 令牌后就能脱离其授权限制。

## 1 已确认的方向与本次建议

### 已确认的方向

- 原有 Agent 实现保持原样，新实现独立维护；两套均使用 DSH。
- 对用户只有一个助手入口，部署选择实现，不提供用户切换或会话中途切换。
- 新增主体为 `data-scalpel-ui/src/modules/ontology-agent/` 和 `integrations/ontology-agent/`。
- 本体定义、业务工具、调用编排和操作状态主要由服务端 DSH 插件承载，不放在浏览器执行。
- 复用现有业务 API。只有发现具体接入缺口才增加最小 Java 适配，不重复业务 Service 和类型映射。
- 用户明确要求：任何新增或修改 Java 代码，都必须提前说明具体缺口、拟改文件/接口及替代方案，并获得用户确认后才能实施。本文中的候选 Java 接入设计不构成实施授权；不能先写代码再请求确认。
- 本次先覆盖分层、数据源、模型，不扩展为任务、调度、服务发布或治理平台。

### 已调整的首批交付范围

根据 2026-10-02 评审反馈，首批覆盖分层、数据源、模型的完整管理流程，不再将修改、删除和物理操作统一推到下一批。现有 REST 已提供主要业务动作，Agent 的工作是定义语义、绑定能力、展示方案并调用这些接口，不重新实现业务功能。实现可以按只读、管理操作、物理操作递进，但均属于同一批交付。

| 维度 | 首批交付 | 业务边界 |
| --- | --- | --- |
| 分层 | 查询、读取规划规则与所属模型、创建、修改、启用、停用、删除 | 保留原有引用保护及分层规则语义 |
| 数据源 | 查询、创建、修改连接及用途、启停、删除、测试；按产品能力浏览元数据及受控数据预览 | 连接配置覆盖现有产品类型，资源发现按后端能力开放；模型绑定仍遵循 JDBC 限制 |
| 模型 | 查询模型与字段、创建 MANAGED/EXTERNAL 模型、修改基本信息与字段、发布、停用、删除；检查物理表、查看 DDL、建表、生成/查询/取消/执行物理变更计划、受控数据查询 | 全部复用现有生命周期、类型映射、保护及方言能力；不生成任意 SQL 绕过业务接口 |
| 支撑引用 | 查询和选择已有 DATA_SOURCE/MODEL 目录；识别已有码表引用 | 目录、码表、字段模板自身的管理不因被引用而自动扩展进本次范围 |

数据查询只使用既有受控预览/查询接口，遵守分页、数量和字段范围，并标注截断情况。创建模型不复制源数据；物理变更计划如由后端提供重建方案，可能复制表内数据，必须按该计划展示影响。模型删除只删管理元数据，不等于删物理表；不新增平台原本没有的删表或任意 SQL 能力。

能力目录需逐项列出本表动作的输入、权限、前置条件、副作用、确认及回查方式。某种产品或状态不支持的动作应明确不可执行，不能以“完整功能”承诺超出后端能力。质量规则、指标管理、任务和数据服务等独立功能域不自动纳入本期；文件导入导出等辅助入口另列能力映射，不能用一句“完整 CRUD”代替接口清单。

## 2 现状核对与不能照搬的概念

| 已核对事实 | 对本体设计的影响 |
| --- | --- |
| 分层是全局、扁平、动态配置，可选关联到模型 | 不建立分层父子树，不把 ODS/DWD 等固定为不可变化的本体类型；它们是分层实例 |
| 分层包含编码前缀和输入策略，当前属于建模规划信息 | 前缀是候选生成规则；允许输入关系不是任务血缘，也不是任务执行门禁 |
| 当前分层没有绑定数据源或存储的业务字段 | 不从旧 `scp-agent` 搬入“分层绑定存储”关系；可查询某分层模型实际使用哪些数据源，但标为派生结果 |
| 一个数据源可以同时具备 SOURCE、STORAGE、DISTRIBUTION 用途 | 用途是同一连接的角色，不拆成三个独立对象，也不凭名称判断用途 |
| 数据源 enabled 是人工状态 | “已启用”不等于“连接健康”；连接测试结果必须附时间与范围 |
| 模型绑定 JDBC 数据源及解析后的 Catalog、Schema、表名 | 物理位置使用后端返回值，不能根据当前连接配置重算已保存模型的位置 |
| MANAGED 要求 STORAGE；EXTERNAL 可绑定任意业务用途的 JDBC 数据源 | 不把 STORAGE 要求套到所有模型；实际类型支持以当前后端能力为准 |
| JDBC 结构预览的来源只要求符合该接口的 JDBC/启用条件，不强制 SOURCE 用途 | 展示用途，但不增加后端不存在的 SOURCE 门槛 |
| `/models/managed-drafts` 原子保存模型及完整字段，不保存来源绑定 | 复用此接口，避免先建空模型再逐个加字段；参考源表不自动形成真实数据血缘 |
| MANAGED 发布可能自动创建缺失物理表 | 发布不是普通元数据保存，首批不把它隐藏在“完成建模”中 |
| 类型转换由 Dialect 提供，LOSSY/UNSUPPORTED 阻止导入或建表 | Agent 只解释和使用结果，不在 TypeScript 另写物理类型映射 |

事实入口：[模型管理](model-management.md)、[数据源管理](data-source-management.md)、[平台类型](model-data-type-system.md)、[物理表演进](model-physical-table-evolution.md)。旧 SCP 的定义组织、确认和回查机制可以借鉴，但业务含义以本系统为准。

## 3 最小本体模型

本体用于给对象、关系、规则和动作赋予稳定语义。首批采用版本化定义文件和内存索引，不引入图数据库、向量库、实例同步库或 OWL 推理器。

### 3.1 对象与标识

| 对象 | 标识及关键属性 | 权威来源 |
| --- | --- | --- |
| WarehouseLayer 数仓分层 | UUID；code、name、enabled、modelCodePrefix、inputLayerPolicy、allowedInputLayers | 分层 API |
| DataSource 数据源 | UUID；code、name、产品类型、用途集合、enabled、目录、类型能力；不含明文凭据 | 数据源与类型 API |
| PhysicalTable 物理表对象 | 组合定位：dataSourceId、Catalog、Schema、表名及对象种类；无平台 UUID 时不伪造业务 ID | 数据源元数据 API、模型物理检查 |
| PhysicalColumn 物理列 | 所属物理表定位与后端返回列标识；原生类型、约束、物理角色 | 表元数据及导入预览 |
| DataModel 数据模型 | UUID；code、name、status、physicalTableMode、warehouseLayer、storageDataSourceId、物理位置、schemaVersion | 模型详情 API |
| ModelField 模型字段 | UUID；modelId、code、name、PlatformTypeDefinition、可空、主键、顺序、说明及可选码表引用 | 模型字段契约 |
| Directory 目录引用 | UUID；scope、name、parentId、显示路径；本期只读，模型使用 MODEL、数据源使用 DATA_SOURCE 范围 | 通用目录 API |

Catalog/Schema 首期作为物理定位的值对象，平台类型作为既有类型契约，不为了画图全部升级成可管理实体。字段候选未保存时使用方案内临时标识，不能冒充已有字段 UUID。

表标识保留 null、空值和大小写的接口语义，不在插件中统一小写化外部数据库标识。模型编码和目标表名的规范化使用其业务契约；无法明确表达的物理对象应返回不支持，不替用户猜测。

### 3.2 关系

| 关系 | 基数与依据 | 注意事项 |
| --- | --- | --- |
| DataModel belongsTo WarehouseLayer | 每个模型 0..1 个分层；分层可有多个模型 | 未分层合法，停用不清除历史引用 |
| DataModel bindsTo DataSource | 每个模型 1 个连接；按模型的 storageDataSourceId | 不据字段名强制所有连接必须 STORAGE |
| DataModel hasField ModelField | 已保存字段归属 1 个模型；通用模型草稿可能暂时无字段 | 本 Agent 首批创建 MANAGED 草稿要求完整字段集 |
| DataModel locatesAt PhysicalTable | 每个模型记录 1 个预期物理位置，实际表可能不存在或无法确认 | 定位不等于存在，更不等于结构一致 |
| PhysicalTable hasColumn PhysicalColumn | 元数据读取时确认 | 读取不全必须带完整性标记 |
| WarehouseLayer allowsInputFrom WarehouseLayer | 同类型多对多；允许自引用；仅规划 | UNRESTRICTED 与 ALLOW_LIST 空集合含义不同 |
| DataSource / DataModel organizedIn Directory | 各自 0..1 个目录，目录 scope 分别核对 | 分层与目录不互相替代 |
| ModelField correspondsTo PhysicalColumn | 仅在后端结构检查或映射结果提供依据时建立 | 不按显示名称猜测，不混同值来源血缘 |

Agent 方案可以保留 `referenceSource` 作为设计依据，例如“字段候选参考了某源表在某时刻的结构”。它属于方案证据，不新增业务模型关系，不对外宣称实际数据已经从该源表流入模型。

### 3.3 事实与判断分开

每次查询返回数据之外，还应保留来源操作、对象标识、观察时间、查询范围、分页/截断情况和结构版本（适用时）。无需将所有业务数据复制成一张长期实例图。

- `OBSERVED`：当前权限范围内得到的事实快照。
- `NOT_FOUND`：在明确范围内得到不存在的可靠结果。
- `UNKNOWN`：连接失败、无权限、没有完整读取或无法判断。
- `SUGGESTED`：Agent 根据需求提出的分层或字段设计建议。

这些是提议的表达类别，不替代原 API 状态。物理检查保留已有 `NOT_FOUND/MATCHED/DRIFTED/UNREACHABLE/UNSUPPORTED`；不能把 UNREACHABLE 下的 `exists=false` 单独解释为表不存在。模型 DRAFT/PUBLISHED/DISABLED 与物理状态分别展示。

## 4 规则表达及责任

规则定义保存稳定 ID、业务说明、适用动作、检查时机、依据、执行位置，以及失败/未知时处理。定义不会自动成为通用规则脚本。

| 类型 | 例子 | 谁保证 |
| --- | --- | --- |
| 后端强制规则 | 编码唯一、目录范围、停用分层分配限制、MANAGED 目标用途、字段类型与主键约束 | 现有 Java API 是最终权威；插件可预查改善体验，不复制完整算法 |
| 规划建议 | 分层前缀、允许输入分层、推荐粒度 | 插件解释依据和冲突，不能擅自升级为保存阻断 |
| Agent 流程规则 | 名称歧义先确定对象、写操作先确认、来源和目标区分、超时不自动重发 | 新插件固定执行流程与工具边界 |

启动时检查对象/关系/能力 ID 唯一、引用有效、只读关系解析器不绑定写能力、所有可执行能力有显式处理器。运行时检查当前接口可用性、权限与契约。

枚举优先读取当前类型/能力接口和 OpenAPI；只有业务解释放在本体中。平台类型和数据库支持矩阵不能从文档摘一份后永久硬编码。发现契约与能力适配不兼容时停用该能力并说明缺口，不让模型猜请求格式。

## 5 工具与能力设计

### 5.1 面向模型的两个入口

| 工具 | 模型可以做什么 | 模型不能做什么 |
| --- | --- | --- |
| `ontology_query` | 搜索定义摘要、按 ID 读完整定义/能力契约、查看一跳关系 | 执行任意图查询、提交 API URL、把定义关系当作已观测实例 |
| `capability_execute` | 调用已注册只读能力、准备/修改操作方案、读取方案及回执 | 指定身份、凭据、任意 operationId/URL；直接确认或提交写入 |

工具 ID 为设计名，最终契约在实现前固化。不同业务能力分别有精确输入 Schema，不使用一个包含全部字段的宽松参数对象。模型只传能力 ID 与相应业务参数；查询契约是指导，真正的允许范围由注册表和输入校验保证。

只读能力直接返回结构化结果。写意图返回服务端创建的操作方案，界面按方案 ID 读取；调用工具完成不代表业务保存完成。完整契约按需读取，不将所有接口、字段和关系一次塞进上下文。

### 5.2 首批能力清单

| 能力组 | 建议能力 ID | 行为 |
| --- | --- | --- |
| 分层查询 | `layer.query`、`layer.get` | 当前分层、规划规则、引用情况；get 可通过 Search DSL 按 UUID 精确查询 |
| 分层管理 | `layer.prepare_create/update/enable/disable/delete` | 各动作是独立能力 ID；展示目标和前后差异，复用引用保护及原接口 |
| 数据源发现 | `datasource.query`、`datasource.get`、`datasource.types` | 当前身份可见连接与类型能力，敏感字段只保留 configured 状态 |
| 数据源管理 | `datasource.prepare_create/update/enable/disable/delete` | 启停通过现有 update 的 enabled 字段实现；按产品类型校验连接形态，凭据由专用表单输入 |
| 物理结构 | `datasource.namespaces`、`datasource.tables`、`datasource.table_schema` | 只读结构，保留完整定位、分页和读取失败语义 |
| 连接测试 | `datasource.prepare_test` | 区分待保存配置测试与已有连接测试；展示目标摘要并确认后调用，不作为后台健康巡检 |
| 数据与资源浏览 | `datasource.preview`、`datasource.resources`、`model.query_data` | 绑定已有产品资源发现及受控查询接口；具体产品参数分别定义，不开放任意 URL/SQL |
| 模型读取 | `model.query`、`model.get`、`model.inspect_physical` | 模型、字段、状态和真实物理结构检查 |
| 建模依据 | `model.platform_types`、`model.preview_managed_import`、`model.preview_external_import` | 复用后端类型和映射结果，不自行翻译 JDBC 类型 |
| 模型创建 | `model.prepare_managed_draft`、`model.prepare_external_draft` | 生成单模型完整方案，确认后执行现有创建接口 |
| 模型维护 | `model.prepare_update/update_fields/publish/disable/delete` | 区分基本信息、完整字段替换及生命周期动作；发布需披露可能建表 |
| 物理表管理 | `model.get_ddl`、`model.change_plan_query/get`、`model.prepare_create_table/create_change_plan/cancel_change_plan/execute_change_plan` | 直接复用后端 DDL 和变更计划；生成计划会持久化并可能替代旧计划，也属于写操作 |
| 支撑目录 | `directory.query` | 只读相应范围目录；未分类合法时允许明确不选择 |
| 方案处理 | `operation.get`、`operation.revise` | 获取结果或修改同一方案；修改使旧预览失效，没有模型侧 confirm/commit |

不机械地给每个 REST 接口换名包装。能力封装的价值是稳定语义、目标解析、必要的查询顺序、预览和回查。长期操作能力以真实业务需求增加，首批没有多智能体分工。

### 5.3 DSH 运行配置（Preset）是什么

Preset 是 DSH 装配一个 Agent 时使用的配置包，规定加载哪些插件和模型工具、采用哪些行为说明及会话辅助能力。现有工程通过 preset.yml 和 agent.cordis.yml 维护它；它不是一个额外智能体，也不是让用户选择的模式。

新本体 Agent 使用自己的运行配置，装配本体查询、业务能力、追问及必要上下文功能。模型可以准备“修改模型”的方案，但不会获得任意 Shell/HTTP 等可直接绕过方案流程的执行工具。REST 执行器仍在插件内部。限制工具范围不减少已定义的业务功能，也不能只靠提示词实现：实际装配及处理器必须保证确认前不能提交写入。

“写操作由界面确认”目前仍是方案建议；用户在本轮要求解释 Preset，尚未据此认定已经接受该交互限制。

## 6 API 复用及已发现的缺口

以下是源码核对的候选映射。能力处理器通过内部 HTTP 客户端调用固定的 REST API，不让模型搜索和拼装任意接口。接口契约以现有 DTO、运行时 `/v3/api-docs` 及实际行为为准；OpenAPI 用于开发时核对和契约维护，不要求每次工具执行动态发现接口。

| 场景 | 现有接口 | 说明 |
| --- | --- | --- |
| 分层查询/创建 | `GET/POST /api/v1/model-warehouse-layers` | 没有独立详情 GET，按 UUID 使用通用查询；创建需 system.configuration.update |
| 数据源列表 | `GET /api/v1/data-sources` | 复用原 SearchRequest 与授权；使用 hasPublishedModels 时保留额外 model.view 权限要求 |
| 数据源详情/类型 | `GET /api/v1/data-sources/{id}`、`GET /api/v1/data-source-types` | 模型端不可见凭据 |
| 数据源结构 | `GET /api/v1/data-sources/{id}/namespaces`、`/tables`、`/table-metadata` | 使用准确 Catalog/Schema/表定位；需要 datasource.metadata |
| 已保存连接测试 | `POST /api/v1/data-sources/{id}/actions/test` | 会访问外部连接，按该业务副作用决定确认流程，不依赖 MCP 分类 |
| 模型读取 | `GET /api/v1/models`、`/{id}`、`/{id}/physical-table` | 分层和数据源过滤使用现有查询契约 |
| 目标类型 | `GET /api/v1/models/platform-types` | 与目标数据源相关 |
| 参考源表创建受管模型 | `POST /api/v1/models/managed-import-preview` | 只映射结构，不检查模型编码/目标表占用，不是完整创建 dry-run |
| 原子保存受管草稿 | `POST /api/v1/models/managed-drafts` | 一次保存模型和 1～500 个字段；不建表、不复制数据、不保存来源绑定 |
| 绑定已有表 | `GET /api/v1/models/external-table-import-preview`，随后 `POST /api/v1/models` 指定 EXTERNAL | 创建时后端再次读取表结构；仅支持现有契约允许的物理对象 |
| 目录选择 | `GET /api/v1/directories` | 按对象限定 MODEL 或 DATA_SOURCE；不静默创建新目录 |

完整管理动作继续复用下列现有接口，不新增同义 Java 封装：

| 场景 | 现有接口 | 需要保留的语义 |
| --- | --- | --- |
| 分层修改/启停/删除 | `POST /api/v1/model-warehouse-layers/{id}/actions/{action}` | action 分别为 update、enable、disable、delete；依赖保护由后端执行 |
| 数据源创建/修改/删除 | `POST /api/v1/data-sources`，`POST /api/v1/data-sources/{id}/actions/{action}` | action 为 update 或 delete；修改请求含完整连接与 enabled，同类型同认证方式下敏感字段 null 的保留语义按原契约 |
| 未保存连接测试 | `POST /api/v1/data-sources/actions/test` | 不保存连接；需要本次表单提供的连接配置 |
| 模型基本信息/字段修改 | `POST /api/v1/models/{id}/actions/{action}` | action 为 update 或 update-fields；字段为完整替换，保留原字段 UUID，遗漏表示删除 |
| 模型发布/停用/删除 | `POST /api/v1/models/{id}/actions/{action}` | action 为 publish、disable、delete；发布可能建表，删除元数据不删物理表 |
| 建表预览/执行 | `GET /api/v1/models/{id}/physical-table/ddl`，`POST /api/v1/models/{id}/actions/create-physical-table` | 用后端 DDL 预览及执行，不接受模型自写 SQL |
| 物理变更计划 | `GET/POST /api/v1/models/{id}/physical-table-change-plans`，`GET .../{planId}`，`POST .../{planId}/actions/{action}` | action 为 cancel 或 execute；生成、取消、执行均按真实副作用处理，执行复用冻结计划、结构指纹、版本及数据检查 |

### 接入事项 A REST 调用的用户身份

数据源列表本身可以作为普通 REST 接口使用。原方案引用的 `#hasPublishedModels` 限制属于系统 MCP 目录编译器，不是 Spring 方法授权无法处理该表达式，也不是本体模式的接口缺口。无需为此新增 `/api/v1/ontology-agent/data-sources`，无需修改原权限表达式。

需要单独解决的是插件如何携带当前用户身份调用 REST。旧 DSH 使用系统 MCP 托管令牌；源码中的 SecurityConfiguration 与 SystemMcpHandlerGuard 表明，即使携带该令牌直接请求 REST，仍会进入 MCP 令牌安全链并受目录限制。因此不能只换 HTTP 客户端便宣称已经移除 MCP 依赖。

新接入需核对现有登录身份的委托方式及凭据有效期，优先复用普通 REST 身份机制；执行必须保留当前用户权限，不能使用共享管理员身份或由模型传入 userId。凭据只在受信任的服务端接入层处理，不进入模型上下文或方案记录。具体凭据传递、失效和续期协议在接入阶段确定，未完成前不承诺 Java 零改动，也不先建设新令牌平台。原有 MCP 安全链和旧 Agent 保持不变。

本轮已细化为[接入方案第 3 节](ontology-agent-integration-and-capabilities.md#3-首选接入java-零新增ts-插件承担专属接入)：插件通过 `/auth/me` 验证 JWT 身份快照，再通过已有 `/dsh/capabilities` 路由前置的 DshIdentityService 检查用户启用状态；保留普通 REST 的权限快照语义。新会话使用插件专属路径，不调用旧 ensure 或 MCP 预配。该方案有旧健康路由依赖，尚待运行核对；只读 Java 身份接口是解耦备选，不是必做项。

### 接入事项 B 优先核对 DSH 原生交互复用

现有 `/api/v1/dsh` 已有原生追问的读取、事件及回答通道。原方案从“它不是完整可编辑业务表单协议”直接推导出“需要新增 Java 确认接口”，结论过早。先验证插件能否通过已有 interactionId 将固定操作方案及修订绑定到一次原生确认，再由插件检查真实用户回答并执行。确认不能只是把“同意”文字交回模型、由模型决定调用写接口。

普通追问、一次性确认、可编辑业务表单及可恢复的操作记录需要分别评估。原生机制可满足的部分直接复用；字段表格、敏感输入和复杂差异展示按实际缺口补业务组件。新增固定方案转发接口只作为备选，证明现有交互无法满足需求后，再按第 1 节约束提前请求用户确认 Java 改动。

### 缺口 C 没有通用的完整建模预校验或条件更新契约

现有导入预览不会保证用户修改后的完整方案必定能保存。首批用已暴露的检查改善反馈，明确最终保存仍可能被后端拒绝，不新增 Java 通用预检平台。

已有对象修改纳入首批。插件保存修改基线和差异，确认前重读相关字段；发现变化时使预览失效并重新核对。字段整体替换必须基于完整字段集且保留 UUID，不能把用户只提到的几个字段当成完整列表直接保存。

插件“先读再写”仍不等于原子条件更新；普通接口没有提供 expectedVersion 时沿用现有页面的并发语义，不宣称杜绝最后瞬间的覆盖。schemaVersion 不是所有基本信息修改的通用版本。物理变更计划则复用后端已有的版本、指纹及执行状态校验，不自行弱化。这一边界应在评审中明确，但不据此自动取消修改能力或新增全局乐观锁。

## 7 确认与执行机制

### 7.1 谁可以提交

下列步骤描述拟议的持久业务方案流程，尚未决定必须使用新增 HTTP 接口承载。按第 6 节及第 9.1 节优先复用原生交互：若现有已认证回答通道足够，沿用该通道，并由插件内部绑定相同的用户、操作 ID、修订和摘要，不要求改变原 Java 请求 DTO。

1. 模型通过能力工具准备方案，插件存储归属、能力、参数、依据及修订号。
2. 前端通过已认证的新插件接入读取结构化预览，展示真实目标及副作用；不是显示一段模型自写说明代替预览。
3. 用户可修改方案；前端和模型修改走同一修订控制。每次修改重新形成待核对内容。
4. 用户通过原生结构化回答确认；插件内部将 interactionId/questionId 绑定到 operationId、revision 和预览摘要，读取已保存参数，不能让确认回答替换业务参数。
5. 新插件借助 Admin 只读接口验证当前用户，再校验会话、修订和有效期，并串行执行；备选 Java 转发方案未经确认不实施。
6. 插件在当前用户权限下重新读取必要状态、检查契约，然后调用固定业务接口并回查。

对修改展示修改前后差异，对删除展示实际删除对象及保留内容，对发布/建表/物理变更展示真实副作用。生成物理变更计划会写入业务状态，不能伪装成只读预览；建议先确认生成计划，再根据返回的实际方案确认执行。各步骤在同一业务对话中呈现，分别记录结果。

确认入口不注册为模型工具，也不作为其他自动执行通道开放，浏览器不获得 Bridge 密钥。工具结果只返回操作 ID 和展示数据，不返回可代替用户确认的访问 key。自然语言“同意”本期仍需要界面上的具体方案确认，不由模型自行证明已获批准。

新 Agent 首批仅装配本体工具、必要追问与上下文能力，不装配任意 Shell、任意 HTTP、Cordis 控制或原始接口调用工具。REST 客户端由插件内部持有，只由显式能力处理器调用固定接口；执行受能力注册表、输入校验、确认状态及后端当前用户权限和业务校验约束。模型工具调用不能修改本体定义或内部客户端配置。不需要经过 api_search、api_describe、api_invoke。

上述保证针对选定的新助手执行通道，不声称阻止合法用户通过普通业务页面自行操作，也不把共享进程当作抵御服务器管理员的安全沙箱。

### 7.2 方案及证据

操作记录至少包含：稳定 operationId、ownerUserId、sessionId、能力 ID、创建请求标识、revision、规范化参数、预览摘要、definitionVersion、能力绑定修订、接口契约指纹、依赖对象/结构快照、创建与到期时间、状态、执行回执和确认记录。

创建/编辑请求使用稳定客户端标识去重；相同标识不同内容冲突。同一方案只消费一次提交。另起新方案可能是另一个业务操作，不能将本地去重宣称为跨所有请求的 exactly-once。

摘要只覆盖与确认有关的字段和稳定排序后的结构，不用整个上游 JSON 或无关统计波动使预览失效。目标、字段、映射结果或能力契约变化必须重新核对；不得在用户确认后静默替换为“最新修正版”。默认建议 24 小时有效，过期只能重新核对。

证据应保存可供复核的最小非敏感结构及结果，而不只是无法解释的 hash。不保存凭据、完整连接秘密或业务行样本。新实现的 REST 身份凭据按第 6 节接入协议处理，不默认沿用旧 MCP 托管令牌及其持久化方式。

数据源创建和凭据修改使用专用敏感输入区，模型只看到“已提供/保持原值/需补充”。持久操作记录只保存非敏感参数和本次输入修订引用，秘密在受信任的执行通道中短暂使用，确认绑定该修订。秘密变化使旧确认失效；断线或重启失去未提交的秘密时要求重新填写，不将密码写入聊天历史或普通方案存储。既有秘密的保留遵循原接口契约，不以掩码字符串回填。

### 7.3 状态与失败

| 状态 | 含义及后续行为 |
| --- | --- |
| EDITING / READY | 信息不完整或已形成待确认预览；用户/模型修改使预览失效 |
| EXECUTING | 已持久化执行意图，禁止再次提交；关闭面板不终止已发出的业务请求 |
| SUCCEEDED | 业务响应成功且完成约定回查 |
| SUCCEEDED_UNVERIFIED | 业务已明确返回成功，回查失败或与预期不一致；显示具体差异，不再次创建 |
| REJECTED | 能确认未执行或业务明确拒绝且无已知部分成功；修改后必须生成新修订并重新确认 |
| PARTIAL | 已确认部分副作用，例如物理变更未完整完成，或建表成功而发布失败；保留后端实际状态和检查结果，禁止直接重放 |
| UNKNOWN | 请求已发出但无法确认结果、响应不完整、重启发现 EXECUTING；只允许核对，不自动重发 |
| CANCELLED / EXPIRED | 尚未执行的方案已取消或过期，原确认不能再用 |

单模型创建优先使用 `/managed-drafts` 原子保存，不使用先创建空模型再写字段的多步流程。一个确认单元绑定一个明确业务动作；复合需求拆为可见步骤，例如创建、发布分别记录，物理变更计划生成与执行分别记录，不承诺跨接口回滚。单次业务接口本身也可能跨管理库和外部数据库产生部分结果，不能只按 HTTP 成功/失败判定是否有副作用。

UNKNOWN 后可按模型编码和物理位置查询候选，并展示对比；发现相同对象不等于证明由本次操作创建，仍需区分观测结果和因果归属。不要通过自动删除或反向写入“补偿”一个不确定结果。

## 8 端到端业务流程

### 8.1 参考已有表创建受管模型

1. 解析需求中的分层、源连接、源表、目标存储及目录。同名、多候选或缺少用途时询问，不以第一项代替用户选择。
2. 读取分层事实、数据源类型能力和完整源表结构。Catalog/Schema 不明确时先消歧。
3. 调用 managed-import-preview 获取平台字段候选与映射质量；存在 LOSSY/UNSUPPORTED 时保持阻断，不降级成字符串绕过。
4. Agent 提出名称、字段说明、粒度等建议，区分来自元数据的事实与推断。前缀可生成候选，但用户可以修改。
5. 插件形成 MANAGED 草稿方案，完整列出字段、目标连接/位置、目录和分层，以及“只保存结构、不创建物理表、不复制数据”。检查结果按已检查、未检查、失败分别呈现。
6. 用户编辑、核对并确认当前修订；提交前读取必要依赖，变化则退回核对。
7. 由固定能力处理器通过内部 REST 客户端调用 `/models/managed-drafts`，后端再次做权威校验与原子保存。
8. 按返回模型 UUID 读取详情，对比规范化后的模式、DRAFT 状态、字段和归属；展示回执和模型详情入口。

### 8.2 绑定已有表创建外部模型

1. 确定连接与默认命名空间中的已有表，检查实际契约能否表示该表；不把任意 Schema 表误当成默认位置。
2. 读取 external-table-import-preview，展示映射结果及问题。目标连接不额外强制 STORAGE 用途。
3. 准备 EXTERNAL 方案，明确“绑定已有表、不复制数据、不修改物理表”。字段来自后端导入，不允许界面暗示可以任意改变真实类型。
4. 用户确认后通过现有模型创建 API 执行；后端会重读物理结构。读取与保存之间的外部变化不能完全由插件封锁，回查不一致时标为待核实。
5. 返回模型与字段，并区分草稿生命周期和当前物理匹配状态。

手工设计 MANAGED 模型复用第一条流程的目标选择、平台类型查询、方案和原子保存，省略来源导入；这时没有来源证据，也不虚构来源关系。

### 8.3 修改已有模型及物理结构

读取模型完整信息、字段、生命周期和物理状态，生成基于现状的差异。基本信息调用 update；允许直接维护的字段元数据或未建表结构调用 update-fields。已匹配的 MANAGED 表需要结构变化时，确认后由后端创建物理变更计划，展示返回的风险、检查、执行方案及影响，再确认执行。EXTERNAL 只允许原接口支持的元数据调整，不能通过更换接口修改实际表。

PUBLISHED 模型若需先停用才能改字段，应把停用列为显式前置动作，不能自动停用并恢复。执行结束核对模型字段、schemaVersion、物理状态及计划状态；PARTIAL 单独呈现，不自动重新生成并执行计划。

### 8.4 创建/修改数据源与分层

数据源先确定产品类型、用途和配置形态，用户在表单补充秘密；读取既有连接时仅展示非敏感摘要。修改从完整原配置形成目标请求，准确处理“保持凭据”和“替换凭据”。连接测试与保存是不同动作，测试成功不等于已保存，也不保证未来持续可用。删除前展示已知引用，后端仍执行最终删除保护。

分层以现有动态配置为依据，支持创建、修改、启停和删除。被引用分层的编码及删除限制按后端执行；停用不清除历史模型归属，修改规划建议也不等于执行数据迁移。

### 8.5 发布、建表与删除

发布前检查物理状态。MANAGED 缺表时明确披露本次发布可能创建表；单独建表不会发布模型。删除模型只删除管理元数据且需满足生命周期与引用保护，确认卡明确保留外部物理表。错误返回后如存在已发生的外部动作，按 PARTIAL 或 UNKNOWN 处理，不能简单宣称“失败且未改变任何内容”。

## 9 代码结构及 Java 改动上限

```text
data-scalpel-ui/src/modules/ontology-agent/
  api/                         聊天与操作方案接口
  hooks/                       会话、事件、操作草稿状态
  model/                       前端类型与展示转换
  components/                  对话、查询结果、方案编辑、确认、回执
  index.ts

integrations/ontology-agent/
  ontology/
    manifest.yaml
    layer/                     对象、关系、规则说明、能力定义
    datasource/
    model/
    references/                目录及既有码表等最小引用
  src/
    dsh/                       插件装配、Bridge、会话、事件、身份租约
    definition/                加载、引用校验、定义查询
    capabilities/              显式注册及 layer/datasource/model 处理器
    client/                    内部 REST 客户端、用户身份接入、接口类型与校验
    operations/                方案、预览、修订、执行、回执、持久化
  presets/datascalpel-ontology/
  tests/

data-scalpel-business/.../business/ontologyagent/  仅解耦身份检查时的待批备选
  web/resource/                一个只读身份检查 Resource
  web/response/                身份响应；首选接入不创建这些文件

deploy/ontology-agent/         选用新插件的部署覆盖、包构建与说明
```

以上为职责地图，按实际代码量建文件，不预建空目录。沿用现有单 React 应用和 Maven Reactor；不新拆业务模块、不抽象通用 Agent 工厂。

Java 目录及下述接口均为候选。任何 Java 新增或修改必须事先获得用户明确确认，范围讨论及本文评审通过不能替代这项确认。当前只允许继续设计、只读核对及文档修订。

分层/数据源/模型业务层不新增同义功能。当前首选接入已给出 Java 零新增的静态可行路径，但尚未运行验证；DshService.ensure 仍通过原托管凭据预配，因此新会话不走该方法。具体替代路径、依赖和失败时处理见配套接入文档，不能将候选 Java 目录视为必建。

Java 允许的增量限于：实现信息、确认方案的可信转发、经核对确有必要的 REST 用户身份接入。能复用的公开身份/Bridge 服务保持原实现，不从 Java 调用插件业务内部代码。不因 MCP 目录限制新增业务接口。若需要 DSH 接入兼容修复，应先说明证据和最小改动，不以本方案授权重构整个旧接入层。

首选浏览器路径集中在新插件 `/api/ontology-agent/v1`，通过同源代理访问；方案、字段编辑、敏感输入和原生交互回答都由此接入。固定处理器消费原生确认，没有面向模型的 confirm/commit 工具。不新增 Java 数据源列表适配或方案转发 Resource。业务操作归属和当前修订由插件检查，模型不能通过任意 HTTP 工具调用交互端点。

保留原有 `modules/dsh`、`integrations/dsh-plugin` 和 Business `dsh` 源码。暂不从它们抽公共库；新插件需承担必要的 Bridge 兼容实现成本，不能把“复用 DSH”误解成无需实现会话接入。

### 9.1 DSH 原生 Web 与交互能力复用评估

固定版本评估后的建议是：以现有 DataScalpel 聊天界面为基线，运行时复用 DSH 原生交互。原生 Web 可通过扩展点定制外观，但其登录、Remote 通信和业务用户范围仍需适配；目前没有依据把它视为零开发的嵌入式聊天组件。完整依据和路线比较集中在[配套文档第 2.1 节](ontology-agent-integration-and-capabilities.md#21-原生-web-裁剪评估结论)。需要区分三个层次：

| 层次 | 已有依据 | 本方案判断 |
| --- | --- | --- |
| 会话、消息、追问及回答机制 | 本仓现有插件使用 DSH sessions、userQuestions | 新插件继续使用这些服务，经自身认证通道绑定操作修订与真实回答，不复用旧 MCP 预配链 |
| 原生 Web 追问、批准/拒绝 UI | 有独立 UI 插件，但依赖客户端宿主服务 | 本期不直接引入；借鉴交互，在现有聊天基线上适配 plan-review 与业务卡 |
| 整个 DSH Web 页面 | 有布局插槽、自身启动令牌/Cookie 及 Remote 通道 | 能定制，但本期不选为产品入口；额外接入成本与节省前端开发的目标不匹配 |

新模块有限复制旧聊天展示代码并适配 API/事件，不越过模块 index 引用旧私有组件，不改旧模块来提取公共库。公共能力仍从 shared 复用，接受部分展示代码需要分别维护。产品仅保留当前对话、新对话及按需打开的个人历史，不显示完整工作区树或全实例会话列表。对用户仍只有一个助手入口，由部署决定实现。

原生确认适合表达一次允许/拒绝；本体插件仍需绑定具体操作、参数及修订，处理过期、重复提交和执行回执。原生工具权限批准不自动等于业务方案批准，通用权限模式或自动批准配置不能跳过本方案要求的人工业务确认。模型字段编辑、连接密码填写、DDL 差异等不应强塞成普通文本追问。

确认应优先由固定能力处理器发起并消费真实交互结果。可行的复用流程为：插件冻结操作方案 → 经已有交互通道展示具体内容 → 用户提交 → 插件校验交互与修订绑定 → 调用现有 REST。取消、失效或没有回答均不得授权执行；浏览器只隐藏卡片不等于确认。若原生等待仅适合当前轮次，跨重启的业务待办仍由操作记录承担，不宣称原生批准请求已经提供持久业务审批。

本轮已读取 DSH 0.1.5-rc.1 的发布包，六个相关包内容校验和与仓库锁文件一致。确认 plan-review、原生 UI 服务依赖和按路径复用 workspace 的契约；详见配套文档第 2 节。没有运行应用，不以静态契约核对代替同源接入及恢复流程验证，也不顺带升级 DSH。

参考：[原生 Web 部署说明](../../deploy/dsh/README.md)、[现有追问适配](../../integrations/dsh-plugin/src/interactions.ts)、[官方 Web 批准 UI](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/client/ui-approval/README.md)、[官方 Web 追问 UI](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/client/ui-user-questions/README.md)、[官方批准服务](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/interaction/user-approval/README.md)。

## 10 部署选择与 Bridge 兼容

建议唯一部署选择为 `existing | ontology`，未配置时为 existing。同一部署变量生成前端启动配置及插件装配；与 DSH capabilities 握手核对 implementationId 和协议版本，冲突时助手不可用，不自动回退另一套写入机制。不为开关新增 Java 配置服务；变量名称和注入方式随部署接入固化。

现有插件的 capabilities 没有 implementationId。兼容判断放在新增接入代码中：配置为 existing 时，允许已知旧协议缺少该字段；配置为 ontology 时，必须收到新插件明确的实现标识及所需能力。不能为了统一握手字段反过来要求修改旧插件，也不能把缺失标识当成本体实现已就绪。

首选方案复用 DSH 会话和原生交互语义，经新插件专属路径接入，不原样复用带 MCP 凭据预配的旧聊天链。内部 `/bridge/v3/capabilities` 及旧后台协调端点保留兼容响应，边界见配套文档第 3.3 节。前端仅在 AppShell 的助手装配点选择懒加载模块，用户始终看到“AI 助手”。无需预建 Java 确认转发。

### 必须先验证的工作区兼容条件

现有 `DshBindingService.ready` 持久化了用户 workspaceId，变化时会拒绝；因此“换成空白 DSH_HOME 的新实例，Java 零改动即可切换”目前不成立。

首选方案是固定当前兼容的 DSH 版本，保留既有 DSH_HOME 及 Workspace Registry，同一部署只加载选中的插件。新插件按同一用户根路径复用工作区标识，但使用独立 Preset、控制存储域和操作记录；不能扫描旧会话并自动接管。工作目录复用不构成进程级隔离。

这项复用必须以真实公开 Workspace API 的幂等行为和现有绑定完成验证。若无法保留标识，不改写原绑定来“修好”，应回到评审选择按实现独立绑定的最小适配；它不是首批默认承诺。

| 内容 | 切换规则 |
| --- | --- |
| DSH 核心 | 先沿用现有部署版本，不同时升级核心；不修改官方包 |
| 插件与 Preset | existing 使用旧包，ontology 使用新包；不同时注册冲突路由 |
| 用户身份及业务凭据 | 复用现有 Admin 用户及权限体系；REST 凭据委托按第 6 节核对，身份租约按兼容性复用；不建第二套账号体系 |
| 原生会话 | 新旧会话 ID、Preset 和控制索引分开；切换不迁移或重放旧对话 |
| 操作状态 | 新插件自己的用户存储域持久化，浏览器缓存不是权威记录 |
| 回退 | 停止接收新操作，等待已发请求收敛或记为 UNKNOWN，再切换插件；保留新数据供核对 |

首批限定一个有状态 DSH 服务作为操作状态的单写入者，不承诺多副本并发执行。利用 DSH 支持的持久存储能力加串行控制，先持久化执行意图再调用外部接口。故障重启不能自动重放 EXECUTING。存储写入失败时拒绝开始业务写入；业务已成功但回执落盘失败时，下次按不确定结果处理。

现有每用户 4、全局 8 个活跃会话、15 分钟空闲释放，以及 Admin 5 秒续租/插件 10 秒失效等约束作为兼容基线核对，不在本项目中悄悄改变。首批没有子智能体，因此不引入额外子任务容量体系。

## 11 本体定义与契约的维护

定义只在 `integrations/ontology-agent/ontology` 维护一份。DSH 和新前端通过插件返回的数据读取，不在 Java resources 再存副本，也不提供首批在线定义编辑。

每次发布包含定义版本和能力绑定修订；未执行方案绑定当时版本，升级后契约或执行映射变化则重新核对，不能让旧确认自动授权新实现。

本体保存业务语义、字段引用、关系解析能力和动作说明。底层 API 请求/响应类型对齐现有 REST DTO 和 OpenAPI；业务能力中仅维护真实需要的输入组合、语义收窄和参数绑定。接口契约指纹如采用，应基于发布时核对的相关 REST 契约，不依赖 MCP 描述接口。未来增加能力需要定义与显式处理器共同注册，禁止定义文件指定任意 URL、模块路径或可执行代码。

既有平台的“业务建模/本体总览”管理的是业务对象类型等资产，与本方案的 Agent 操作语义定义不同。本期不读写或迁移它的本体数据，不把两个同名概念混为一个子系统。

## 12 实施顺序及评审检查

| 阶段 | 交付 | 退出条件 |
| --- | --- | --- |
| A 定义评审 | 对象关系、规则分类、动作目录、首批范围及 API 映射 | 业务含义正确，三类用途和 MANAGED/EXTERNAL 分清，不新增分层存储关系 |
| B 接入验证 | 新插件骨架、统一入口部署选择、Bridge/工作区兼容、REST 用户身份与只读能力 | 旧实现不改，工作区 ID 可复用，权限不足时明确报告；新链路不依赖 MCP，新旧历史不混用 |
| C 三维度只读 | 分层、数据源、源表、模型及物理状态查询与结果展示 | 同名消歧、分页完整性、未知状态、权限失败均正确表达 |
| D 完整管理操作 | 分层及数据源增删改查/启停/测试，模型创建、修改基本信息及字段、停用和删除 | 前后差异准确、秘密不进入对话、完整字段替换正确、生命周期和引用保护沿用后端 |
| E 发布与物理操作 | 发布、建表、物理变更计划生成/查询/取消/执行 | 复用后端计划和检查，真实呈现 DDL 副作用、并发漂移和部分结果 |
| F 故障与部署评审 | 重启恢复、重复提交、结果不确定、切换与回退说明 | 不自动重放写入，不假报成功，旧助手路径保持可用 |

以上均为首批内部实现顺序，不代表将 D/E 阶段另推到下一批。建议评审用例包括：自定义分层与停用分层；同名数据源；新连接及秘密修改；类型切换；完整字段替换与并发编辑；引用保护；普通 JDBC 用途的 EXTERNAL 绑定；源表位于非默认命名空间；LOSSY 映射；不存在/不可达的物理表；发布时建表；物理计划失效及 PARTIAL；模型删除保留物理表；确认后字段或目标变化；重复点击；成功但回查失败；提交中断后恢复；权限撤回；旧会话回退。

这些是建议的产品验收范围，不恢复根 AGENTS 暂停的强制测试政策。本次没有运行应用、测试或外部接口；实施时若进行功能验证，必须使用根 `start-local-dev.sh` 所管理的开发环境。

## 13 本轮评审结论与剩余设计事项

1. **范围已调整**：首批包括三个领域的完整管理动作，不再仅交付查询和创建草稿；明细见第 1、5、6 节。
2. **职责分配已接受，接入方案已细化**：本体、能力编排、操作状态在 DSH TypeScript 插件，直接调用现有 REST。业务层零新增；首选接入不新增 Java，静态依据、具体耦合和待运行验证项已列入配套文档，不预先建设封装层。
3. **待理解后评审**：Preset 是 Agent 运行配置，解释见第 5.3 节。界面确认及不向模型开放通用执行工具仍为建议，本轮没有将用户的术语疑问记为同意。
4. **已接受**：保留 DSH 原工作区标识，独立会话和控制域，兼容性核对先于业务写入能力实施。
5. **已接受**：首批单 DSH 单写入者，不保证跨所有请求恰好执行一次，不自动回滚多次业务调用。
6. **新增明确约束**：任何新增或修改 Java 代码均须提前与用户确认；先完成 DSH 原生交互及现有通道的复用评估，不把候选确认转发接口视为必做。
7. **本轮新增选型建议，待用户评审**：原生 Web 裁剪评估已完成；建议采用现有产品聊天基线，在新模块有限复制并适配。保留一个助手与个人历史入口，运行时使用 DSH，旧实现保持原样；不把界面选型结论当作接入运行验证。

后续设计需要将完整动作清单细化到输入和确认卡，并落实身份/确认接入选择；当前仍处于设计阶段，不据此开始实现代码。

## 附录 现状核对入口

- [当前 DSH 入口](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/dsh/web/resource/DshResource.java)、[绑定及 workspaceId 校验](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/dsh/service/DshBindingService.java)、[身份租约](../../integrations/dsh-plugin/src/admin.ts)、[原生工作区接入](../../integrations/dsh-plugin/src/workspaces.ts)。
- [分层 Resource](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/model/web/resource/ModelWarehouseLayerResource.java)、[分层 Service](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/model/service/ModelWarehouseLayerService.java)。
- [数据源 Resource](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/datasource/web/resource/DataSourceResource.java)、[类型 Resource](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/datasource/web/resource/DataSourceTypeResource.java)。
- [模型 Resource](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/model/web/resource/DataModelResource.java)、[原子受管草稿输入](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/model/web/request/CreateManagedDraftRequest.java)、[来源结构预览输入](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/model/web/request/ManagedImportPreviewRequest.java)、[物理检查响应](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/model/web/response/PhysicalTableInspectionResponse.java)。
- [现有令牌的 REST 访问限制](../../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/systemmcp/security/SystemMcpHandlerGuard.java)、[安全链选择](../../data-scalpel-admin/src/main/java/cn/superhuang/data/scalpel/admin/security/SecurityConfiguration.java)。这些是身份接入核对依据，不是新 Agent 采用 MCP 的理由。
- [当前助手挂载点](../../data-scalpel-ui/src/app/layout/AppShell.tsx)、[现有部署覆盖](../../deploy/dsh/docker.cordis.patch.yml)。
- 旧方案参考：本机 `E:/webStudy/scp-agent/src/core/runtime.ts`、`src/interaction/store.ts`、`src/interaction/preview.ts`、`src/core/ontology-graph.ts`。仅参考代码结构与控制经验，不作为本系统的业务事实来源。
