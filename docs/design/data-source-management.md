# 数据源管理

## 范围

数据源管理维护可复用的数据库连接。一个连接可以同时承担数据源、数据存储和数据分发用途，并可关联 `DATA_SOURCE` 范围的通用目录。

当前阶段提供：

- 数据源 CRUD、统一 Search DSL 查询和目录筛选。
- 已保存连接和未保存表单的真实连接测试。
- 查询数据库的库/Schema、表和视图。
- 读取字段、主键、索引和注释等表元数据。
- 最多 100 行的只读数据预览。
- PostgreSQL、MySQL、Oracle、SQL Server、ClickHouse、达梦、人大金仓和 openGauss 方言。

本阶段不建设外部数据库持续集成测试环境，不提供 DDL、数据修改、连接健康定时检查或任务执行连接池。其他数据库的真实环境兼容性验证安排在第三阶段。

## 架构

`data-scalpel-dialect` 是不依赖 Spring、JPA 和业务实体的纯 Java/JDBC 技术模块，包含：

- 数据库类型和能力定义。
- JDBC URL、驱动属性和连接规格。
- 标识符引用、默认 Catalog/Schema 和预览 SQL 方言。
- 统一的表、字段、主键、索引和预览数据模型。
- 基于短连接的连接测试与只读元数据读取。

`data-scalpel-business` 负责把数据源实体转换为运行时连接快照，编排方言调用并映射为稳定的 Web DTO。远程数据库调用不运行在管理库的 JPA 事务中。

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
| `database_type` | 数据库产品类型 |
| `enabled` | 人工启停状态，不表示实时健康度 |
| `description` | 说明 |
| `host`、`port`、`database_name`、`schema_name`、`username`、`connection_password` | 公共连接参数 |
| `connection_options` | 使用可移植文本编码保存的少量方言高级参数 |

密码只写不返回；响应仅返回 `passwordConfigured`。更新时密码为 `null` 表示保留原密码，高级参数为 `null` 表示保留原参数，空 Map 表示清空高级参数。

高级参数由方言定义并校验。例如 PostgreSQL 的 `sslmode`、Oracle 的 Service Name/SID 模式和 SQL Server 的证书选项。驱动类名和 JDBC URL 模板不是可编辑业务字典。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/data-source-types` | 数据库类型、默认值、能力和驱动状态 |
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

物理表统一使用 `catalog + schema + table` 标识，不把可能带点号或空格的表名直接放入 URL Path。

数据预览不执行 `count(*)`。查询 `limit + 1` 行判断是否截断，所有表标识符由方言引用，结果中的长文本和二进制值会限制展示长度。

## PostgreSQL 人工验证基线

管理库中保留 `datascalpel_adapter_test` Schema，用于当前阶段的人工验证：

- `sample_order`：包含 UUID 主键、唯一约束、复合普通索引、数值、布尔、时间和文本字段。
- `active_orders`：基于样例表的视图。
- 数据源记录编码：`pg_adapter_test`。

已使用真实 PostgreSQL 16.14 验证连接测试、Schema 发现、表/视图列表、6 个字段、主键、索引和 2 行数据预览。该验证是一次人工验收，不作为自动化集成测试运行。
