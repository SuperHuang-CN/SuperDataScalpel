# 数据源管理

## 范围

数据源管理维护可复用的外部连接。一个连接可以同时承担数据源、数据存储和数据分发用途，并可关联 `DATA_SOURCE` 范围的通用目录。连接类型包括 JDBC 数据库、HTTP/JSON API、Kafka 集群和 S3 兼容对象存储；它们共用数据源的目录、用途、启停和审计模型，但各自使用明确的配置契约。

当前阶段提供：

- 数据源 CRUD、统一 Search DSL 查询和目录筛选。
- JDBC：已保存连接和未保存表单的真实连接测试、库/Schema、表和视图、表元数据与最多 100 行的只读预览。
- HTTP API：只作为输入使用，支持可复用连接、运行时 Token、请求签名、API 资源、分页/异步请求、资源测试和 Canvas 输入节点。
- Kafka：登记一个 Kafka 集群；Topic 是任务阶段的资源，不属于数据源配置。
- S3：登记一个固定 Bucket，可选配置根目录；对象 Key 位于该根目录之下。
- JDBC 方言：PostgreSQL、MySQL、Oracle、SQL Server、ClickHouse、达梦、人大金仓和 openGauss。
- 空间结构元数据：已安装 PostGIS 的 PostgreSQL 与 MySQL 8.x 可整表读取受约束的 Geometry subtype、EPSG CRS 和 XY 维度；不读取 Geometry 值，也不创建或解释空间索引。

当前尚未实现 Kafka、S3 客户端，因此它们只支持 CRUD 和目录/用途管理；连接测试、资源发现、元数据和预览会返回“尚未实现”。常规构建不依赖外部数据库；PostGIS 和 MySQL 8 Geometry 提供默认跳过、只面向可销毁隔离实例的 opt-in 验收。不提供连接健康定时检查或任务执行连接池，其他数据库的真实环境兼容性验证仍需独立安排。

## 架构

`data-scalpel-dialect` 是不依赖 Spring、JPA 和业务实体的纯 Java/JDBC 技术模块，只负责 JDBC 能力，包含：

- 数据库类型和能力定义。
- JDBC URL、驱动属性和连接规格。
- 标识符引用、默认 Catalog/Schema 和预览 SQL 方言。
- 统一的表、字段、主键、索引和预览数据模型。
- 基于短连接的连接测试与只读元数据读取。
- 连接感知的 PostGIS/MySQL 8 空间能力检查、整表空间元数据增强和 EPSG 到数据库本地空间参考 ID 的目录解析。

`data-scalpel-business` 负责维护数据源聚合，并按连接类别转换为运行时快照、编排 JDBC 方言或 HTTP 连接器调用和映射稳定的 Web DTO。远程 JDBC/HTTP 调用不运行在管理库的 JPA 事务中。HTTP 通用连接器负责管理端连接及资源测试；Task Engine Runner 使用同一份稳定运行契约执行真实拉取。Kafka/S3 客户端接入时在该业务域新增对应运行时实现，不扩展或污染 JDBC 方言模块。

各数据库 JDBC 驱动由 `data-scalpel-admin` 在运行时提供。数据库类型接口会返回驱动是否可用，前端的名称、默认端口、字段标签和高级参数来自该接口，不再依赖硬编码表单定义。

## 数据模型

表：`ds_data_source`

| 字段 | 含义 |
| --- | --- |
| `id`、`created_at`、`updated_at` | 继承 `BaseEntity` 的 UUID 和审计时间 |
| `code` | 全局唯一、创建后不可修改的技术编码 |
| `name` | 显示名称 |
| `directory_id` | 可选的通用目录 UUID |
| `source_enabled` | 是否作为输入数据源 |
| `storage_enabled` | 是否作为模型物理存储 |
| `distribution_enabled` | 是否作为数据分发目标 |
| `database_type` | 数据源产品类型；兼容既有 JDBC 列名，取值包含数据库、`HTTP_API`、`KAFKA`、`S3` |
| `enabled` | 人工启停状态，不表示实时健康度 |
| `description` | 说明 |
| `host`、`port`、`database_name`、`schema_name`、`username`、`connection_password` | 通用连接存储槽位。JDBC 分别表示主机、端口、数据库、Schema、用户名、密码；Kafka 使用主机保存 Bootstrap Servers；S3 使用主机、数据库、Schema、用户名、密码分别保存 Endpoint、Bucket、根目录、AccessKey、SecretKey |
| `connection_options` | 使用可移植文本编码保存的少量类型专属非敏感参数，例如 JDBC 方言参数、Kafka 安全协议、S3 Region 与 Path-style 标记 |
| `api_configuration` | HTTP API 的非敏感连接、鉴权方式和超时/重试配置，使用 PostgreSQL `text` 映射 |
| `api_credentials_ciphertext` | HTTP API 密码、Token、API Key、Client Secret、签名密钥和私钥的 AES-GCM 密文 |

密码、SecretKey 和 HTTP API 凭据只写不返回；响应仅返回相应的 `passwordConfigured`、`secretKeyConfigured` 或各类凭据的 `configured` 状态。更新时敏感字段为 `null` 表示保留原值。JDBC 高级参数为 `null` 表示保留原参数，空 Map 表示清空高级参数。HTTP API 凭据使用 `DATASCALPEL_DATA_SOURCE_CREDENTIAL_KEY` 指定的独立 Base64 AES 密钥加密；生产部署必须稳定保存该密钥，更换密钥前需要先完成凭据轮换，否则既有密文无法解密。

为兼容第一版已落库的 JDBC 表结构，非 JDBC 连接在原来要求非空、但没有对应业务含义的 `port`、`database_name`、`username` 槽位中保存内部占位值；该值不会通过 API 返回，也不参与运行时连接。这样既不需要引入迁移框架，也不会把 Kafka/S3 参数伪装成 JDBC 语义。

对于已存在的 PostgreSQL 管理库，应用启动时会幂等更新 Hibernate 早期创建的 `database_type` 校验约束，使其包含当前所有 `DataSourceType` 枚举值。原因是 `ddl-auto=update` 不会自动演进该约束；其他数据库不执行这段 PostgreSQL 兼容 SQL。

JDBC 高级参数由方言定义并校验。例如 PostgreSQL 的 `sslmode`、Oracle 的 Service Name/SID 模式和 SQL Server 的证书选项。驱动类名和 JDBC URL 模板不是可编辑业务字典。S3 的 Bucket 是数据源固定边界，不允许由调用方在后续资源请求中覆盖。

### JDBC 自定义连接参数

JDBC 连接同时支持方言预定义参数和少量自定义非敏感参数，两者统一保存在
`connection.options` 中。前端将预定义参数渲染为类型明确的表单控件，将定义之外的参数渲染为
可增删的键值列表；调用方不填写 `?`、`&`、`;`，也不提交完整 JDBC URL。

方言始终负责生成 JDBC URL。普通自定义参数通过 `JdbcConnectionSpec.properties` 交给驱动，
只有 Oracle `connectionMode` 等确实改变 URL 结构的预定义参数由方言内部消费。这样不会形成
“主机、端口、数据库字段”和“手写 URL”两套互相冲突的连接来源，也能兼容 PostgreSQL/MySQL
的查询参数语法、SQL Server 的分号语法和 Oracle 的 Service Name/SID 差异。

参数边界如下：

- 一个连接最多保存 20 个参数；参数名最长 64 个字符，值最长 512 个字符，编码后的整体内容
  不得超过 `connection_options` 列的 4000 字符容量。
- 参数名以字母开头，只允许字母、数字、点、下划线和短横线；大小写不同但名称相同的参数
  视为重复，保存时仍保留驱动要求的原始大小写。
- 方言预定义的 `BOOLEAN` 和 `SELECT` 参数必须通过服务端类型与选项校验，不能仅依赖前端控件。
- `user`、`password`、主机、端口、数据库、Schema、驱动、URL，以及方言强制设置的连接超时、
  Socket 超时和应用标识属于系统保留参数，不能由自定义参数覆盖。
- 名称包含密码、Secret、Token、API Key 等含义的参数属于敏感参数，第一版不允许放入 `options`，因为
  普通 options 会在详情接口中回显。确需证书库密码等能力时，应新增只写且单独保护的敏感参数契约。
- 更新时 `options = null` 表示保留当前参数，空 Map 表示清空全部参数。空白自定义行不会进入请求。
- 未知参数由具体 JDBC 驱动解释；部分驱动可能静默忽略拼写错误，因此连接测试只能验证连接可用，
  不能证明每个未知参数都已被驱动采用。

数据源参数变化继续计入 Service Engine 注册的运行时签名。修改任一预定义或自定义参数后，已有
Engine 数据源快照会标记为过期，重新同步后 Engine 连接池以新 options 指纹重建。

### JDBC 连接测试诊断

连接测试能够正常执行、但目标数据库连接失败时，接口仍返回 `200 OK` 和
`success = false`。这是连接测试的业务结果，不属于 HTTP 接口本身失败；请求校验、权限和未预期的
服务端异常仍使用统一的 `ProblemDetail`。

失败结果在稳定的 `code` 和面向使用者的 `message` 之外返回 `diagnostic`：包括 JDBC 异常类型、
驱动原始消息、SQLState、Vendor Code，以及经过限长的 Cause/NextException 异常链。前端以连接测试
详情弹窗展示这些字段，并支持复制诊断文本。完整 Java 堆栈不返回浏览器。

捕获连接失败时，后台使用 `WARN` 记录数据库类型、目标主机/端口/数据库、稳定错误码、SQLState、
Vendor Code、耗时和异常堆栈。响应与日志都会遮蔽当前连接密码，不记录完整 JDBC Properties；单条
原始消息最多 2000 字符，响应最多返回 8 个下层异常，避免驱动异常形成过大的响应。

### HTTP API 连接与资源

`HTTP_API` 数据源只允许 `SOURCE` 用途。连接层保存 Base URL、默认 Header、连接/请求超时、最小请求间隔、重试上限和鉴权配置；具体业务接口不直接塞进数据源连接，而是保存为从属的 `ds_api_resource`：

| 字段 | 含义 |
| --- | --- |
| `data_source_id` | 所属 HTTP API 数据源 UUID 标量引用 |
| `code`、`name`、`enabled` | 数据源内唯一编码、名称和启停状态 |
| `connector_type` | 连接器类型；第一阶段内置 `GENERIC_HTTP` |
| `definition_json` | 请求模板、签名、调用模式、分页、异步轮询、显式输出 Schema 和执行限制 |

连接层支持无鉴权、Basic、固定 Bearer Token、Header/Query API Key、OAuth2 Client Credentials 和自定义 Token Endpoint。OAuth2 与 Token Endpoint 在运行时获取并按有效期缓存 Token；业务请求遇到 `401/403` 时只允许清理缓存、刷新并重试一次。Token 的默认放置方式为 `Authorization: Bearer ${token}`，也可以配置到 Query 或 JSON Body。

资源请求只支持 `GET`、`POST` 和 JSON 响应。Path、Query、Header、Body 使用受限模板变量，不执行 SpEL、JavaScript 或上传脚本。运行时参数只用于日期、筛选条件、初始游标等非敏感业务值；它们会随 Canvas 定义持久化，不能存放 Token、密码、API Key 或 Secret。

签名支持 `MD5`、`HMAC_SHA256`、`HMAC_SHA512` 和 `RSA_SHA256`。执行器在鉴权、分页参数、时间戳和 Nonce 全部进入最终请求后生成签名，并在每页、每次重试时重新计算。超出通用契约的厂商协议通过随应用发布且可测试的 Java 连接器扩展，不支持运行时上传代码。

调用模式包括单次请求、同步分页和异步任务。分页支持页码、Offset/Limit、Cursor 和响应中的 Next URL；异步任务按“提交并提取 Job ID → 轮询状态 → 获取结果 → 对结果分页”执行。数据数组由 JSON Pointer 定位，空字符串表示响应根节点就是数组。最大页数、行数、响应字节数、持续时间、重复 Cursor/Next URL 检测和 Next URL 同源限制共同防止无限拉取。

连接测试只验证 Base URL 和鉴权流程。API 资源测试执行完整资源流程，成功时返回 HTTP 状态、Content-Type、耗时、记录数和最多 20 行脱敏预览；失败时返回原始技术错误的限长、脱敏诊断。完整堆栈只记录在后台，响应和日志均不得出现凭据、签名或业务数据。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/data-source-types` | 数据源类型、支持用途、连接类别与运行时能力；JDBC 额外返回默认值、能力和驱动状态 |
| `GET` | `/api/v1/data-sources` | 统一 Search DSL 分页查询 |
| `GET` | `/api/v1/data-sources/{id}` | 查询详情 |
| `POST` | `/api/v1/data-sources` | 创建 |
| `POST` | `/api/v1/data-sources/{id}/actions/update` | 更新 |
| `POST` | `/api/v1/data-sources/{id}/actions/delete` | 删除 |
| `POST` | `/api/v1/data-sources/actions/test` | 测试未保存连接 |
| `POST` | `/api/v1/data-sources/{id}/actions/test` | 测试已保存连接 |
| `GET` | `/api/v1/data-sources/{id}/namespaces` | 查询 Catalog/Schema |
| `GET` | `/api/v1/data-sources/{id}/tables` | 查询表和可选视图；最多返回 500 项 |
| `GET` | `/api/v1/data-sources/{id}/table-metadata` | 查询字段、主键和索引 |
| `GET` | `/api/v1/data-sources/{id}/table-preview` | 预览数据；默认 50 行、最多 100 行 |
| `GET` | `/api/v1/data-sources/{id}/api-resources` | 查询 HTTP API 数据源下的 API 资源 |
| `GET` | `/api/v1/data-sources/{id}/api-resources/{resourceId}` | 查询 API 资源详情 |
| `POST` | `/api/v1/data-sources/{id}/api-resources` | 创建 API 资源 |
| `POST` | `/api/v1/data-sources/{id}/api-resources/{resourceId}/actions/update` | 更新 API 资源 |
| `POST` | `/api/v1/data-sources/{id}/api-resources/{resourceId}/actions/delete` | 删除 API 资源 |
| `POST` | `/api/v1/data-sources/{id}/api-resources/{resourceId}/actions/test` | 使用非敏感运行时参数测试 API 资源 |

数据源创建/更新请求由 `type` 和带 `kind` 的 `connection` 组成。`type` 与 `connection.kind` 必须匹配：数据库使用 `JDBC`，HTTP API 使用 `HTTP_API`，Kafka 使用 `KAFKA`，S3 使用 `S3`。JDBC 元数据端点仅对 JDBC 类型可用；API 资源端点仅对 HTTP API 类型可用。

物理表统一使用 `catalog + schema + table` 标识，不把可能带点号或空格的表名直接放入 URL Path。

数据预览不执行 `count(*)`。查询 `limit + 1` 行判断是否截断，所有表标识符由方言引用，结果中的长文本和二进制值会限制展示长度。

## PostgreSQL 人工验证基线

管理库中保留 `datascalpel_adapter_test` Schema，用于当前阶段的人工验证：

- `sample_order`：包含 UUID 主键、唯一约束、复合普通索引、数值、布尔、时间和文本字段。
- `active_orders`：基于样例表的视图。
- 数据源记录编码：`pg_adapter_test`。

已使用真实 PostgreSQL 16.14 验证连接测试、Schema 发现、表/视图列表、6 个字段、主键、索引和 2 行数据预览。该验证是一次人工验收，不作为自动化集成测试运行。
