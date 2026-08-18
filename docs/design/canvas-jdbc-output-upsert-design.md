# Canvas JDBC_OUTPUT UPSERT 设计

状态：JDBC_OUTPUT UPSERT 的历史引入版本为 Canvas 1.24；当前 Canvas 2.3 已同时支持
JDBC_OUTPUT 与 MODEL_OUTPUT 的批流 UPSERT。

## 定义

`JDBC_OUTPUT.writeMode` 支持 `UPSERT`，并使用目标字段名数组 `upsertKeyColumns`。非 UPSERT 模式该数组必须为空；UPSERT 必须选择目标表的一整组主键或安全唯一索引字段。

```ts
type JdbcWriteMode = 'APPEND' | 'OVERWRITE' | 'UPSERT';

interface JdbcOutputConfiguration {
  sourceTableName: string;
  dataSourceId: string;
  targetTableName: string;
  writeMode: JdbcWriteMode | null;
  columnMappings: JdbcColumnMapping[];
  upsertKeyColumns: string[];
}
```

Canvas `2.x` 的 JDBC_OUTPUT 从基础版本开始支持该配置。Canvas `1.x` 属于不兼容大版本，
不再读取或自动迁移。

UPSERT 支持批处理和实时 micro-batch。实时任务继续禁止 `OVERWRITE` 和 Geometry 输出；批处理允许更新非 Key Geometry 字段。Key 必须已映射、可写、非 Geometry，冲突时只更新已映射的非 Key 字段。只有 Key 时 PostgreSQL 使用 `DO NOTHING`，MySQL 使用无变化更新。

## 元数据与方言

目标表元数据增加 `uniqueKeys`，只包含主键以及无表达式、无条件谓词的字段型唯一索引。PostgreSQL 使用 `ON CONFLICT (keys...)`；MySQL 使用 `ON DUPLICATE KEY UPDATE`。MySQL 无法指定具体冲突约束，目标存在多组唯一键时 Compiler 返回 `MYSQL_UPSERT_MULTIPLE_UNIQUE_KEYS` 警告。

PostgreSQL/MySQL 元数据都保留约束字段顺序；主键与索引字段组重复时只保留主键。Compiler 要求配置字段数组与其中一组 `uniqueKeys` 完整且顺序一致，并要求 Key 全部出现在映射后的目标字段中。Inspector 以完整约束为单选项展示，元数据变化后保留失效配置并标红。

所有目标表和字段标识符由 `data-scalpel-dialect` 渲染。UPSERT 不创建、修改或推测数据库约束。

## 写入语义

每个 Dataset 或 micro-batch 在写入前按全部 Key 聚合检查：任意 NULL 返回 `UPSERT_KEY_NULL`，批内重复返回 `UPSERT_DUPLICATE_KEY`。检查不输出实际 Key 值，并要求用户在上游使用 `DEDUPLICATE`。

检查使用 `MEMORY_AND_DISK` 缓存，成功后复用同一 Dataset。写入使用每 Spark 分区一个 JDBC 事务、PreparedStatement 和 500 行批次；Geometry 继续使用 WKB 桥接。跨分区不提供全局事务，Streaming 仍是至少一次交付，不宣称 Exactly Once。

Canvas `2.3` 起 `MODEL_OUTPUT` 同时支持批处理和实时 UPSERT。模型节点不保存 `upsertKeyColumns`，固定从模型元数据快照取得按字段顺序排列的完整主键；模型无主键、Key 不可写或未全部映射时编译失败。平台不访问物理表验证唯一约束，最终由数据库真实 UPSERT 结果判断。实时模型输出继续禁止 OVERWRITE 和 Geometry，并复用同一 `foreachBatch`、NULL/重复 Key 校验及至少一次语义。

## 已实现范围

- Contracts、Canvas `2.x` 定义、方言和 Manifest 写入契约。
- PostgreSQL/MySQL 安全唯一键读取、方言能力和受控 SQL 渲染。
- Compiler 的完整约束匹配、Key 映射/字段限制、批流模式与 MySQL 多唯一键警告。
- Batch 和 Streaming `foreachBatch` 的缓存、NULL/重复 Key 检查、分区事务和写入指标。
- 前端写入模式、完整唯一约束选择、失效值保留和 MySQL 原生冲突提示。

UPSERT 不创建或修改目标唯一约束。同一 Key 跨 micro-batch 出现属于正常更新；只拒绝当前 Dataset 或单个 micro-batch 内部的重复 Key。数据库或分区故障仍可能形成跨分区部分提交，重试依靠 Key 收敛。
