# 模型平台数据类型设计

## 目标

模型、任务和数据服务统一使用 `PlatformDataType` 表达数据库无关的稳定类型。标量类型名称和语义尽量与 Spark SQL 对齐，但核心模块不依赖 Spark 类，也不持久化 Spark `DataType`、Catalyst JSON 或 Java 类名；空间字段使用独立 Geometry 定义，不假装成 Spark String/Binary。

平台类型包括：

- `BOOLEAN`、`BYTE`、`SHORT`、`INTEGER`、`LONG`
- `FLOAT`、`DOUBLE`、`DECIMAL`
- `STRING`、`BINARY`
- `DATE`、`TIMESTAMP`、`TIMESTAMP_NTZ`
- `GEOMETRY`

`TIMESTAMP` 表示时间线上的时刻，对应 Spark `TimestampType`；`TIMESTAMP_NTZ` 表示不带时区的本地墙上时间，对应 Spark `TimestampNTZType`。`STRING` 的 `length` 可为空：指定长度表示保留物理长度约束，空值表示无长度上限。`DECIMAL` 精度限制为 1～38，小数位必须在 0～精度之间。

`GEOMETRY` 使用 `GeometryTypeDefinition(kind, crs, dimension)` 表达。V1 业务范围固定为
八种 GeometryKind、EPSG 正整数编码和 XY；Geometry 不能作为主键。数据库本地 SRID/SRS ID
不进入平台契约，完整边界见[空间字段结构管理 V1](spatial-field-structure-management-v1.md)。

参考 Spark JDBC 的实现顺序：读取时先处理数据库方言的原生类型，再使用标准 JDBC 类型回退；写入时先由数据库方言决定物理类型。详见 [Spark JDBC 类型映射](https://spark.apache.org/docs/latest/sql-data-sources-jdbc.html#data-type-mapping)和 [Spark JdbcUtils](https://github.com/apache/spark/blob/master/sql/core/src/main/scala/org/apache/spark/sql/execution/datasources/jdbc/JdbcUtils.scala)。

## 分层

| 层 | 类型 | 职责 |
| --- | --- | --- |
| 稳定契约 | `PlatformDataType`、`PlatformTypeDefinition` | 模型、任务、数据服务和未来 Spark 执行器共同使用 |
| JDBC 边界 | `JdbcTypeDescriptor` | 保存驱动返回的 JDBC 类型、原生类型名、长度和精度，不作为模型定义持久化 |
| 方言物理层 | `PhysicalTypeDefinition`、`TableColumnType` | DDL、结构比较和物理表变更计划使用，不直接暴露为模型字段类型 |
| 文件发现 | `LogicalType` | 文件数据集和通用元数据预览继续使用；它包含 JSON、ARRAY、OTHER，不等同于模型契约 |

依赖方向为 `contracts <- dialect <- business/admin`。`data-scalpel-dialect` 只依赖稳定类型契约，仍不依赖 Spring、JPA 或业务实体。

## 双向映射

映射结果必须带质量：

- `EXACT`：语义和约束精确保留。
- `NORMALIZED`：目标类型完整覆盖源值域，但物理名称或表示被归一化；允许使用并向用户解释。
- `LOSSY`：长度、精度、时区或值域可能丢失；禁止导入和建表。
- `UNSUPPORTED`：没有受控映射；禁止导入和建表。

读取和写入不是互逆函数。例如 PostgreSQL 没有 8 位整数，平台 `BYTE` 写入 `smallint` 后，再读取只能可靠识别为 `SHORT`。代码不得使用同名枚举 `valueOf` 假设双向完全一致。

当前重点方言：

| 平台类型 | PostgreSQL | 达梦 | ClickHouse |
| --- | --- | --- | --- |
| `BYTE` / `SHORT` | `smallint` / `smallint` | `SMALLINT` / `SMALLINT` | `Int8` / `Int16` |
| `INTEGER` / `LONG` | `integer` / `bigint` | `INT` / `BIGINT` | `Int32` / `Int64` |
| `FLOAT` / `DOUBLE` | `real` / `double precision` | `REAL` / `DOUBLE` | `Float32` / `Float64` |
| `DECIMAL(p,s)` | `numeric(p,s)` | `DECIMAL(p,s)` | `Decimal(p,s)` |
| 有长度 / 无长度 `STRING` | `varchar(n)` / `text` | `VARCHAR(n)` / `CLOB` | 有长度不支持 / `String` |
| `BINARY` | `bytea` | `BLOB` | 暂不支持 |
| `DATE` | `date` | `DATE` | `Date` |
| `TIMESTAMP` | `timestamp with time zone` | `TIMESTAMP WITH TIME ZONE` | `DateTime64(6,'UTC')` |
| `TIMESTAMP_NTZ` | `timestamp` | `TIMESTAMP` | 暂不支持 |
| `GEOMETRY` | PostGIS `geometry(KIND,localSrid)` | 暂不支持 | 暂不支持 |

ClickHouse 无符号整数读取时按能够完整覆盖其值域的平台类型归一：`UInt8 -> SHORT`、`UInt16 -> INTEGER`、`UInt32 -> LONG`、`UInt64 -> DECIMAL(20,0)`。

MySQL 8 的 Geometry 写入使用 `KIND SRID localSrsId` 并强制 Geometry 受管表为 InnoDB；
读取通过空间 catalog 还原 subtype、EPSG 和 XY。MySQL 5.7、MariaDB、无 SRID restriction
的列及非二维空间列均为 `UNSUPPORTED`。

## 接口与交互

- `GET /api/v1/models/platform-types?storageDataSourceId=...` 返回目标数据存储支持的平台类型以及长度能力。字段编辑器据此禁用不支持类型，不在前端硬编码数据库判断。
- `GET /api/v1/models/external-table-import-preview?storageDataSourceId=...&physicalTableName=...` 返回每列的原生类型、平台类型、映射质量和问题。
- 绑定已有表时，预览和最终保存都会重新由后端方言映射。外部 JDBC 读取在管理数据库事务外执行，随后使用短事务保存模型和字段。
- 外部表结构检查使用“物理类型 -> 平台类型”的读取映射；受管表建表和变更使用“平台类型 -> 物理类型”的写入映射。

旧 API 输入 `TEXT` 临时按无长度上限 `STRING` 读取，`DATETIME` 临时按 `TIMESTAMP_NTZ` 读取；所有新响应和数据库写入只使用新名称。JPA 转换器兼容已有 `field_type` 数据，后续数据清理完成后可删除旧别名。

## Spark 接入边界

Spark 执行模块使用 Java 映射器：

```java
DataType toSparkType(PlatformTypeDefinition type);

PlatformTypeDefinition fromSparkType(DataType type);
```

映射器使用 `org.apache.spark.sql.types.DataTypes`，但 `contracts`、`dialect`、`business` 和 `admin` 不增加 Spark 依赖。Geometry 在 V1 显式抛出 `SPATIAL_FIELD_UNSUPPORTED`，不得映射为 String/Binary。第一版不支持 `ARRAY`、`MAP`、`STRUCT` 等复杂模型字段；出现真实需求后再扩充平台契约和每个方言的能力测试。

## 验证要求

- 方言单元测试必须覆盖 PostgreSQL、达梦和 ClickHouse 的双向映射、归一化、损失和不支持分支。
- 外部表预览和创建必须确认 `LOSSY`、`UNSUPPORTED` 会被阻止。
- PostgreSQL 真实测试使用隔离 Schema 创建并回读平台类型，达梦和 ClickHouse 在获得隔离实例前使用纯方言测试。
- 前端不得出现 JDBC 数字类型或原生数据库类型到模型类型的映射函数。
