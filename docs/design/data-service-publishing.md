# 数据服务定义、Engine 启用与查询运行设计

## 目标与模式

数据服务提供三种创建后不可切换的模式：

- `STANDARD_TABLE`：绑定一个已发布模型，将模型对应的单张物理表启用为字段白名单约束的标准查询服务。
- `SQL_QUERY`：先绑定一个已启用且声明 `SQL_SERVICE_QUERY` 能力的 JDBC 数据源，再关联该数据源下的一个或多个模型，并将一条启用时冻结的只读参数化 SQL 模板部署到已注册该数据源的目标 Engine。当前支持 PostgreSQL、HighGo、MySQL、openGauss、人大金仓、达梦、Oracle、SQL Server 与 ClickHouse。
- `SCRIPT_API`：绑定一个已启用的 JDBC 数据源和已注册该数据源的目标 Engine，将 Groovy 脚本交给 Engine 内嵌的 API Studio 保存并注册为公开 `POST` 路由。

SQL 服务不是任意 SQL 执行接口。调用方不能提交 SQL、表名、列名或 SQL 片段，只能向启用快照中声明的标量参数传值。第一版不支持写入语句、列表参数、动态 SQL、标识符参数、自定义 count、路径/Header 参数或自定义响应模板。

SQL 服务关联的模型只用于来源说明、血缘记录、编辑辅助和删除保护，不是 SQL 表访问白名单。第一版不解析 SQL 中的表名，也不要求实际查询表必须对应已选模型；只要查询通过现有只读、参数、类型、元数据和数据库执行检查即可。数据库账号的只读权限仍是实际安全边界。

脚本服务第一版固定使用 Groovy、`POST`、`/open-api/v1/` 静态路径和一个默认数据源。脚本作者视为可信，允许查询和写入，并保留 API Studio 当前的 `db`、`log`、`Assert`、`Utils`、`Pager`、事务、日志与 SQL Trace 行为；暂不提供沙箱、超时中断、编译缓存、多数据源依赖、请求/响应 Schema、灰度发布或其他脚本语言。

一个 `ServiceEngine` 是独立 JVM 和独立 PostgreSQL 运行库，不为每个服务启动进程。Admin 是控制面，Engine 是运行面；Engine 持久化无凭据的服务快照，数据源配置、运行时连接和已发布脚本统一交给内嵌的 API Studio 管理，不访问 Admin 数据库。API Studio 当前按自身既有方式明文保存数据库密码。

## 管理领域模型

`DataService` 只保存编码、名称、目录、不可修改的类型、Engine、`contextPath` 和生命周期状态。`contextPath` 是服务在 Engine 上的实际访问路径，映射到数据库 `route_path`，同一 Engine 内唯一。具体定义分表保存：

- `StandardDataServiceDefinition`：`dataServiceId`、`modelId`、定义版本。
- `SqlDataServiceDefinition`：`dataServiceId`、`dataSourceId`、大文本 `sqlText`、定义版本。
- `SqlDataServiceModelReference`：标量 `dataServiceId`、`modelId` 和关联顺序；同一服务内模型及顺序分别唯一。
- `SqlDataServiceParameter`：标量 `dataServiceId`、参数名、平台类型及长度/精度/scale、必填标记、顺序和说明。
- `ScriptDataServiceDefinition`：`dataServiceId`、`dataSourceId`、大文本 `script`、定义版本。

实体之间只使用 UUID 标量引用，不使用 JPA Entity 关联或级联。删除服务前必须已经从 Engine 停用且不存在网关绑定，再由业务 Service 显式删除模型引用、参数、具体定义、部署记录和根实体。模型删除同时检查标准服务定义和 SQL 服务模型引用；数据源和 Engine 数据源注册的删除保护同时检查 SQL 与脚本定义中的执行数据源。

旧版数据服务状态 `PUBLISHED` 的实际含义是“已部署到 Engine”，Admin 启动时会迁移为 `ENABLED`，已有 Engine 部署快照继续保留。网关发布是独立的新状态，升级后需要对已启用服务执行一次“发布到网关”。完整生命周期与 Provider 映射见[数据服务启停与网关发布设计](data-service-gateway-publishing.md)和[Super API Gateway Provider 集成](super-api-gateway-provider-integration.md)。

## Admin API

数据服务统一使用以下资源：

- `GET /api/v1/data-services`：摘要分页，包含 `type`、`definitionConfigured`、`definitionVersion`、`sourceId`、`sourceName`，不返回 SQL 大文本。
- `GET /api/v1/data-services/{id}`：完整详情和可空的具体定义。
- `POST /api/v1/data-services`：创建基础信息；允许不携带具体定义形成可恢复草稿。
- `POST /api/v1/data-services/{id}/actions/update`：更新基础信息；未携带定义时保留已有定义。
- `POST /api/v1/data-services/{id}/actions/update-definition`：按服务固定类型整体新增或更新具体定义。
- `GET /api/v1/data-services/{id}/standard-model-candidates`：分页查询当前 Engine 下的标准单表模型候选，可选择包含不可用项及原因。
- `GET /api/v1/data-services/{id}/related-models`：一次返回有序关联模型摘要；要求 `service.view` 与 `model.view`，标准单表返回一个 `PRIMARY`，SQL 返回有序 `REFERENCE`，脚本或未配置定义返回空列表。模型已删除时仍返回引用 ID，并标记 `resolved=false`。
- `GET /api/v1/data-services/{id}/lineage/table`：以服务为根查询标准服务关联模型及其表级上游血缘。
- `GET /api/v1/data-services/{id}/lineage/fields/{fieldId}`：查询标准服务实际暴露字段及其字段级上游血缘。
- `POST /api/v1/data-services/{id}/lineage/actions/query-fields`：批量查询实际暴露字段及其上游路径，默认前 20 个、最多 50 个。
- `POST /api/v1/data-services/actions/test-sql`：测试未保存表单定义并返回实际查询预览。
- `POST /api/v1/data-services/actions/execute-script-draft`：在所选 Engine 中执行未保存脚本，不保存 API Studio 数据，也不注册公开路由。
- `GET /api/v1/data-services/script-completion`：按 Engine 和数据源代理 API Studio 的 Groovy、变量和数据库补全数据。
- `POST /api/v1/data-services/{id}/actions/enable`：冻结定义并部署到 Service Engine。
- `POST /api/v1/data-services/{id}/actions/publish`：将已启用服务发布到当前网关。
- `POST /api/v1/data-services/{id}/actions/disable`：先撤回全部网关对象，再从 Service Engine 移除。
- `POST /api/v1/data-services/{id}/actions/cleanup-deployment`
- `POST /api/v1/data-services/{id}/actions/delete`

创建与基础更新为了兼容原调用仍接受显式互斥定义，但三个定义全部为空表示只保存基础信息，不会清除已有定义。独立定义更新必须且只能提供一种与服务固定类型匹配的定义：`STANDARD_TABLE` 使用 `standardDefinition.modelId`，`SQL_QUERY` 使用 `sqlDefinition`，`SCRIPT_API` 使用 `scriptDefinition`。首次保存定义产生 v1，内容实际变化后才递增版本；部署 `revision` 仍只在启用时增长。未配置定义的服务可以查看、修改和删除，但启用返回 409“数据服务定义未配置”。

Engine 属于基础信息，可以独立修改。修改 Engine 不清空或改写原定义；定义页保留原值并展示不兼容原因。重新保存定义和启用时，后台都按当前 Engine 校验数据源 READY 注册。标准单表定义还要求模型已发布、存在字段且绑定有效 JDBC 存储数据源。

SQL 服务先在基础信息中选择 Engine，再在定义页选择该 Engine 已就绪且声明 `SQL_SERVICE_QUERY` 能力的数据源 → 该数据源下的一个或多个模型 → SQL 和参数。当前支持 PostgreSQL、HighGo、MySQL、openGauss、人大金仓、达梦、Oracle、SQL Server 与 ClickHouse。`modelIds` 至少一个、不能包含空值或重复值，且所有模型的 `storageDataSourceId` 必须与 `dataSourceId` 相同。草稿、已发布和已停用模型均可关联，模型状态不参与保存、测试或启用判定。切换数据源后必须重新选择关联模型；切换 Engine 时保留原定义并由用户在定义页调整。

标准单表与 SQL 共用数据服务模块内的模型选择工作区，统一目录树、名称/编码搜索、状态/数仓分层/数据源筛选、分页表格和跨页选中语义，但由各自业务外壳提供候选查询与提交规则。SQL 不再一次加载固定上限的模型到多选下拉框，而是复用通用 `/api/v1/models` Search API，并始终附加当前 SQL 服务数据源条件，以每页 20 条执行服务端分页。SQL 选择 Drawer 提供“全部模型/已选模型”视图，选择先保存在 Drawer 草稿中，取消不修改定义，确定后才写入有序 `modelIds`；原有关联顺序保持，新模型按选择顺序追加。

已保存但不在当前候选页的 SQL 模型由 `related-models` 摘要接口回显。模型已删除、摘要无法加载或模型已不属于当前数据源时，原引用不会被静默清空；选择器会保留并解释问题，在用户移除或修复前禁止确认。切换数据源且存在关联模型时，页面必须先确认将清空关联模型、测试参数值和测试结果；取消后保持原数据源和编辑状态。

SQL 定义示例：

```json
{
  "code": "customer_query",
  "name": "客户查询",
  "engineId": "00000000-0000-0000-0000-000000000001",
  "contextPath": "/open-api/v1/customers",
  "type": "SQL_QUERY",
  "standardDefinition": null,
  "sqlDefinition": {
    "dataSourceId": "00000000-0000-0000-0000-000000000002",
    "modelIds": [
      "00000000-0000-0000-0000-000000000003",
      "00000000-0000-0000-0000-000000000004"
    ],
    "sqlText": "select id, name from customer where department_id = :departmentId",
    "parameters": [{
      "name": "departmentId",
      "typeDefinition": {"type": "LONG", "length": null, "precision": null, "scale": null},
      "required": true,
      "description": "部门 ID"
    }]
  }
}
```

SQL 测试请求同样必须提供 `dataSourceId`、`modelIds`、SQL 和参数定义，并额外提供 `arguments` 和 1～50 的 `previewSize`。数据源或模型选择错误使用 RFC 9457 Problem Details 400；SQL 内容、参数、类型、元数据或执行失败属于正常测试结果，返回 HTTP 200 和 `valid=false`、稳定 `problems`。成功结果返回输出字段、固定第一页预览和 `elapsedMs`，不持久化输出快照，也不代替启用时重新检查。

脚本定义示例：

```json
{
  "code": "customer_script",
  "name": "客户复合服务",
  "engineId": "00000000-0000-0000-0000-000000000001",
  "contextPath": "/open-api/v1/customer-script",
  "type": "SCRIPT_API",
  "standardDefinition": null,
  "sqlDefinition": null,
  "scriptDefinition": {
    "dataSourceId": "00000000-0000-0000-0000-000000000002",
    "script": "log.info(\"hello\")\nreturn [message: bodyRoot?.message ?: \"ok\"]"
  }
}
```

草稿调试请求携带 `engineId`、`dataSourceId`、`routePath`、脚本、测试 JSON，以及可选 query/header。Admin 只负责鉴权和代理，Engine 调用 API Studio 的内存草稿执行门面；整个过程不保存脚本、不写历史、不注册公开路由。响应透传执行结果、日志、结构化日志事件、SQL Trace、事务状态、耗时和带行列位置的错误诊断。

脚本可用 `log.debug("阶段={} 行数={}", stage, count)` 辅助排查；当前 API Studio 的 `LogFunction` 独立采集 `source=SCRIPT` 日志到 `logEvents` 和兼容的 `logs`，不依赖控制台 DEBUG 是否开启。`log.error` 本身不会改变执行状态，脚本日志也没有自动脱敏或条数上限。SQL Trace 最多保留 200 条，`sqlCount` 为已保留条数；当前报告不主动设置 `truncated`，不能用 false 证明完整性。第三方 Agent 的阶段日志示例、排查顺序和最终版本补测约定见 [服务开发 Skill：通过日志定位问题](../../skills/datascalpel/references/service-development.md#通过日志定位问题)。

## 管理端创建与详情工作台

数据服务列表的“新建服务”使用类型下拉菜单，不预设默认类型。选择类型后在列表右侧 Drawer 中维护编码、名称、目录、说明、Service Engine 和服务 `contextPath`；确认后立即形成可恢复草稿并留在当前列表，不自动进入服务定义页。访问方式不属于服务定义，仅在发布网关时配置。用户随后从创建成功提示、列表“继续配置”或详情“服务定义”页签手动进入定义编辑器。列表和详情明确展示“定义未配置”并禁止启用。

对应前端路由为：

| 路由 | 用途 | 页面权限 |
| --- | --- | --- |
| `/dataservice` | 数据服务列表 | `service.view` |
| `/dataservice/:id` | 查看详情和执行生命周期操作 | `service.view` |
| 数据服务列表或详情中的基础信息 Drawer | 新增或修改基础信息，保存后留在当前上下文 | `service.create` / `service.update` |
| `/dataservice/:id/edit` | 旧地址兼容跳转到基本信息详情 | `service.view` |
| `/dataservice/:id/definition/edit` | 配置或修改服务定义 | `service.update` |

创建 Drawer 主操作为“创建服务”，不提供“保存并启用”。创建成功后关闭 Drawer、刷新列表，并在成功提示中提供“配置定义”快捷入口；不改变当前筛选、目录和分页上下文。列表将 `keyword`、`status`、`type`、`engine`、`directory`、`page` 和 `size` 写入 URL；`directory=uncategorized` 表示未分类。进入详情或定义编辑器再返回时，筛选、目录和分页位置能够恢复。

详情页签固定为“基本信息、服务定义、关联模型、血缘分析、运行与发布”。标准单表定义页复用模型管理的全高目录与结果工作区，直接内嵌名称/编码、数据源和数仓分层筛选以及服务端分页单选表格，不再通过二级 Drawer 选择；默认只返回可发布模型，选择表格行后立即更新当前表单定义。已选模型在结果面板底部固定摘要栏展示 Schema、字段数、主键数和 Engine 兼容状态，完整字段通过模型详情查看。开启“显示不可用模型”后，未发布、无字段、数据源无效或当前 Engine 注册未就绪的模型保留在结果中但禁止选择并展示原因；不会批量加载所有候选字段。

“血缘分析”使用正式血缘查询，不生成数据源、网关或消费者示例节点。第一版只支持标准单表服务：服务节点作为模型的终端消费者，通过 `EXPOSES` 连接模型或字段。服务启用到 Service Engine 成功后进入模型正式血缘，与是否发布到 API 网关无关；停用后仍展示，修改关联模型后立即切换到当前定义。草稿服务只在自身详情展示当前关联模型。已启用服务的字段级关系以当前 Revision 的部署字段白名单为准，与模型当前字段不一致时只连接可精确确认的字段并标记陈旧。字段级画布按所属表卡片组织字段行，默认选择可精确匹配的前 20 个字段，支持最多 50 个多选和路径聚焦；未暴露字段保留独立摘要但不伪造 `EXPOSES`。SQL 和脚本服务显示暂未接入血缘。

详情页根据服务端状态计算交互模式，不在前端提前猜测生命周期结果：

| 服务与部署状态 | 定义模式 | 可用操作 |
| --- | --- | --- |
| 草稿且无未清理部署 | 可编辑 | SQL 测试或脚本调试、保存、启用 |
| 已停用且部署为 `REMOVED` | 可编辑 | SQL 测试或脚本调试、保存、重新启用 |
| 已启用、Engine 为 `DEPLOYED`、尚未发布网关 | 只读 | SQL 测试或脚本调试、发布到网关、停用 |
| 已启用、当前 revision 已发布网关 | 只读 | SQL 测试或脚本调试、重新发布、复制网关 cURL、停用 |
| Engine 为 `PENDING` 或 `FAILED` | 部署锁定 | SQL 测试或脚本调试、重试启用、清理 Engine 部署 |
| 其他未完成移除状态 | 部署锁定 | 展示状态和错误，等待处理 |

权限会继续叠加到以上状态：保存要求 `service.update`，详情页 SQL 测试要求 `service.update`，启用、发布到网关、停用、重试和清理要求 `service.publish`。标准服务创建还要求模型和 Engine 查看权限；SQL 服务创建还要求数据源查看权限。缺少依赖资源查看权限时页面持续显示提示，并禁用相关选择和维护操作。

SQL 工作台在桌面端采用左右布局。左侧顶部固定当前 Engine 中已就绪且声明 `SQL_SERVICE_QUERY` 能力的数据源和关联模型操作区，下方在独立滚动区域直接展示全部已选模型的名称、编码、状态和可复制物理位置，不再截断为前若干项；数据源不会随模型列表滚动。每个有效模型可以就地展开字段结构，同一时间只展开一个，首次展开时通过模型详情接口按需加载并缓存字段、平台类型、可空性、主键和说明，不批量请求所有模型字段。常驻解释性提示不占用左栏空间，仅在模型引用失效、数据源与 Engine 不兼容或权限不足等需要处理的状态下显示问题。更多模型通过约 1080px、窄屏自适应的选择 Drawer 管理。关联模型仍只用于来源说明和血缘，不构成 SQL 访问白名单。右侧使用可拖动的上下分栏，上层维护 Monaco SQL、参数定义和临时测试值，下层展示测试状态、问题和预览数据。预览表列头以两行合并展示字段名、平台类型与可空性，不再单独展示输出字段表。下层可整体收起并保留紧凑状态栏，展开高度由浏览器保存并可双击分隔条复位；编辑器外层不滚动，左侧、上层和结果表分别管理内部滚动。Engine 等基础信息在新增或编辑 Drawer 中维护，窄屏仍使用安全的单列布局。SQL 编辑页面及 Monaco 均通过路由动态加载，不进入数据服务列表首屏包。

脚本工作台由 API Studio 的 `@superhuang/super-api-studio-script-workbench` 包提供，DataScalpel 通过固定在 `data-scalpel-ui/vendor` 的 `3.0.0-SNAPSHOT` tarball 使用，不使用 iframe，也不复制 Monaco Groovy 语言实现。定义页左栏选择当前 Engine 中已就绪的默认 JDBC 数据源并维护请求 Example，右侧完整宽度交给受控 `ScriptWorkbench` 管理脚本编辑、执行结果、日志、SQL Trace、耗时和错误定位；执行时使用左栏当前选中的 Example。脚本编辑器与执行结果之间支持上下拖动调整高度、键盘微调和双击恢复默认值，并在本地记忆高度偏好。补全和执行均由浏览器访问 Admin，再由 Admin 访问目标 Engine，浏览器不直接接触 Engine 地址或 Management Token。

页面仅对持久化服务定义计算未保存状态；SQL 测试参数、脚本测试 JSON、问题和执行结果不参与 dirty fingerprint，也不会随服务保存。站内跳转使用路由 blocker，刷新、关闭标签页和浏览器离开使用 `beforeunload`。启用或重新启用前若存在未保存修改，必须先保存。保存校验失败时显示错误摘要并定位第一个错误字段。

## SQL 模板与类型边界

参数名格式为 `[A-Za-z][A-Za-z0-9_]{0,63}`。方言层命名参数编译器只在 SQL 代码区识别 `:name`，会跳过字符串、注释、引号标识符和 PostgreSQL dollar quote，并正确保留 `::` cast。同一参数可以重复出现，部署快照按占位符出现顺序保存参数名。

固定限制：

- SQL 最长 100000 字符。
- 最多 50 个参数定义和 200 次占位符出现。
- 只允许一条 `SELECT` 或 `WITH ... SELECT`。
- 拒绝顶层 `LIMIT`、`OFFSET`、`FETCH` 和锁定查询。
- 拒绝 `${}`、SQL 片段、列表展开和动态标识符。
- 声明参数与 SQL 使用参数必须完全一致。

参数使用 `PlatformTypeDefinition` 转换后由 `PreparedStatement` 绑定。可选参数缺失或显式 `null` 都绑定 JDBC NULL；未知参数、必填缺失/为 null、非标量或类型错误在公开接口返回 `INVALID_QUERY` 400。第一版拒绝 BINARY 和 Geometry 参数。

启用时通过 PreparedStatement 元数据冻结输出字段名称、顺序、平台类型和 nullable。输出列名称必须非空且大小写不敏感唯一，最多 200 列。物理类型映射为 `EXACT` 或 `NORMALIZED` 才能启用；`LOSSY`、`UNSUPPORTED`、BINARY、Geometry、JSON、ARRAY 和无法映射的类型均被拒绝，Geometry 输出使用稳定问题码 `SPATIAL_FIELD_UNSUPPORTED`。无法稳定推导参数类型时，SQL 作者应在模板中显式 cast。

## 部署快照

Admin 向 Engine 发送统一 `ServiceDefinitionSnapshot`，其中 `type` 与 `standardDefinition`、`sqlDefinition`、`scriptDefinition` 恰好一种匹配。

SQL 快照只包含：

- 协议版本。
- 编译后的 JDBC SQL。
- 占位符顺序对应的参数名。
- 参数及平台类型。
- 启用时冻结的输出字段。

Engine 的 SQL 快照不包含关联模型；运行面无需访问 Admin 模型。快照也不包含数据源密码、Admin 实体或测试参数值。服务部署摘要覆盖服务类型、路由、Engine、数据源、有序 `modelIds` 和完整 SQL 定义，不包含 revision。DataScalpel 控制面仍为本地部署状态机和网关发布维护 `DataService` revision，但不会把 revision 发送给 Engine。

脚本快照只保存单个默认数据源 ID 和 Groovy 源码。启用时 Engine 将快照转换为 API Studio `PublishedScriptApiDefinition`，固定映射 `ApiInfo.id = serviceId`、`method = POST`、`type = Ql`、`path/fullPath = routePath`、`datasource = dataSourceId.toString()`。相同 ID 直接覆盖；内容完全相同时不更新数据库、不写历史且不重复注册路由。DataScalpel 是脚本源数据，API Studio 保存的是部署副本；在 API Studio 页面直接修改的脚本会在 DataScalpel 下次启用时被覆盖。

Engine 的部署接口按 `serviceId` 直接创建或覆盖，最后一次成功写入生效；卸载接口按 `serviceId` 幂等移除，不再执行 revision 顺序检查，也不会返回 revision 冲突。Admin 仍按“短事务读取快照 → 事务外 JDBC 检查 → 短事务复核并持久化待部署快照 → 事务外调用 Engine → 短事务提交结果”执行启用。HTTP 超时不被当作确定失败；服务保留失败/不确定状态，可重试覆盖相同 ID，或调用 `cleanup-deployment` 向原 Engine 幂等移除。

## Engine 状态机与恢复

Engine 本地部署状态为：

| 状态 | 含义 |
| --- | --- |
| `DEPLOYING` | 快照已持久化，路由尚未确认注册 |
| `DEPLOYED` | 路由已注册 |
| `DEPLOY_FAILED` | 注册失败，不暴露路由 |
| `REMOVING` | 正在幂等注销路由 |
| `REMOVE_FAILED` | 注销未完成，等待重试或恢复 |
| `REMOVED` | 路由已注销 |

部署先持久化 `DEPLOYING`，再注册运行时路由，最后标记 `DEPLOYED`。标准与 SQL 服务由 `DynamicServiceRouteRegistry` 注册或替换 Spring MVC `POST` 路由；脚本服务不进入该注册表，由 API Studio 保存 `ApiInfo` 并注册路由。两套路由在注册前检查现有 Spring Mapping，禁止不同服务占用相同 `POST` 路径。注册失败会撤销该服务的运行时路由并标记 `DEPLOY_FAILED`，绝不返回成功。Engine 停用先标记 `REMOVING`，幂等注销对应路由；脚本服务同时幂等删除 API Studio `ApiInfo`，最后标记 `REMOVED`，失败则记录 `REMOVE_FAILED`。

启动时先由 API Studio Bootstrap 从自身配置表恢复数据源和已发布脚本路由，再逐一处理服务：`DEPLOYED`、`DEPLOYING` 重新校验并注册，脚本服务通过幂等 `upsert` 只修复缺失或不同的配置；`DEPLOY_FAILED` 不自动暴露；`REMOVING`、`REMOVE_FAILED` 确保注销并完成为 `REMOVED`。单个服务恢复失败会记录完整日志和失败状态，不影响其他服务或应用启动。

Engine 接收 SQL 快照时再次检查只读单语句、占位符数量、参数顺序、输出快照和数据库 `SQL_SERVICE_QUERY` capability。当前 PostgreSQL、HighGo、MySQL、openGauss、人大金仓、达梦、Oracle、SQL Server 与 ClickHouse 声明该 capability；这些数据库均可注册到 DataScalpel Service Engine，并承载标准表、SQL 查询和脚本服务。

## 公开调用协议

标准与 SQL 两种查询模式返回相同分页 JSON。标准单表服务使用固定 V1 查询协议，字段编码只能引用发布快照中开放的字段，不接受物理列名或 SQL：

```http
POST /open-api/v1/orders
Content-Type: application/json

{
  "pageNo": 1,
  "pageSize": 20,
  "fields": ["id", "name", "department"],
  "filter": {
    "operator": "AND",
    "conditions": [
      {"field": "status", "operator": "EQ", "value": "ACTIVE"},
      {
        "operator": "OR",
        "conditions": [
          {"field": "age", "operator": "GTE", "value": 18},
          {"field": "level", "operator": "IN", "value": ["A", "B"]}
        ]
      }
    ]
  },
  "sort": [{"field": "createdAt", "direction": "DESC"}],
  "groupBy": [],
  "aggregates": [],
  "returnCount": false
}
```

`filter` 可省略；存在时根节点必须是非空 `AND` 或 `OR` 条件组。单值操作符为 `EQ`、`NE`、`GT`、`GTE`、`LT`、`LTE`、`LIKE`、`NOT_LIKE`；`IN`、`NOT_IN` 使用非空数组；`BETWEEN`、`NOT_BETWEEN` 使用两个元素的数组；`IS_NULL`、`IS_NOT_NULL`、`IS_EMPTY`、`IS_NOT_EMPTY` 不传 `value`。默认最多 50 个叶子条件、5 层嵌套。`fields` 为空时返回全部可查询字段；聚合项使用 `function`、`field`、`alias`，仅 `COUNT` 支持 `field: "*"`。

SQL 服务请求示例：

```http
POST /open-api/v1/customers
Content-Type: application/json

{
  "pageNo": 1,
  "pageSize": 20,
  "arguments": {"departmentId": 1001, "keyword": null},
  "returnCount": false
}
```

响应：

```json
{
  "pageNo": 1,
  "pageSize": 20,
  "totalCount": null,
  "items": [{"id": 1001, "name": "示例客户"}]
}
```

- `pageNo` 默认 1；`pageSize` 默认 20、最大 100。
- offset 为 `(pageNo - 1) * pageSize`，检查整数溢出且不能超过 Engine `maximum-offset`。
- `returnCount` 默认 false；为 true 时先执行 `SELECT COUNT(*) FROM (<baseSql>) ds_count`。
- PostgreSQL、HighGo、MySQL、openGauss、人大金仓与 ClickHouse 使用 `LIMIT/OFFSET`；达梦和 Oracle 使用 `OFFSET/FETCH`；SQL Server 额外补充稳定的分页 `ORDER BY` 后使用 `OFFSET/FETCH`。
- 基础 SQL 参数先绑定，分页参数最后绑定；count 只复用基础参数。
- BigDecimal 输出为字符串，日期和时间输出 ISO 字符串，null 保持 null。
- 每次执行都会比较实际 ResultSet 的字段名称、顺序、类型和 nullable；结构漂移返回安全 502。
- SQL/数据库执行失败返回 502；调用参数错误返回 `INVALID_QUERY` 400。

Engine 通过 `ApiDataSourceRegistry` 取得 API Studio 按数据源复用的连接池，并继续使用 read-only connection、语句超时和 `setMaxRows`。数据库账号必须在数据库侧具备只读权限；关联模型、词法检查和 JDBC read-only 都不能替代数据库权限，其中关联模型尤其不构成 SQL 访问白名单。未提供顶层 `ORDER BY` 时允许查询，但跨页顺序由数据库决定。

脚本服务接收普通 `POST` 请求，由 API Studio 将 JSON body、query、header、path variable、cookie 和 session 注入脚本环境。脚本返回值沿用 API Studio 当前结果包装规则，也可以返回 API Studio 支持的直接响应对象。公开执行失败时 Service Engine 启用嵌入模式异常传播，由 DataScalpel 的统一异常处理返回 RFC 9457 Problem Details，不再返回 HTTP 200 错误包装。脚本执行不使用 SQL 服务的 read-only connection 限制，数据库事务与提交/回滚语义沿用 API Studio 当前行为。

## Engine 配置

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `DATASCALPEL_ENGINE_QUERY_DEFAULT_PAGE_SIZE` | `20` | 默认分页大小 |
| `DATASCALPEL_ENGINE_QUERY_MAXIMUM_PAGE_SIZE` | `100` | 最大分页大小 |
| `DATASCALPEL_ENGINE_QUERY_MAXIMUM_FILTER_COUNT` | `50` | 标准服务最大叶子过滤条件数 |
| `DATASCALPEL_ENGINE_QUERY_MAXIMUM_FILTER_DEPTH` | `5` | 标准服务最大过滤嵌套深度 |
| `DATASCALPEL_ENGINE_QUERY_MAXIMUM_IN_VALUES` | `1000` | 单个 IN/NOT_IN 条件最大值数量 |
| `DATASCALPEL_ENGINE_QUERY_MAXIMUM_OFFSET` | `100000` | 最大分页偏移 |
| `DATASCALPEL_ENGINE_QUERY_TIMEOUT_SECONDS` | `30` | JDBC 查询超时 |

其他必需配置包括 Engine 独立数据库连接、稳定 Engine 编码和 Admin/Engine 共享管理 Token；不再配置 Engine 数据源快照加密密钥。Engine 编码由 Engine 自身配置，必须以字母开头且只包含字母、数字和下划线，最长 64 位；Admin 登记 Engine 时通过 `/internal/v1/info` 自动发现并保存为不可修改的内部指纹，不由用户手工录入。首次登记会校验编码唯一性，后续修改管理地址或 Management Token 时会重新读取并核对该指纹，连接失败或指纹不一致时拒绝保存。管理接口位于 `/internal/v1/**` 并要求 Bearer Token；公开调用统一从已发布的网关 Proxy 地址进入，网关发布细节见[数据服务启停与网关发布设计](data-service-gateway-publishing.md)。

## PostgreSQL 实库验收

默认构建使用 H2 PostgreSQL 模式覆盖 Engine SQL 路由，不要求本机运行 PostgreSQL。设置 `DATASCALPEL_PG_INTEGRATION=true` 以及 `DATASCALPEL_PG_HOST`、`DATASCALPEL_PG_PORT`、`DATASCALPEL_PG_DATABASE`、`DATASCALPEL_PG_SCHEMA`、`DATASCALPEL_PG_USERNAME`、`DATASCALPEL_PG_PASSWORD` 后，`PostgreSqlSqlServiceIntegrationTest` 会在指定的可丢弃 schema 中创建随机表，并验证 PreparedStatement 元数据、标量和重复参数、显式 null、分页、count、read-only connection、BigDecimal 字符串以及日期时间 ISO 输出。测试结束始终删除随机表。

## 2026-09 部署并发约束

Service Engine 对同一服务的完整部署、卸载与启动恢复串行协调，包含运行路由修改及结果落库；运行锁不跨越数据库事务边界，也不把外部调用放进管理数据库事务。启动恢复在获得协调锁后重新读取当前部署状态，避免旧快照恢复已卸载路由。Engine code 仍标识单个运行实例，未引入多实例共享同一动态路由表的能力。

Admin 的每次启用/移除使用独立 operationId，包括复用同一 revision 的相同定义重试；准备与完成阶段持有服务行锁，旧 operationId 的结果返回冲突，不能覆盖新操作状态或样式结果。HTTP 契约仍是按 serviceId 覆盖/卸载；operationId 是本地并发归属，不改变部署 revision 的公开语义。
