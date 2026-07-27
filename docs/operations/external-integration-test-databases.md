# 固定外部集成测试基础设施

## 用途

DataScalpel 中所有需要真实 PostgreSQL 或 MySQL 实例的开发、集成测试和人工验收，均可使用本页约定的两个固定测试库。没有专项隔离要求时，应优先使用这两个实例，不再临时申请其他外部测试数据库。

适用范围包括但不限于：数据源连接与元数据、Dialect、模型物理表、Local SQL、Canvas 编译与执行、Task Runner、Local Docker 端到端流程，以及后续新增的 PostgreSQL/MySQL 功能。

这两个实例仅作为开发和集成测试环境，不得用于生产数据，也不得在未确认对象归属时修改已有表。

除数据库外，执行链路测试统一使用 Kafka `home.superhuang.cn:9094`。测试 Topic 必须使用
`datascalpel.<domain>.it.<uuid>` 或带日期后缀的独立名称；只能删除当前测试创建的 Topic。

## 固定连接

| 数据库 | Host | Port | Database | Username | 测试命名空间 |
| --- | --- | ---: | --- | --- | --- |
| PostgreSQL | `home.superhuang.cn` | `5432` | `test` | `test` | 允许按测试自由创建隔离 Schema |
| MySQL | `home.superhuang.cn` | `3306` | `test` | `test` | 使用 `test` Database，测试表增加 `datascalpel_<domain>_it_` 前缀 |

密码不进入 Git。当前开发机的完整连接信息保存在仓库根目录的 `.env.integration.local`，该文件由 `.gitignore` 中的 `.env.*` 规则排除。

## 本机使用

执行真实数据库测试前加载本机配置：

```bash
source .env.integration.local
```

PostgreSQL 沿用工程现有变量：

```text
DATASCALPEL_PG_INTEGRATION
DATASCALPEL_PG_HOST
DATASCALPEL_PG_PORT
DATASCALPEL_PG_DATABASE
DATASCALPEL_PG_SCHEMA
DATASCALPEL_PG_USERNAME
DATASCALPEL_PG_PASSWORD
DATASCALPEL_PG_SSLMODE
```

MySQL 统一使用：

```text
DATASCALPEL_MYSQL_INTEGRATION
DATASCALPEL_MYSQL_HOST
DATASCALPEL_MYSQL_PORT
DATASCALPEL_MYSQL_DATABASE
DATASCALPEL_MYSQL_USERNAME
DATASCALPEL_MYSQL_PASSWORD
DATASCALPEL_MYSQL_USE_SSL
```

## 测试约束

- 自动化测试应使用随机表名，并在 `finally` 或测试清理阶段删除创建的对象。
- PostgreSQL 允许在 `test` 数据库中自由创建独立 Schema 进行隔离。推荐使用 `datascalpel_<domain>_test` 或带随机后缀的名称，例如任务执行使用 `datascalpel_task_test`，Dialect/模型适配测试可沿用 `datascalpel_adapter_test`。
- 测试可以创建、修改和删除自己创建的 PostgreSQL Schema；清理前必须确认 Schema 属于当前测试，不得删除其他测试仍在使用的 Schema。
- MySQL 没有独立 Schema 时按业务域隔离表前缀，例如任务执行使用 `datascalpel_task_it_`；新增领域使用 `datascalpel_<domain>_it_`。
- `OVERWRITE`、`TRUNCATE` 和失败恢复测试只能操作本次测试创建的目标表。
- 禁止清空、改名或删除无法确认归属的已有对象。
- 日志、测试报告、Kafka 消息、Runner result 和提交到 Git 的文件中不得包含密码或完整 JDBC 凭据。
- 自动化 Kafka 测试必须使用随机 Topic，并在测试结束时删除；生产环境仍禁止依赖 Broker 自动创建 Topic。
- Docker Runner 可直接使用 `home.superhuang.cn`，不需要将地址改写为 `host.docker.internal`。

## Canvas 端到端验收顺序

1. PostgreSQL 读取、Join、APPEND 和 OVERWRITE。
2. MySQL 读取、Join、APPEND 和 OVERWRITE。
3. PostgreSQL 与 MySQL 跨库 Join，并写入隔离的测试目标表。
4. 核对 MinIO manifest/result/log、Kafka 状态事件、Dispatcher Execution 与 Admin TaskRun 的最终一致性。
