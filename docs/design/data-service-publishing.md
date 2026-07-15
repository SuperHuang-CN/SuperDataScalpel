# 数据服务发布第一版

## 目标与范围

第一版只实现“一个模型（一个物理表）发布为一个标准数据服务”。服务的查询入参和响应格式固定，支持分页、字段选择、过滤、排序、分组和聚合；不支持任意 SQL、脚本发布、自定义响应模板、跨表关联或写入操作。

一个 `ServiceEngine` 是一个独立 JVM，不是为每个服务启动一个 JVM。一个数据服务在任一时刻只部署到一个已启用 Engine。这样可横向增加 Engine 来分摊运行服务数量，同时保留简单、同步的发布流程。

## 结构与职责

```text
Admin（控制面）
  ├─ 数据服务定义、模型引用、发布状态与部署结果
  ├─ Engine × JDBC 数据源的显式注册关系与同步状态
  └─ 以固定 Token 调用 Engine 内部部署接口

Service Engine（运行面，独立 JVM + 独立 PostgreSQL）
  ├─ 保存部署定义快照和独立的数据源快照；凭据字段以 AES-GCM 加密保存
  ├─ 发布时动态注册真实 Spring MVC 路由
  └─ 将公共请求编译为 AST → 数据库方言 SQL → JDBC 参数化执行

业务数据源
  └─ 只读查询目标表
```

Admin 保存业务定义；Engine 不读取 Admin 库。一个 JDBC 数据源必须先注册并同步到目标 Engine，Engine 才会保存其加密连接快照；部署快照只引用数据源 ID。Engine 重启后先恢复本地数据源，再恢复所有未移除的部署和动态路由。

## 控制面 API 与生命周期

Service Engine 管理接口：

- `GET /api/v1/service-engines`、`GET /api/v1/service-engines/{id}`
- `POST /api/v1/service-engines`
- `POST /api/v1/service-engines/{id}/actions/update`
- `POST /api/v1/service-engines/{id}/actions/test`
- `POST /api/v1/service-engines/{id}/actions/delete`

Engine 数据源注册接口：

- `GET /api/v1/service-engine-data-sources`、`GET /api/v1/service-engine-data-sources/{id}`
- `POST /api/v1/service-engine-data-sources`（注册并首次同步）
- `POST /api/v1/service-engine-data-sources/{id}/actions/sync`
- `POST /api/v1/service-engine-data-sources/{id}/actions/test`
- `POST /api/v1/service-engine-data-sources/{id}/actions/delete`

数据服务管理接口：

- `GET /api/v1/data-services`、`GET /api/v1/data-services/{id}`
- `POST /api/v1/data-services`
- `POST /api/v1/data-services/{id}/actions/update`
- `POST /api/v1/data-services/{id}/actions/publish`
- `POST /api/v1/data-services/{id}/actions/disable`
- `POST /api/v1/data-services/{id}/actions/delete`

创建服务时校验服务编码、目录、模型、Engine、Engine 数据源注册关系和同一 Engine 内的路由唯一性。发布前必须同时满足：模型已发布、模型绑定的是启用的 JDBC 数据存储、该数据源到目标 Engine 的注册状态为 `READY`、物理表结构与模型字段一致。随后 Admin 生成版本化部署定义并同步调用 Engine：

1. Engine 校验 `/open-api/v1/...` 路径及与系统路由、已部署服务的冲突。
2. Engine 持久化版本化部署定义，并引用已注册数据源的本地快照。
3. Engine 动态注册 `POST` 路由并确认部署。
4. Admin 收到确认后将服务标记为 `PUBLISHED`、部署标记为 `DEPLOYED`。

任何远程调用失败都会保留部署记录为 `FAILED` 和错误信息，服务不会进入 `PUBLISHED`。下线也以同步确认方式移除 Engine 路由；未确认移除的服务不能修改或删除，以免控制面与运行面状态失配。

数据源注册状态：

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 正在向 Engine 同步连接快照 |
| `READY` | Engine 已确认当前快照，可以发布服务 |
| `OUTDATED` | Admin 中的数据源连接或运行能力已变化，需手动同步 |
| `FAILED` | 首次注册或同步失败，保留错误信息便于重试 |

数据源连接、数据库类型、JDBC 存储用途或启用状态变化时，所有对应 Engine 注册关系都会标记为 `OUTDATED`。再次同步成功后，Engine 会替换本地快照并清理旧连接池；所有引用该数据源的已发布服务随即使用新连接，无需重新发布。存在已发布服务时，不能停用/移除该数据源的 JDBC 存储能力，也不能解除对应 Engine 注册。

服务与部署状态：

| 数据服务状态 | 部署状态 | 含义 |
| --- | --- | --- |
| `DRAFT` | 无或 `REMOVED` | 可编辑，尚未对外开放 |
| `DRAFT` | `FAILED` | 发布失败，先处理错误再重试 |
| `PUBLISHED` | `DEPLOYED` | 已注册公共路由 |
| `PUBLISHED` | `FAILED` | 下线调用未获确认，继续视为已发布 |
| `DISABLED` | `REMOVED` | 已从 Engine 下线 |

## Engine 配置和安全

Engine 必须使用独立 PostgreSQL，`ddl-auto=update` 管理其运行时部署表和本地数据源表。运行时使用以下环境变量：

本版本不兼容旧的“部署内嵌数据源快照”表结构。按当前开发阶段约定，升级 Engine 时直接清空其运行时数据并重建 `ds_engine_deployment`、`ds_engine_data_source`，随后重新注册数据源并发布服务；不提供旧部署数据迁移。

| 变量 | 用途 |
| --- | --- |
| `DATASCALPEL_ENGINE_DB_URL` / `USERNAME` / `PASSWORD` | Engine 自己的 PostgreSQL 连接 |
| `DATASCALPEL_ENGINE_CODE` | 在控制面登记的稳定 Engine 编码 |
| `DATASCALPEL_ENGINE_MANAGEMENT_TOKEN` | Admin 与 Engine 共用的内部调用 Token |
| `DATASCALPEL_ENGINE_ENCRYPTION_KEY` | Base64 编码的 16/24/32 字节 AES 密钥 |
| `DATASCALPEL_ENGINE_PORT` | 可选，默认 `8081` |

内部 API `GET /internal/v1/info`、`POST /internal/v1/data-sources`、`POST /internal/v1/data-sources/{id}/actions/test`、`POST /internal/v1/data-sources/actions/remove`、`POST /internal/v1/deployments`、`POST /internal/v1/deployments/actions/remove` 都要求 `Authorization: Bearer <Token>`。第一版采用该固定 Token，公开路由不要求 Engine 层认证；网关、内网边界或调用方认证由部署环境负责。

加密密钥和管理 Token 均不可提交到版本库。加密密钥改变后旧快照不能解密，必须在新密钥下重新发布服务。

## 标准公共查询协议

每个已发布服务都动态暴露其登记的 `routePath`，路径必须为不带尾斜杠的静态 `/open-api/v1/...`，例如：

```http
POST /open-api/v1/orders
Content-Type: application/json

{
  "pageNo": 1,
  "pageSize": 20,
  "columns": ["id", "customerName", "amount"],
  "conditionType": "AND",
  "filters": [
    {"name": "amount", "operator": ">=", "value": 100},
    {"name": "customerName", "operator": "like", "value": "张"}
  ],
  "orders": [{"column": "id", "direction": "DESC"}],
  "returnCount": true
}
```

响应格式固定：

```json
{
  "pageNo": 1,
  "pageSize": 20,
  "totalCount": 1,
  "resultList": [
    {"id": 1001, "customerName": "张三", "amount": "188.50"}
  ]
}
```

- `pageNo` 从 1 开始；默认大小 20，默认最大 100，可由 Engine 配置调整。
- 管理控制台会为已发布且已部署的服务提供“复制访问 cURL”。命令使用 Service Engine 的公网地址、公开路由和基础分页请求体；如部署环境要求网关认证，应由调用方按实际约定补充认证请求头。
- 未指定 `columns` 时返回所有非二进制字段；二进制字段不能返回、过滤、分组、聚合或排序。
- 支持 `=`, `!=`, `>`, `>=`, `<`, `<=`, `in`, `not in`, `between`, `not between`, `like`, `not like`, `is null`, `is not null`, `is empty`, `is not empty`。
- `IN`、`NOT IN` 和范围操作通过 `values` 传值，范围操作必须恰有两个值。其它单值操作使用 `value`。
- 分组使用 `groups`；聚合使用 `aggregators`（`COUNT`、`SUM`、`MIN`、`MAX`、`AVG`）并提供唯一 `alias`。聚合查询的普通返回字段必须同时出现在 `groups`。
- 字段名永远从服务发布快照的字段白名单解析，值先按字段类型转换。SQL 使用占位符绑定，不能由调用方指定表名、物理列或任意 SQL。

## JDBC 方言执行

公共请求不直接生成字符串 SQL。Engine 依次完成：字段白名单验证 → 自有 `StandardQuery` AST → 方言编译 → 原生 JDBC 参数绑定。方言实现位于 `data-scalpel-dialect`，不依赖 Spring、JPA 或业务实体；当前内建 PostgreSQL、MySQL、Oracle、SQL Server、ClickHouse、达梦、人大金仓和 openGauss 的查询方言与 JDBC 连接规格。

数据源连接池按 Engine 本地数据源 ID 惰性创建并复用，查询语句设置超时。SQL/脚本发布和额外数据库类型将以新增 AST 节点、方言编译器能力和受控协议的方式演进，不改变本版的标准服务契约。
