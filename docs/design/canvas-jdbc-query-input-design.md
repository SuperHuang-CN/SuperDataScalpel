# Canvas JDBC_QUERY_INPUT 设计

状态：Canvas 1.24 已实现；针对性协议、业务、Compiler、Runner 和前端测试已通过。

## 定义

`JDBC_QUERY_INPUT` 是数据库输入节点，支持 `BATCH` 和 `STREAMING`，无入边、至少一条出边。它执行一条静态只读 JDBC 查询并产生一个 `BOUNDED` 逻辑表；实时任务只在启动时读取一次，运行期间不刷新。

```ts
interface JdbcQueryInputConfiguration {
  dataSourceId: string;
  sql: string;
  outputTableName: string;
  analyzedSqlSha256: string;
  outputColumns: CanvasColumnSchema[];
}
```

查询支持 PostgreSQL、HighGo、MySQL、openGauss 和人大金仓上的单条 `SELECT` 或 `WITH ... SELECT`，最大 100,000 字符，不支持运行参数、模板变量、预览和分区读取。查询结果字段随 Canvas 定义保存；SQL Hash 使用去除终止分号并 trim 后的 UTF-8 SQL 计算 SHA-256。

节点类别为 `INPUT`，批流注册表均启用，无入边且至少一条出边。它以 `outputTableName` 产生一个 `BOUNDED` 表，Origin 固定为 `JDBC_QUERY`；与普通 `JDBC_INPUT` 不同，它不引用物理表元数据，也不把 SQL 扩张到普通表输入配置中。

## 分析、编译与运行

- `POST /api/v1/data-sources/{id}/actions/inspect-query` 使用 `datasource.metadata` 权限，只接受已启用、具有 `SOURCE` 用途的 PostgreSQL、HighGo、MySQL、openGauss 或人大金仓数据源。它在只读连接和只读 Session 中优先读取 PreparedStatement 元数据；驱动不提供时最多执行一行 fallback，只返回 Hash 和字段，不返回 SQL 或数据行。
- Inspector 仅在用户点击“分析 SQL”时调用接口；SQL 变化后保留旧快照并标记过期。
- Compiler 校验 Hash、字段结构、数据源和表名冲突，并使用保存字段创建零行计划，不连接数据库。
- 发布、重新启用和运行准备不重新分析查询结果，也不把当前结果结构与保存快照比较。
- Runner 再次校验只读 SQL 和 Hash，并以数据库只读 Session 执行查询；保存的 `outputColumns`
  是下游逻辑规划依据，真实结果能否被使用由 Spark 实际分析和执行决定。
- 首版拒绝 Geometry 查询列；可以查询 WKT/WKB 后连接 `GEOMETRY_CONSTRUCT`。

显式分析时，字段名重复、类型参数不完整、无法无损映射或 Geometry 均阻止保存/编译；SQL 文本
变化仍通过 Hash 返回 `JDBC_QUERY_SCHEMA_STALE`，导入内容不能绕过只读 SQL 和 Hash 校验。

安全摘要只包含数据源 ID、输出表和字段数，不记录 SQL、SQL Hash、字面量、数据或凭据。

## 版本与错误

节点从 Canvas `1.24` 引入，低版本返回 `NODE_TYPE_REQUIRES_SCHEMA_VERSION`；Manifest v9 才允许运行，v8 携带该节点时拒绝。主要错误包括 `JDBC_QUERY_NOT_READ_ONLY`、`JDBC_QUERY_SQL_TOO_LONG`、`JDBC_QUERY_SCHEMA_REQUIRED`、`JDBC_QUERY_SCHEMA_INVALID`、`JDBC_QUERY_SCHEMA_STALE`、`JDBC_QUERY_DATABASE_NOT_SUPPORTED`、`JDBC_QUERY_GEOMETRY_NOT_SUPPORTED` 和 `JDBC_QUERY_INPUT_FAILED`。

## 已实现范围

- Contracts、Jackson 判别联合、Canvas 版本门槛和 `JDBC_QUERY` 表来源。
- 数据源查询分析 API、只读 SQL 词法护栏、Hash、字段映射和安全响应。
- Admin 生成 Manifest v9 逻辑字段快照，不执行运行时查询结果漂移检查。
- SchemaOnly/Runtime Data Access、批处理读取和 Streaming 启动时静态缓存。
- 前端节点库、Monaco Inspector、显式分析、过期状态、字段预览、摘要和 JSON 往返。

首版仍不包含查询参数、数据预览、增量/周期刷新、分区读取和 Geometry 直接结果。
