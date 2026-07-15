# 数据源管理

## 范围

数据源管理维护可复用的外部连接。一个连接可以同时承担数据源、数据存储和数据分发用途，并可关联 `DATA_SOURCE` 范围的通用目录。连接类型包括 JDBC 数据库、Kafka 集群和 S3 兼容对象存储；它们共用数据源的目录、用途、启停和审计模型，但各自使用明确的配置契约。

当前阶段提供：

- 数据源 CRUD、统一 Search DSL 查询和目录筛选。
- JDBC：已保存连接和未保存表单的真实连接测试、库/Schema、表和视图、表元数据与最多 100 行的只读预览。
- Kafka：登记一个 Kafka 集群；Topic 是任务阶段的资源，不属于数据源配置。
- S3：登记一个固定 Bucket，可选配置根目录；对象 Key 位于该根目录之下。
- JDBC 方言：PostgreSQL、MySQL、Oracle、SQL Server、ClickHouse、达梦、人大金仓和 openGauss。

当前尚未实现 Kafka、S3 客户端，因此它们只支持 CRUD 和目录/用途管理；连接测试、资源发现、元数据和预览会返回“尚未实现”。本阶段不建设外部数据库持续集成测试环境，不提供 DDL、数据修改、连接健康定时检查或任务执行连接池。其他数据库的真实环境兼容性验证安排在第三阶段。

## 架构

`data-scalpel-dialect` 是不依赖 Spring、JPA 和业务实体的纯 Java/JDBC 技术模块，只负责 JDBC 能力，包含：

- 数据库类型和能力定义。
- JDBC URL、驱动属性和连接规格。
- 标识符引用、默认 Catalog/Schema 和预览 SQL 方言。
- 统一的表、字段、主键、索引和预览数据模型。
- 基于短连接的连接测试与只读元数据读取。

`data-scalpel-business` 负责维护数据源聚合，并按连接类别转换为运行时快照、编排 JDBC 方言调用和映射稳定的 Web DTO。远程 JDBC 调用不运行在管理库的 JPA 事务中。Kafka/S3 客户端接入时在该业务域新增对应运行时实现，不扩展或污染 JDBC 方言模块。

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
| `database_type` | 数据源产品类型；兼容既有 JDBC 列名，取值包含数据库、`KAFKA`、`S3` |
| `enabled` | 人工启停状态，不表示实时健康度 |
| `description` | 说明 |
| `host`、`port`、`database_name`、`schema_name`、`username`、`connection_password` | 通用连接存储槽位。JDBC 分别表示主机、端口、数据库、Schema、用户名、密码；Kafka 使用主机保存 Bootstrap Servers；S3 使用主机、数据库、Schema、用户名、密码分别保存 Endpoint、Bucket、根目录、AccessKey、SecretKey |
| `connection_options` | 使用可移植文本编码保存的少量类型专属非敏感参数，例如 JDBC 方言参数、Kafka 安全协议、S3 Region 与 Path-style 标记 |

密码和 SecretKey 只写不返回；响应仅返回相应的 `passwordConfigured` 或 `secretKeyConfigured`。更新时敏感字段为 `null` 表示保留原值。JDBC 高级参数为 `null` 表示保留原参数，空 Map 表示清空高级参数。

为兼容第一版已落库的 JDBC 表结构，非 JDBC 连接在原来要求非空、但没有对应业务含义的 `port`、`database_name`、`username` 槽位中保存内部占位值；该值不会通过 API 返回，也不参与运行时连接。这样既不需要引入迁移框架，也不会把 Kafka/S3 参数伪装成 JDBC 语义。

对于已存在的 PostgreSQL 管理库，应用启动时会幂等更新 Hibernate 早期创建的 `database_type` 校验约束，使其包含当前所有 `DataSourceType` 枚举值。原因是 `ddl-auto=update` 不会自动演进该约束；其他数据库不执行这段 PostgreSQL 兼容 SQL。

JDBC 高级参数由方言定义并校验。例如 PostgreSQL 的 `sslmode`、Oracle 的 Service Name/SID 模式和 SQL Server 的证书选项。驱动类名和 JDBC URL 模板不是可编辑业务字典。S3 的 Bucket 是数据源固定边界，不允许由调用方在后续资源请求中覆盖。

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

数据源创建/更新请求由 `type` 和带 `kind` 的 `connection` 组成。`type` 与 `connection.kind` 必须匹配：JDBC 使用 `JDBC`，Kafka 使用 `KAFKA`，S3 使用 `S3`。现有 JDBC API 的真实测试和元数据端点仅对 JDBC 类型可用。

物理表统一使用 `catalog + schema + table` 标识，不把可能带点号或空格的表名直接放入 URL Path。

数据预览不执行 `count(*)`。查询 `limit + 1` 行判断是否截断，所有表标识符由方言引用，结果中的长文本和二进制值会限制展示长度。

## PostgreSQL 人工验证基线

管理库中保留 `datascalpel_adapter_test` Schema，用于当前阶段的人工验证：

- `sample_order`：包含 UUID 主键、唯一约束、复合普通索引、数值、布尔、时间和文本字段。
- `active_orders`：基于样例表的视图。
- 数据源记录编码：`pg_adapter_test`。

已使用真实 PostgreSQL 16.14 验证连接测试、Schema 发现、表/视图列表、6 个字段、主键、索引和 2 行数据预览。该验证是一次人工验收，不作为自动化集成测试运行。
