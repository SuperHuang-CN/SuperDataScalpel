# 数据服务定义、Engine 启用与查询运行设计

## 目标与模式

数据服务提供两种创建后不可切换的模式：

- `STANDARD_TABLE`：绑定一个已发布模型，将模型对应的单张物理表启用为字段白名单约束的标准查询服务。
- `SQL_QUERY`：先绑定一个已启用的 PostgreSQL JDBC 数据源，再关联该数据源下的一个或多个模型，并将一条启用时冻结的只读参数化 SQL 模板部署到已注册该数据源的目标 Engine。

SQL 服务不是任意 SQL 执行接口。调用方不能提交 SQL、表名、列名或 SQL 片段，只能向启用快照中声明的标量参数传值。第一版不支持写入语句、列表参数、动态 SQL、标识符参数、自定义 count、路径/Header 参数或自定义响应模板。

SQL 服务关联的模型只用于来源说明、血缘记录、编辑辅助和删除保护，不是 SQL 表访问白名单。第一版不解析 SQL 中的表名，也不要求实际查询表必须对应已选模型；只要查询通过现有只读、参数、类型、元数据和数据库执行检查即可。数据库账号的只读权限仍是实际安全边界。

一个 `ServiceEngine` 是独立 JVM 和独立 PostgreSQL 运行库，不为每个服务启动进程。Admin 是控制面，Engine 是运行面；Engine 只持久化无凭据的服务快照和加密的数据源连接快照，不访问 Admin 数据库。

## 管理领域模型

`DataService` 只保存编码、名称、目录、不可修改的类型、Engine、路由和生命周期状态。具体定义分表保存：

- `StandardDataServiceDefinition`：`dataServiceId`、`modelId`、定义版本。
- `SqlDataServiceDefinition`：`dataServiceId`、`dataSourceId`、大文本 `sqlText`、定义版本。
- `SqlDataServiceModelReference`：标量 `dataServiceId`、`modelId` 和关联顺序；同一服务内模型及顺序分别唯一。
- `SqlDataServiceParameter`：标量 `dataServiceId`、参数名、平台类型及长度/精度/scale、必填标记、顺序和说明。

实体之间只使用 UUID 标量引用，不使用 JPA Entity 关联或级联。删除服务前必须已经从 Engine 停用且不存在网关绑定，再由业务 Service 显式删除模型引用、参数、具体定义、部署记录和根实体。模型删除同时检查标准服务定义和 SQL 服务模型引用；数据源和 Engine 数据源注册的删除保护仍依据 SQL 定义中的执行数据源。

旧版数据服务状态 `PUBLISHED` 的实际含义是“已部署到 Engine”，Admin 启动时会迁移为 `ENABLED`，已有 Engine 部署快照继续保留。网关发布是独立的新状态，升级后需要对已启用服务执行一次“发布到网关”。完整生命周期与 Kong 映射见[数据服务启停与网关发布设计](data-service-gateway-publishing.md)。

## Admin API

数据服务统一使用以下资源：

- `GET /api/v1/data-services`：摘要分页，包含 `type`、`sourceId`、`sourceName`，不返回 SQL 大文本。
- `GET /api/v1/data-services/{id}`：完整详情和具体定义。
- `POST /api/v1/data-services`
- `POST /api/v1/data-services/{id}/actions/update`
- `POST /api/v1/data-services/actions/test-sql`：测试未保存表单定义并返回实际查询预览。
- `POST /api/v1/data-services/{id}/actions/enable`：冻结定义并部署到 Service Engine。
- `POST /api/v1/data-services/{id}/actions/publish`：将已启用服务发布到当前网关。
- `POST /api/v1/data-services/{id}/actions/disable`：先撤回全部网关对象，再从 Service Engine 移除。
- `POST /api/v1/data-services/{id}/actions/cleanup-deployment`
- `POST /api/v1/data-services/{id}/actions/delete`

创建与更新使用显式互斥定义。`STANDARD_TABLE` 只能提供 `standardDefinition.modelId`；`SQL_QUERY` 只能提供 `sqlDefinition`。更新请求仍携带类型以校验不可切换，但不能修改编码。

SQL 服务的管理端选择顺序为：PostgreSQL 数据源 → 该数据源下的一个或多个模型 → 已注册该数据源的 Engine → SQL 和参数。`modelIds` 至少一个、不能包含空值或重复值，且所有模型的 `storageDataSourceId` 必须与 `dataSourceId` 相同。草稿、已发布和已停用模型均可关联，模型状态不参与保存、测试或启用判定。切换数据源后必须重新选择模型和 Engine。

SQL 定义示例：

```json
{
  "code": "customer_query",
  "name": "客户查询",
  "engineId": "00000000-0000-0000-0000-000000000001",
  "routePath": "/open-api/v1/customers",
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

## 管理端创建与详情工作台

数据服务列表的“新建服务”使用类型下拉菜单，不预设默认类型：

- 标准单表服务：进入独立的紧凑表单页。
- SQL 查询服务：进入独立的 SQL 工作台。
- 脚本服务：仅显示“规划中”的禁用入口；后端不存在 `SCRIPT` 类型或接口契约。

对应前端路由为：

| 路由 | 用途 | 页面权限 |
| --- | --- | --- |
| `/dataservice` | 数据服务列表 | `service.view` |
| `/dataservice/new/standard` | 新建标准单表服务 | `service.create` |
| `/dataservice/new/sql` | 新建 SQL 查询服务 | `service.create` |
| `/dataservice/:id` | 查看、编辑和执行生命周期操作 | `service.view` |

创建页只提供“保存草稿”，不提供“保存并启用”。创建成功后用 `replace` 进入详情路由，因此浏览器返回会直接回到进入创建页之前的列表。列表将 `keyword`、`status`、`type`、`engine`、`directory`、`page` 和 `size` 写入 URL；`directory=uncategorized` 表示未分类。进入详情或创建页再返回时，筛选、目录和分页位置能够恢复。

详情页根据服务端状态计算交互模式，不在前端提前猜测生命周期结果：

| 服务与部署状态 | 定义模式 | 可用操作 |
| --- | --- | --- |
| 草稿且无未清理部署 | 可编辑 | SQL 测试、保存、启用 |
| 已停用且部署为 `REMOVED` | 可编辑 | SQL 测试、保存、重新启用 |
| 已启用、Engine 为 `DEPLOYED`、尚未发布网关 | 只读 | SQL 测试、发布到网关、停用 |
| 已启用、当前 revision 已发布网关 | 只读 | SQL 测试、重新发布、复制网关 cURL、停用 |
| Engine 为 `PENDING` 或 `FAILED` | 部署锁定 | SQL 测试、重试启用、清理 Engine 部署 |
| 其他未完成移除状态 | 部署锁定 | 展示状态和错误，等待处理 |

权限会继续叠加到以上状态：保存要求 `service.update`，详情页 SQL 测试要求 `service.update`，启用、发布到网关、停用、重试和清理要求 `service.publish`。标准服务创建还要求模型和 Engine 查看权限；SQL 服务创建还要求数据源查看权限。缺少依赖资源查看权限时页面持续显示提示，并禁用相关选择和维护操作。

SQL 工作台在桌面端采用左右布局。左侧保存服务元数据、PostgreSQL 数据源、关联模型、Engine 和公开路由；右侧包含模型物理位置、Monaco SQL 编辑器、参数定义、临时测试值、输出字段、问题和预览数据。左右区域各自滚动，窄屏改为上下布局。SQL 编辑页面及 Monaco 均通过路由动态加载，不进入数据服务列表首屏包。

页面仅对持久化服务定义计算未保存状态；测试参数、问题和预览结果不参与 dirty fingerprint，也不会随服务保存。站内跳转使用路由 blocker，刷新、关闭标签页和浏览器离开使用 `beforeunload`。启用或重新启用前若存在未保存修改，必须先保存。保存校验失败时按“基本信息、来源与 Engine、SQL 模板、参数定义”显示错误摘要并定位第一个错误字段。

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

Admin 向 Engine 发送统一 `ServiceDefinitionSnapshot`，其中 `type` 与 `standardDefinition`、`sqlDefinition` 恰好一种匹配。

SQL 快照只包含：

- 协议版本。
- 编译后的 JDBC SQL。
- 占位符顺序对应的参数名。
- 参数及平台类型。
- 启用时冻结的输出字段。

Engine 的 SQL 快照不包含关联模型；运行面无需访问 Admin 模型。快照也不包含数据源密码、Admin 实体或测试参数值。服务部署摘要覆盖服务类型、路由、Engine、数据源、有序 `modelIds` 和完整 SQL 定义，不包含 revision。模型选择或顺序变化会改变定义版本和摘要；相同摘要的失败或超时重试复用 revision，定义变化才生成新 revision。同 revision 不同摘要由 Engine 返回冲突。

Admin 按“短事务读取快照 → 事务外 JDBC 检查 → 短事务复核并持久化待部署快照 → 事务外调用 Engine → 短事务提交结果”执行启用。HTTP 超时不被当作确定失败；服务保留失败/不确定状态，可用同摘要重试或调用 `cleanup-deployment` 向原 Engine 幂等移除。

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

部署先持久化 `DEPLOYING`，再注册/替换动态 Spring MVC `POST` 路由，最后标记 `DEPLOYED`。注册失败会撤销该服务的内存路由并标记 `DEPLOY_FAILED`，绝不返回成功。Engine 停用先标记 `REMOVING`，幂等注销后标记 `REMOVED`，失败则记录 `REMOVE_FAILED`。

启动时先恢复本地数据源，再逐一处理服务：`DEPLOYED`、`DEPLOYING` 重新校验并注册；`DEPLOY_FAILED` 不自动暴露；`REMOVING`、`REMOVE_FAILED` 确保注销并完成为 `REMOVED`。单个服务恢复失败会记录完整日志和失败状态，不影响其他服务或应用启动。

Engine 接收 SQL 快照时再次检查只读单语句、占位符数量、参数顺序、输出快照和数据库 `SQL_SERVICE_QUERY` capability。当前只有 PostgreSQL 声明该 capability。

## 公开查询协议

两种模式继续返回相同分页 JSON。SQL 服务请求示例：

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
  "resultList": [{"id": 1001, "name": "示例客户"}]
}
```

- `pageNo` 默认 1；`pageSize` 默认 20、最大 100。
- offset 为 `(pageNo - 1) * pageSize`，检查整数溢出且不能超过 Engine `maximum-offset`。
- `returnCount` 默认 false；为 true 时先执行 `SELECT COUNT(*) FROM (<baseSql>) ds_count`。
- 数据查询由 PostgreSQL 方言包装为 `SELECT * FROM (<baseSql>) ds_query LIMIT ? OFFSET ?`。
- 基础 SQL 参数先绑定，分页参数最后绑定；count 只复用基础参数。
- BigDecimal 输出为字符串，日期和时间输出 ISO 字符串，null 保持 null。
- 每次执行都会比较实际 ResultSet 的字段名称、顺序、类型和 nullable；结构漂移返回安全 502。
- SQL/数据库执行失败返回 502；调用参数错误返回 `INVALID_QUERY` 400。

Engine 使用按数据源复用的连接池、read-only connection、语句超时和 `setMaxRows`。数据库账号必须在数据库侧具备只读权限；关联模型、词法检查和 JDBC read-only 都不能替代数据库权限，其中关联模型尤其不构成 SQL 访问白名单。未提供顶层 `ORDER BY` 时允许查询，但跨页顺序由数据库决定。

## Engine 配置

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `DATASCALPEL_ENGINE_QUERY_DEFAULT_PAGE_SIZE` | `20` | 默认分页大小 |
| `DATASCALPEL_ENGINE_QUERY_MAXIMUM_PAGE_SIZE` | `100` | 最大分页大小 |
| `DATASCALPEL_ENGINE_QUERY_MAXIMUM_OFFSET` | `100000` | 最大分页偏移 |
| `DATASCALPEL_ENGINE_QUERY_TIMEOUT_SECONDS` | `30` | JDBC 查询超时 |

其他必需配置包括 Engine 独立数据库连接、稳定 Engine 编码、Admin/Engine 共享管理 Token 和 AES 快照加密密钥。Engine 编码由 Engine 自身配置，必须以字母开头且只包含字母、数字和下划线，最长 64 位；Admin 登记 Engine 时通过 `/internal/v1/info` 自动发现并保存为不可修改的内部指纹，不由用户手工录入。首次登记会校验编码唯一性，后续修改管理地址或 Management Token 时会重新读取并核对该指纹，连接失败或指纹不一致时拒绝保存。管理接口位于 `/internal/v1/**` 并要求 Bearer Token；公开调用统一从已发布的网关 Proxy 地址进入，网关发布细节见[数据服务启停与网关发布设计](data-service-gateway-publishing.md)。

## PostgreSQL 实库验收

默认构建使用 H2 PostgreSQL 模式覆盖 Engine SQL 路由，不要求本机运行 PostgreSQL。设置 `DATASCALPEL_PG_INTEGRATION=true` 以及 `DATASCALPEL_PG_HOST`、`DATASCALPEL_PG_PORT`、`DATASCALPEL_PG_DATABASE`、`DATASCALPEL_PG_SCHEMA`、`DATASCALPEL_PG_USERNAME`、`DATASCALPEL_PG_PASSWORD` 后，`PostgreSqlSqlServiceIntegrationTest` 会在指定的可丢弃 schema 中创建随机表，并验证 PreparedStatement 元数据、标量和重复参数、显式 null、分页、count、read-only connection、BigDecimal 字符串以及日期时间 ISO 输出。测试结束始终删除随机表。
