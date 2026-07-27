# 08 Local Docker 端到端验收开发设计

## 1. 目标

在本地环境完成第一条正式真实执行链路验收，证明计算引擎管理、Kafka可靠消息、Dispatcher账本、Local Docker Backend、Runner、MinIO和 PostgreSQL/MySQL 可以协同工作。

依赖：[01](01-compute-engine-management.md)至[07](07-task-engine-preflight-only.md)全部完成。

本阶段以修复问题和补齐自动化验收为主，不再引入新的执行语义。

## 2. 验收拓扑

```text
Browser :18887
   │
   ▼
Admin :18080 ──HTTP──> Task Engine :18091
   │                      仅预检
   │ Kafka command
   ▼
Kafka
   │
   ▼
Dispatcher :18092 ──Docker CLI──> JDK 21 Runner Container
   ▲                                  │
   │ Kafka runner event               ├─ MinIO manifest/result
   │                                  └─ PostgreSQL/MySQL JDBC
   └──────── Kafka admin event ──> Admin
```

Service Engine 可以继续由 `start-local-dev.sh` 启动，但不参与 Canvas 真实执行链路。

## 3. `start-local-dev.sh` 调整

脚本增加：

- 构建 `data-scalpel-task-dispatcher`。
- 校验 Runner Local Uber JAR 存在且未过期。
- 检查 Kafka Broker、MinIO、Docker和 PostgreSQL。
- 确保 `dispatcher` Schema存在。
- 启动 Task Engine 时不再设置 execution/Docker参数。
- 启动 Dispatcher，默认端口 18092。
- 注入与 Admin 相同的数据库 URL、用户名和密码，Dispatcher URL追加 `currentSchema=dispatcher` 或设置 Hibernate default schema。
- 注入 Dispatcher Token、Kafka配置、MinIO配置和 Runner JAR路径。
- 等待 Dispatcher `/health/ready`。
- 创建或更新 Local ComputeEngine并执行 test/register。
- 设置计算引擎 Topic并确认存在。
- cleanup 时按相反顺序停止本次脚本启动的进程。

本地端口默认值：

```text
Admin API       18080
Service Engine  8081
Task Engine     18091
Task Dispatcher 18092
Vite UI         18887
```

脚本不得：

- 删除非本脚本创建的 Kafka Topic。
- 清空 Dispatcher Schema。
- 删除已有 MinIO Bucket对象。
- 使用 `docker system prune` 等破坏性命令。
- 把 Token、数据库密码或 MinIO Secret打印到终端。

## 4. 本地基础设施策略

### 4.1 Kafka

优先连接 `DATASCALPEL_KAFKA_BOOTSTRAP_SERVERS`。如果没有配置，可由独立的本地基础设施脚本启动单节点 KRaft Kafka；`start-local-dev.sh` 本身只检查 Broker 并给出明确错误，不负责创建 Kafka 集群。脚本会显式开启 Dispatcher 的本地 Topic 确保能力，在计算引擎注册时创建缺失的 command、runner-event 和 admin-event Topic；该能力默认关闭，生产环境仍由运维预建 Topic。

需要 Topic：

```text
datascalpel.execution.command.local
datascalpel.runner.event.local
datascalpel.execution.event
datascalpel.execution.invalid-message.v1
```

测试环境允许单副本；生产配置不沿用该值。

### 4.2 PostgreSQL

Admin 数据库账户需要：

- Admin当前 Schema的原有权限。
- 创建/使用 `dispatcher` Schema的权限。
- Dispatcher Schema内建表、查询和更新权限。

Dispatcher 不能访问 Admin业务表的约束通过代码结构和集成测试保证；如果生产需要更强隔离，可后续使用同数据库不同账户，本阶段按已确认方案共用账户。

### 4.3 MinIO

Admin 和 Dispatcher配置同一私有 Bucket：

- Admin 上传 manifest。
- Dispatcher检查对象、生成预签名 URL、读取 result并上传日志。
- Runner只使用短期预签名 URL。

Runner容器可访问的 Endpoint 通常使用：

```text
http://host.docker.internal:<minio-port>
```

不能把宿主机 `127.0.0.1` 直接下发给容器。

### 4.4 Docker

启动前检查：

```text
docker version
docker info
docker image inspect <jdk21-image>
```

此前真实数据库集成测试曾因 Docker存储空间不足被阻塞。验收前应通过 `docker system df` 检查空间，但清理操作由开发者确认后手动执行。

## 5. 测试数据库和数据

使用专门的 PostgreSQL/MySQL测试库，不复用 Admin管理库表。

### 5.1 orders

```text
order_id      BIGINT        NOT NULL
customer_id   BIGINT        NOT NULL
amount        DECIMAL(18,2) NOT NULL
created_at    TIMESTAMP     NOT NULL
```

### 5.2 customers

```text
customer_id   BIGINT       NOT NULL
customer_name VARCHAR(100) NOT NULL
```

### 5.3 dwd_order_customer

```text
order_id      BIGINT
customer_id   BIGINT
amount        DECIMAL(18,2)
created_at    TIMESTAMP
customer_name VARCHAR(100)
```

至少插入 3 个订单和 2 个客户，其中一个客户有两个订单。预期 INNER JOIN输出 3 行，金额和客户名称逐字段校验。

## 6. Canvas 验收定义

```text
JDBC_INPUT orders ─┐
                   ├─ JOIN(order_customer) ─ JDBC_OUTPUT dwd_order_customer
JDBC_INPUT customers┘
```

配置：

- Join：`orders.customer_id = customers.customer_id`。
- Join Type：INNER。
- Output：BY_NAME，APPEND。
- 第二次独立验收改为 OVERWRITE，确认使用 TRUNCATE + append。

执行前必须看到 Canvas Designer通过 Engine预检；真实运行时 Admin再重建权威快照并预检一次。

## 7. 手工验收步骤

1. 启动 Kafka、MinIO和测试 PostgreSQL/MySQL。
2. 执行 `start-local-dev.sh`。
3. 登录 Admin，确认 Task Engine和 Local ComputeEngine均为可用。
4. 检查 Dispatcher注册状态 ACTIVE、后端 LOCAL_DOCKER。
5. 创建测试数据源并验证 Input/Output用途。
6. 创建 SPARK_CANVAS并绑定 Local ComputeEngine。
7. 配置两个 Input、Join和 Output。
8. 发布任务。
9. 点击立即运行。
10. 确认 TaskRun先显示 QUEUED，再显示 RUNNING。
11. 检查 Dispatcher账本存在唯一 executionId。
12. 检查 Docker容器带完整稳定 Labels。
13. 等待 SUCCESS。
14. 查询目标表并核对 3 行数据。
15. 检查 manifest、result.json、console.log对象存在。
16. 确认日志和 result不包含数据库密码、Token或预签名 URL。
17. 使用 OVERWRITE 再执行一次，确认没有重复数据。

## 8. 故障验收

### 8.1 Kafka短暂不可用

- Admin运行请求创建 TaskRun和 Outbox。
- 恢复 Kafka后命令自动发送。
- 只创建一个 Dispatcher execution。

### 8.2 Dispatcher重启

- QUEUED任务继续排队。
- 运行中的容器通过 Label重新挂接。
- 不启动第二个容器。

### 8.3 Runner失败

- 使用错误 JDBC地址或故意的 Schema漂移。
- Runner生成安全失败 result并上传。
- Dispatcher转为 FAILED并发送 Admin事件。
- 目标表没有发生预计划阶段之前的写入。

### 8.4 取消

- QUEUED取消不启动容器。
- RUNNING取消执行 stop/kill并最终 CANCELLED。
- 迟到 Runner事件不覆盖 CANCELLED。

### 8.5 超时

- 配置短 deadline。
- Dispatcher停止容器并进入 TIMED_OUT。
- Admin和 Dispatcher终态一致。

### 8.6 结果事件丢失

- 模拟 Runner上传 result后 Kafka发送失败。
- Dispatcher通过容器终态和 MinIO对象恢复 SUCCESS/FAILED。

## 9. 自动化测试分层

```text
单元：契约、状态机、命令构造、结果映射
组件：Admin Outbox/Inbox、Dispatcher Ledger/Outbox
集成：Kafka + PostgreSQL + MinIO Testcontainers
执行：Docker + Runner + PostgreSQL/MySQL
端到端：Admin API提交到目标表结果
```

真实 Docker测试使用独立 Maven Profile，默认单元测试不因开发机没有 Docker而失败；CI验收环境必须运行该 Profile。

## 10. 构建与检查

```bash
pnpm --dir data-scalpel-ui check
./mvnw -pl data-scalpel-task-dispatcher -am test
./mvnw -pl data-scalpel-task-engine -am test
./mvnw -pl data-scalpel-admin -am test
./mvnw verify
```

真实验收 Profile示意：

```bash
./mvnw -Ptask-execution-it verify
```

## 11. 阶段退出条件

- Local Docker主链路和六类故障场景通过。
- Engine Daemon不再出现 Docker执行日志。
- Dispatcher重启不造成重复容器。
- result/log/manifest边界和敏感信息要求通过检查。
- `start-local-dev.sh` 可以一键启动 Admin、Engine、Dispatcher 和 UI，并正确注册本地计算引擎。
